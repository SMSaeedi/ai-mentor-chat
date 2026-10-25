package com.ai.mentor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.InterruptedIOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Throwable ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());

        Throwable rootCause = ex;
        while (rootCause.getCause() != null && rootCause != rootCause.getCause()) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage() != null ? rootCause.getMessage() : ex.getMessage();

        if (rootCause instanceof IllegalArgumentException) {
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "Bad Request");
            body.put("message", message != null ? message : "The request contains invalid input.");
            return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
        }

        if (isTimeout(ex)) {
            body.put("status", HttpStatus.GATEWAY_TIMEOUT.value());
            body.put("error", "Gateway Timeout");
            body.put("message", "The AI model did not respond in time. Please retry the request.");
            logger.warn("AI model request timed out", ex);
            return new ResponseEntity<>(body, HttpStatus.GATEWAY_TIMEOUT);
        }

        logger.error("Unhandled error while processing request", ex);
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", message != null ? message : "An unexpected error occurred.");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException)
                return true;

            String message = current.getMessage();
            if (current.getClass().getSimpleName().toLowerCase().contains("timeout")
                    || message != null && (message.equalsIgnoreCase("timeout")
                    || message.toLowerCase().contains("timed out"))) {
                return true;
            }
        }
        return false;
    }
}