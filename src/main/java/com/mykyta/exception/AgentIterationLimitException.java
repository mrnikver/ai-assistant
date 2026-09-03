package com.mykyta.exception;

/**
 * Signals that an agent continued requesting actions until its configured safety limit.
 *
 * <p>Termination at the boundary prevents a faulty prompt or model behavior from
 * creating an infinite LLM/tool cycle.</p>
 */
public class AgentIterationLimitException extends RuntimeException {

    /**
     * Creates a limit failure for one agent run.
     *
     * @param maxIterations configured maximum number of LLM turns
     */
    public AgentIterationLimitException(int maxIterations) {
        super("Agent reached the maximum of " + maxIterations + " iterations without producing a final answer");
    }
}
