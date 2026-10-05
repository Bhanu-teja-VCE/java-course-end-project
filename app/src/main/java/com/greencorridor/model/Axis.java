package com.greencorridor.model;

/** The two conflicting traffic streams at a junction. */
public enum Axis {
    EW("Main road (East-West)"),
    NS("Cross street (North-South)");

    private final String label;

    Axis(String label) {
        this.label = label;
    }

    public Axis other() {
        return this == EW ? NS : EW;
    }

    public String getLabel() {
        return label;
    }
}
