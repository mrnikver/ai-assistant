package com.mykyta.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Converts expected request and agent failures into stable HTTP error responses.
 *
 * <p>Operational details remain in server logs while clients receive concise,
 * non-sensitive messages.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Reports the first bean-validation error in a client-readable form.
     *
     * @param exception validation failure raised while binding the request
     * @return HTTP 400 response with a stable application error code
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApplicationException> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");

        log.warn("Request validation failed: error={}", message);

        return ResponseEntity.badRequest()
                .body(new ApplicationException(
                        "INVALID_REQUEST",
                        message,
                        Instant.now()
                ));
    }

    /**
     * Reports syntactically invalid request JSON without exposing parser internals.
     *
     * @param exception JSON conversion failure
     * @return HTTP 400 response with a stable application error code
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApplicationException> handleMalformedJson(
            HttpMessageNotReadableException exception
    ) {
        log.warn("Malformed JSON request: cause={}", exception.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(new ApplicationException(
                        "MALFORMED_JSON",
                        "Request body contains invalid JSON",
                        Instant.now()
                ));
    }

    /**
     * Reports termination when the model never produces an answer within the loop limit.
     *
     * @param exception bounded-loop termination signal
     * @return HTTP 500 response identifying agent exhaustion
     */
    @ExceptionHandler(AgentIterationLimitException.class)
    public ResponseEntity<ApplicationException> handleAgentIterationLimit(
            AgentIterationLimitException exception
    ) {
        log.error("Agent iteration limit reached", exception);
        return ResponseEntity.internalServerError()
                .body(new ApplicationException(
                        "AGENT_ITERATION_LIMIT",
                        "The assistant could not complete the request within its reasoning limit",
                        Instant.now()
                ));
    }
}
