package com.example.exception;

public class TicketAnalysisException extends RuntimeException {
    
    public TicketAnalysisException(String message) {
        super(message);
    }
    
    public TicketAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public TicketAnalysisException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
} 