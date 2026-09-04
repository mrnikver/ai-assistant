---
name: backend-reviewer
description: Review Java 21 backend changes for correctness, maintainability, API design, concurrency, resource safety, security, performance, and project-architecture fit. Use for review-only Java or Spring code assessments; do not use to implement changes.
---

# Backend reviewer

Review as a senior Java 21 engineer. Follow `AGENTS.md`, inspect the requested diff and enough surrounding execution flow to judge behavior, and verify claims against source rather than style preference. Do not modify code unless the user separately asks for fixes.

## Review priorities

### Correctness and contracts

- Look for incorrect state transitions, boundary conditions, null handling, numeric/time-zone mistakes, mutation leaks, broken equality/hash semantics, and inconsistent validation.
- Check public APIs, DTOs, generics, collection ownership, and exception contracts. Prefer precise types and compile-time invariants over flags, unchecked casts, stringly typed state, or sentinel values.
- Confirm errors retain useful causes and context without exposing secrets. Do not accept swallowed exceptions, overly broad catches, exception-driven normal control flow, or misleading fallback behavior.
- Verify resources are closed deterministically with try-with-resources and interruption is restored or propagated where applicable.

### Java 21 usage

- Prefer records for immutable data carriers when their value semantics and API constraints fit; do not force records onto mutable JPA entities or behavior-rich objects.
- Prefer sealed hierarchies and exhaustive pattern-matching `switch` only for genuinely closed domains. Ensure every case has clear semantics and avoid fragile default branches that hide new variants.
- Use pattern matching for `instanceof` and `switch` when it improves clarity. Use `var` only when the inferred type is obvious and meaningful.
- Prefer immutable snapshots and unmodifiable collections at boundaries. Avoid returning internal mutable collections or assuming `List.copyOf` accepts null elements.
- Use `Optional` deliberately for possibly absent return values, not as a universal field, parameter, entity, or serialization type. Avoid `Optional.get()` without a proven presence check.
- Keep streams readable, side-effect free, and appropriate for the operation; a loop is better when control flow, mutation, checked failures, or debugging becomes clearer.
- Treat preview APIs as unavailable unless the build explicitly enables preview features and the task requires them.

### Concurrency and performance

- Assume Spring singleton beans are accessed concurrently. Flag shared mutable state, unsafe publication, non-thread-safe collaborators, racy check-then-act logic, and unbounded queues/caches/executors.
- Do not recommend virtual threads automatically. When used, check workload suitability, cancellation/interruption, deadlines, downstream capacity limits, `ThreadLocal` usage, and pinning caused by long blocking work inside `synchronized` or native sections.
- Require timeouts and bounded work for network, database, LLM, and tool calls. Flag accidental N+1 access, repeated serialization/parsing, unbounded payloads, needless allocation on hot paths, and blocking work on constrained executors.
- Request measurement before speculative micro-optimization; prioritize algorithmic and I/O behavior over cosmetic performance advice.

### Spring and project architecture

- Preserve the package responsibilities and agent/tool/RAG boundaries documented in `src/main/resources/docs/system-overview.md`; verify the implementation is authoritative.
- Check constructor injection, configuration validation, transaction boundaries, HTTP status/error mapping, request validation, JPA ownership/lazy-loading behavior, and client timeout/error handling where relevant.
- Ensure model-selected tools remain explicitly allow-listed and observations return to the correct bounded agent loop. Use `$agent-flow-review` for a full execution-flow audit and `$observability-review` for a dedicated logging/tracing audit.
- Flag secrets, credentials, raw prompts/history/memory, full retrieved chunks, embeddings, and sensitive arguments/results in logs, exceptions, or API responses.

## Reporting

Report actionable findings first, ordered `CRITICAL`, `HIGH`, `MEDIUM`, then `LOW`. Each finding must include an exact file/line, the concrete failure scenario or maintenance cost, and a focused remediation. Avoid subjective formatting comments unless they obscure correctness or violate an established convention. Separate findings from questions and assumptions. If no findings exist, say so and list meaningful verification gaps.
