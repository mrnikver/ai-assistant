package com.mykyta.tool;

/**
 * Wraps an operational failure raised while a registered tool performs its work.
 */
public class ToolExecutionException extends RuntimeException {

    /**
     * Creates a tool failure while preserving its underlying cause for application logs.
     *
     * @param message safe failure description that may be returned to the LLM
     * @param cause underlying operational failure
     */
    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
