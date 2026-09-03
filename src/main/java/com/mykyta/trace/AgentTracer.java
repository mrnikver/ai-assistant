package com.mykyta.trace;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a request-local causal trace from explicit application events.
 *
 * <p>This component is intentionally an observability collector, not a reasoning
 * recorder. It captures model calls, validated tool execution, retrieval, memory
 * access, and finalization while excluding prompts and hidden chain of thought.</p>
 */
@Component
public class AgentTracer {
    private final ThreadLocal<TraceContext> current = new ThreadLocal<>();

    /** Creates a collector whose active context is isolated to the current request thread. */
    public AgentTracer() { }

    /**
     * Starts the root span for one assistant request.
     * @return session whose close operation completes and detaches the trace
     */
    public TraceSession beginTrace() {
        TraceContext context = new TraceContext(UUID.randomUUID().toString());
        current.set(context);
        MutableSpan root = context.start(TraceSpanType.AGENT_RUN, "Agent run", null);
        return new TraceSession(this, context, root);
    }

    /**
     * Starts a child of the currently active span, or a no-op scope outside a trace.
     * @param type operation category
     * @param name human-readable operation name
     * @return span lifetime handle
     */
    public TraceScope startSpan(TraceSpanType type, String name) {
        TraceContext context = current.get();
        if (context == null) return NoopScope.INSTANCE;
        MutableSpan span = context.start(type, name, context.stack.peek());
        return new ActiveScope(context, span);
    }

    /**
     * Resolves iteration context for integrations that should not own agent control flow.
     * @return nearest active agent iteration, or {@code null} outside an iteration
     */
    public Integer currentIteration() {
        TraceContext context = current.get();
        if (context == null) return null;
        return context.stack.stream().map(span -> span.iteration).filter(value -> value != null).findFirst().orElse(null);
    }

    /**
     * Allocates the next model-call sequence number for diagnostic display.
     * @return one-based LLM call sequence within the active trace, or zero outside a trace
     */
    public int nextLlmCallNumber() {
        TraceContext context = current.get();
        return context == null ? 0 : ++context.llmCalls;
    }

    /** Returns the active request trace identifier for log correlation. */
    public String currentTraceId() {
        TraceContext context = current.get();
        return context == null ? null : context.traceId;
    }

    private void detach(TraceContext context) {
        if (current.get() == context) current.remove();
    }

    /** Owns the root span and exposes the immutable trace after completion. */
    public static final class TraceSession implements AutoCloseable {
        private final AgentTracer tracer;
        private final TraceContext context;
        private final MutableSpan root;
        private boolean closed;

        private TraceSession(AgentTracer tracer, TraceContext context, MutableSpan root) {
            this.tracer = tracer;
            this.context = context;
            this.root = root;
        }

        /**
         * Supplies the public association key before the trace is complete.
         * @return identifier returned with the chat response
         */
        public String traceId() { return context.traceId; }

        /**
         * Marks the agent run as failed using only a safe error category.
         * @param error failure whose type identifies the category
         */
        public void fail(Throwable error) { root.fail(error); }

        /**
         * Returns the immutable trace after this session has been closed.
         * @return completed execution trace
         * @throws IllegalStateException if called before completion
         */
        public AgentTrace completedTrace() {
            if (!closed) throw new IllegalStateException("Trace session is still active");
            List<TraceSpan> spans = context.spans.stream().map(MutableSpan::snapshot).toList();
            TraceSpan rootSpan = spans.getFirst();
            return new AgentTrace(context.traceId, rootSpan.status(), rootSpan.startedAt(), rootSpan.endedAt(), rootSpan.durationMs(), spans);
        }

        @Override
        public void close() {
            if (closed) return;
            root.close();
            context.stack.clear();
            tracer.detach(context);
            closed = true;
        }
    }

    private static final class TraceContext {
        private final String traceId;
        private final List<MutableSpan> spans = new ArrayList<>();
        private final Deque<MutableSpan> stack = new ArrayDeque<>();
        private int llmCalls;

        private TraceContext(String traceId) { this.traceId = traceId; }

        private MutableSpan start(TraceSpanType type, String name, MutableSpan parent) {
            MutableSpan span = new MutableSpan(UUID.randomUUID().toString(), parent == null ? null : parent.spanId, type, name);
            if (parent != null) span.iteration = parent.iteration;
            spans.add(span);
            stack.push(span);
            return span;
        }
    }

    private static final class ActiveScope implements TraceScope {
        private final TraceContext context;
        private final MutableSpan span;

        private ActiveScope(TraceContext context, MutableSpan span) { this.context = context; this.span = span; }
        @Override public TraceScope metadata(String key, Object value) { span.put(key, value); return this; }
        @Override public TraceScope metadata(Map<String, ?> values) { values.forEach(span::put); return this; }
        @Override public void fail(Throwable error) { span.fail(error); }
        @Override public void close() {
            span.close();
            if (context.stack.peek() == span) context.stack.pop(); else context.stack.remove(span);
        }
    }

    private enum NoopScope implements TraceScope {
        INSTANCE;
        @Override public TraceScope metadata(String key, Object value) { return this; }
        @Override public TraceScope metadata(Map<String, ?> values) { return this; }
        @Override public void fail(Throwable error) { }
        @Override public void close() { }
    }

    private static final class MutableSpan {
        private final String spanId;
        private final String parentSpanId;
        private final TraceSpanType type;
        private final String name;
        private final Instant startedAt = Instant.now();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private TraceStatus status = TraceStatus.SUCCESS;
        private Instant endedAt;
        private Integer iteration;

        private MutableSpan(String spanId, String parentSpanId, TraceSpanType type, String name) {
            this.spanId = spanId; this.parentSpanId = parentSpanId; this.type = type; this.name = name;
        }
        private void put(String key, Object value) {
            if (value != null) {
                metadata.put(key, value);
                if ("iteration".equals(key) && value instanceof Number number) iteration = number.intValue();
            }
        }
        private void fail(Throwable error) {
            status = TraceStatus.ERROR;
            if (error != null) {
                metadata.put("errorType", error.getClass().getSimpleName());
            }
        }
        private void close() { if (endedAt == null) endedAt = Instant.now(); }
        private TraceSpan snapshot() {
            close();
            return new TraceSpan(spanId, parentSpanId, type, name, status, startedAt, endedAt,
                    Duration.between(startedAt, endedAt).toMillis(), iteration, Map.copyOf(metadata));
        }
    }
}
