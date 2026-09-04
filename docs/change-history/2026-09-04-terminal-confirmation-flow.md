# Make Confirmation Required Terminal

## Status

COMPLETED

## Original Request

Refactor the guarded `restart_service` flow so that application-owned `CONFIRMATION_REQUIRED` immediately terminates the current orchestration request, returns a structured response, deduplicates equivalent pending actions, and executes an explicitly confirmed pending action exactly once without LLM reinterpretation.

## Requirements

- Preserve validation, allow-list checks, and the rule that an LLM cannot confirm an action.
- Stop Runtime and Supervisor orchestration immediately when confirmation is required, without another LLM call.
- Return structured confirmation details to the API caller/UI.
- Reuse an equivalent awaiting pending action instead of creating duplicates.
- Execute the exact stored action once through `PendingActionExecutor` after explicit application-level confirmation.
- Expose action, confirmation, execution, and loop-stop state in logs and traces.

## Implementation Plan

Trace the tool result through `ToolRegistry`, specialist delegation, `AgentRuntime`, `AssistantService`, and the public response. Extend the existing result contracts with a typed terminal state, make it propagate through nested agent calls, deduplicate pending actions, harden confirmation execution idempotency, update consumers and architecture documentation, then verify with Maven.

## Implementation

- Added a typed pending-action terminal signal to tool outcomes, tool results, and agent results.
- Made `AgentRuntime` stop immediately on that signal and propagate it through Supervisor delegation without another LLM call.
- Added structured chat response status and pending-action details for confirmation, execution, expiry, and already-resolved outcomes.
- Reused equivalent awaiting actions within a conversation and added an atomic execution claim with one-shot terminal states.
- Narrowed application-level confirmation matching so unrelated discussion cannot confirm an action.
- Added pending-action and loop-stop fields to logs and trace metadata.
- Updated the sibling UI TypeScript contract for the new structured fields on its own branch.

## Files Changed

- `docs/change-history/2026-09-04-terminal-confirmation-flow.md` — Records this implementation task.
- `src/main/java/com/mykyta/agent/AgentRuntime.java` — Stops agent loops on typed confirmation-required results.
- `src/main/java/com/mykyta/agent/SupervisorAgent.java` — Propagates specialist terminal states without synthesis.
- `src/main/java/com/mykyta/model/*` — Adds response/action states and carries terminal details.
- `src/main/java/com/mykyta/response/ChatResponse.java` — Exposes structured action state to callers.
- `src/main/java/com/mykyta/service/AssistantService.java` — Resolves user confirmation before orchestration and maps action outcomes.
- `src/main/java/com/mykyta/service/PendingActionService.java` — Deduplicates pending actions and owns one-shot transitions.
- `src/main/java/com/mykyta/service/PendingActionExecutor.java` — Atomically claims and executes the exact stored action.
- `src/main/java/com/mykyta/tool/*` — Preserves typed guarded-tool results through the registry.
- `src/main/resources/docs/system-overview.md` — Documents the terminal and one-shot flow.
- `../ai-assistant-ui/src/types.ts` — Adds the matching client-side response contract in a separate repository change.

## Design Decisions

- Extend the existing guarded-tool and orchestration path rather than introduce a parallel restart endpoint.
- Keep confirmation resolution at the request coordinator boundary; agent-generated text never reaches it.
- Retain optional LLM summarization only after the application has executed or resolved the action.
- Scope deduplication to a conversation because confirmation ownership is conversation-bound.

## Deviations

None.

## Validation

- `mvn verify` — passed; the project currently has no test sources.
- `npm run build` in `../ai-assistant-ui` — passed.
- `npm run lint` in `../ai-assistant-ui` — passed.
- Reviewed scenarios A–F against the typed terminal propagation, narrowed confirmation resolver, equivalent-action reuse, and atomic `CONFIRMED -> EXECUTING -> EXECUTED` transition.

## Result

`restart_service` now creates or reuses one guarded pending action and immediately ends both specialist and Supervisor loops. Only a subsequent explicit user message resolved by the application can claim and execute the stored action, and repeated attempts cannot create equivalent IDs or execute the same action twice. The API, logs, and traces expose structured lifecycle state.

## Remaining Work

None.

## Git / Delivery

- Branch: `fix/terminal-confirmation-flow`
- Commit: `4d00222` (implementation), plus a delivery-metadata follow-up commit.
- Pull request: Not opened yet.
- Merge status: Not merged; pending implementation and review.
