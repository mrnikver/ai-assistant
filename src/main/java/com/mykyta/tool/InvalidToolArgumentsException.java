package com.mykyta.tool;

/**
 * Indicates that an LLM requested a known tool with arguments that violate its contract.
 *
 * <p>The registry converts this exception into a controlled tool observation so
 * the model can correct its request instead of terminating the user request.</p>
 */
public class InvalidToolArgumentsException extends RuntimeException {

    /**
     * Creates an argument-validation failure suitable for returning to the model.
     *
     * @param message concise explanation of the invalid arguments
     */
    public InvalidToolArgumentsException(String message) {
        super(message);
    }
}
