package com.greencorridor.model;

/** How a junction decides when to change its lights. */
public enum SignalMode {
    FIXED("Fixed-time", "Every road gets the same green time in every cycle."),
    ADAPTIVE("Adaptive (vehicle-actuated)",
            "Green is cut short when the road is empty and extended while vehicles keep coming.");

    private final String label;
    private final String description;

    SignalMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }
}
