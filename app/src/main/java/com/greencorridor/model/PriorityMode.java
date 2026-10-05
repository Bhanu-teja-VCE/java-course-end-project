package com.greencorridor.model;

/** How junctions react to emergency vehicles. */
public enum PriorityMode {
    NONE("No priority", "Emergency vehicles wait at red lights like everyone else."),
    LOCAL("Local sensor", "A junction turns green only when the emergency vehicle reaches its sensor."),
    CORRIDOR("Green corridor",
            "The emergency vehicle radios every junction ahead, so queues are cleared before it arrives.");

    private final String label;
    private final String description;

    PriorityMode(String label, String description) {
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
