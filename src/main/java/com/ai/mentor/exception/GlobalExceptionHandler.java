package com.ai.mentor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

        if (message != null && (message.contains("API key not valid") || message.contains("INVALID_ARGUMENT"))) {
            body.put("status", HttpStatus.UNAUTHORIZED.value());
            body.put("error", "Unauthorized");
            body.put("message", "Invalid Gemini API Key provided. Please verify your configuration.");
            return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
        }

        if (isTimeout(rootCause, message)) {
            body.put("status", HttpStatus.GATEWAY_TIMEOUT.value());
            body.put("error", "Gateway Timeout");
            body.put("message", "The Gemini service did not respond in time. Please retry the request.");
            return new ResponseEntity<>(body, HttpStatus.GATEWAY_TIMEOUT);
        }

        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", message != null ? message : "An unexpected error occurred.");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isTimeout(Throwable rootCause, String message) {
        String exceptionName = rootCause.getClass().getSimpleName();
        return exceptionName.contains("Timeout")
                || (message != null && (message.equalsIgnoreCase("timeout")
                || message.toLowerCase().contains("timed out")));
    }
}