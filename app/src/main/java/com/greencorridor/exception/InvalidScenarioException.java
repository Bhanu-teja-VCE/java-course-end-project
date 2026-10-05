package com.greencorridor.exception;

/** A scenario has a missing or out-of-range value. Remembers which field is wrong. */
public class InvalidScenarioException extends SimulationException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidScenarioException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Name of the offending field, so the form can put the cursor there. */
    public String getField() {
        return field;
    }
}
