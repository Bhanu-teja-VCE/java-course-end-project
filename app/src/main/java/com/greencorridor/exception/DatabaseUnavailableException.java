package com.greencorridor.exception;

/** The MySQL server could not be reached (wrong password, server stopped, driver missing...). */
public class DatabaseUnavailableException extends RepositoryException {

    private static final long serialVersionUID = 1L;

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
