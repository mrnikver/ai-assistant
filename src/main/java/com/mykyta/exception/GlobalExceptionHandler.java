package com.mykyta.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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
}
