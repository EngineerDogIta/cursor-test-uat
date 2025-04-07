package com.example.exception;

import com.example.exception.InvalidJobStateException;
import com.example.exception.JobNotFoundException;
import com.example.exception.JobProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * Catches specific custom exceptions and standard Spring exceptions
 * to return appropriate HTTP responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles JobNotFoundException.
     *
     * @param ex      The caught exception.
     * @param request The current web request.
     * @return A ResponseEntity with HTTP status 404 (Not Found).
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Object> handleJobNotFoundException(JobNotFoundException ex, WebRequest request) {
        logger.warn("Job not found: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles InvalidJobStateException.
     *
     * @param ex      The caught exception.
     * @param request The current web request.
     * @return A ResponseEntity with HTTP status 409 (Conflict).
     */
    @ExceptionHandler(InvalidJobStateException.class)
    public ResponseEntity<Object> handleInvalidJobStateException(InvalidJobStateException ex, WebRequest request) {
        logger.warn("Invalid job state operation: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    /**
     * Handles JobProcessingException.
     *
     * @param ex      The caught exception.
     * @param request The current web request.
     * @return A ResponseEntity with HTTP status 500 (Internal Server Error) or 422 (Unprocessable Entity).
     */
    @ExceptionHandler(JobProcessingException.class)
    public ResponseEntity<Object> handleJobProcessingException(JobProcessingException ex, WebRequest request) {
        logger.error("Job processing error: {}", ex.getMessage(), ex.getCause());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        // Consider 422 if it's a client-induced processing error (e.g., bad input)
        // Use 500 for genuine internal failures during processing.
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", "Error processing job: " + ex.getMessage());
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, status);
    }

    /**
     * Handles validation errors (e.g., @Valid annotation).
     *
     * @param ex      The caught exception.
     * @param request The current web request.
     * @return A ResponseEntity with HTTP status 400 (Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        logger.warn("Validation error: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        // Extract specific validation messages if needed
        body.put("message", "Validation failed: " + ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles generic exceptions as a fallback.
     *
     * @param ex      The caught exception.
     * @param request The current web request.
     * @return A ResponseEntity with HTTP status 500 (Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unhandled exception occurred: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred. Please contact support.");
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 