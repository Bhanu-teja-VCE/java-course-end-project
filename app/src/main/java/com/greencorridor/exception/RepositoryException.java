package com.greencorridor.exception;

/** A database operation failed. Wraps the original SQLException. */
public class RepositoryException extends SimulationException {

    private static final long serialVersionUID = 1L;

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""), cause);
    }
}
