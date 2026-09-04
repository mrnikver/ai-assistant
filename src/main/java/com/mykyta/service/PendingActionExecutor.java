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
        PendingAction claimedAction = pendingActionService.beginExecution(action.actionId());
        try (TraceScope span = agentTracer.startSpan(TraceSpanType.TOOL_CALL, "Confirmed action: " + claimedAction.toolName())) {
            span.metadata("actionId", claimedAction.actionId());
            span.metadata("tool", claimedAction.toolName());
            span.metadata("arguments", sanitizer.map(claimedAction.arguments()));
            span.metadata("confirmationStatus", "CONFIRMED");
            span.metadata("executionStatus", "EXECUTING");
            try {
                ToolExecutionOutcome outcome = switch (claimedAction.toolName()) {
                    case RestartServiceTool.NAME -> restartServiceTool.executeConfirmed(claimedAction);
                    default -> throw new IllegalStateException("No executor for confirmed tool: " + claimedAction.toolName());
                };
                pendingActionService.markExecuted(claimedAction.actionId());
                span.metadata(outcome.metadata());
                log.info("Pending action executed: actionId={}, tool={}, confirmationStatus=CONFIRMED, executionStatus=EXECUTED",
                        claimedAction.actionId(), claimedAction.toolName());
                return outcome;
            } catch (RuntimeException exception) {
                pendingActionService.markFailed(claimedAction.actionId());
                span.metadata("executionStatus", "FAILED");
                span.fail(exception);
                throw exception;
            }
        }
    }
}
