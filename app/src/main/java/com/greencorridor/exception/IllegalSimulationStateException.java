package com.greencorridor.exception;

/**
 * Unchecked exception: the program asked the engine to do something that makes no sense
 * right now (pausing a run that never started, starting one twice...). This is a
 * programming error rather than a recoverable condition, so it extends RuntimeException.
 */
public class IllegalSimulationStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalSimulationStateException(String message) {
        super(message);
    }
}
