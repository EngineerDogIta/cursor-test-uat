package com.example.exception;

/**
 * Exception thrown when an error occurs during the processing of a TestGenerationJob.
 */
public class JobProcessingException extends RuntimeException {

    /**
     * Constructs a new JobProcessingException with the specified detail message.
     *
     * @param message the detail message.
     */
    public JobProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new JobProcessingException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause.
     */
    public JobProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
} 