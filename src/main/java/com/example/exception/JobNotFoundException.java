package com.example.exception;

/**
 * Exception thrown when a requested TestGenerationJob cannot be found.
 */
public class JobNotFoundException extends RuntimeException {

    /**
     * Constructs a new JobNotFoundException with the specified detail message.
     *
     * @param message the detail message.
     */
    public JobNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new JobNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the getCause() method).
     */
    public JobNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 