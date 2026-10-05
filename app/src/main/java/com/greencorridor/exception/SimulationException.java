package com.greencorridor.exception;

/**
 * Base class of all checked exceptions thrown by GreenCorridor.
 * Being checked, the compiler forces callers to handle or declare them.
 */
public class SimulationException extends Exception {

    private static final long serialVersionUID = 1L;

    public SimulationException(String message) {
        super(message);
    }

    public SimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}
