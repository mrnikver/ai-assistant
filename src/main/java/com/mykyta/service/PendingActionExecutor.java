package com.mykyta.service;

import com.mykyta.model.PendingAction;
import com.mykyta.model.PendingActionStatus;
import com.mykyta.observability.LlmObservabilitySanitizer;
import com.mykyta.tool.RestartServiceTool;
import com.mykyta.tool.ToolExecutionOutcome;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Dispatches confirmed pending actions without consulting the language model. */
@Service
@Slf4j
public class PendingActionExecutor {
    private final PendingActionService pendingActionService;
    private final RestartServiceTool restartServiceTool;
    private final AgentTracer agentTracer;
    private final LlmObservabilitySanitizer sanitizer;

    public PendingActionExecutor(PendingActionService pendingActionService, RestartServiceTool restartServiceTool,
                                 AgentTracer agentTracer, LlmObservabilitySanitizer sanitizer) {
        this.pendingActionService = pendingActionService;
        this.restartServiceTool = restartServiceTool;
        this.agentTracer = agentTracer;
        this.sanitizer = sanitizer;
    }

    public ToolExecutionOutcome execute(PendingAction action) {
        if (action.status() != PendingActionStatus.CONFIRMED) {
            throw new IllegalStateException("Pending action is not confirmed");
        }
        try (TraceScope span = agentTracer.startSpan(TraceSpanType.TOOL_CALL, "Confirmed action: " + action.toolName())) {
            span.metadata("actionId", action.actionId());
            span.metadata("tool", action.toolName());
            span.metadata("arguments", sanitizer.map(action.arguments()));
            span.metadata("confirmationStatus", "CONFIRMED");
            span.metadata("executionStatus", "EXECUTING");
            try {
                ToolExecutionOutcome outcome = switch (action.toolName()) {
                    case RestartServiceTool.NAME -> restartServiceTool.executeConfirmed(action);
                    default -> throw new IllegalStateException("No executor for confirmed tool: " + action.toolName());
                };
                pendingActionService.markCompleted(action.actionId());
                span.metadata(outcome.metadata());
                log.info("Pending action completed: actionId={}, tool={}, confirmationStatus=CONFIRMED, executionStatus=COMPLETED",
                        action.actionId(), action.toolName());
                return outcome;
            } catch (RuntimeException exception) {
                pendingActionService.markFailed(action.actionId());
                span.metadata("executionStatus", "FAILED");
                span.fail(exception);
                throw exception;
            }
        }
    }
}
