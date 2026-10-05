package com.greencorridor.exception;

/** A scenario file could not be written or read back. */
public class ScenarioFileException extends SimulationException {

    private static final long serialVersionUID = 1L;

    public ScenarioFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
