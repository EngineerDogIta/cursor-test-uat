package com.example.exception;

/**
 * Exception thrown when an operation is attempted on a job that is in an inappropriate state
 * (e.g., trying to delete a job that is currently running).
 */
public class InvalidJobStateException extends RuntimeException {

    /**
     * Constructs a new InvalidJobStateException with the specified detail message.
     *
     * @param message the detail message.
     */
    public InvalidJobStateException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidJobStateException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause.
     */
    public InvalidJobStateException(String message, Throwable cause) {
        super(message, cause);
    }
} 