package com.greencorridor.model;

import java.io.Serializable;

/**
 * Something that happened during a run and must be stored. Vehicle and signal threads
 * produce these; the database logger thread consumes them.
 */
public abstract class SimEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Marker put into the event buffer to tell the logger thread that the run is over. */
    public static final SimEvent END = new SimEvent() {
        private static final long serialVersionUID = 1L;

        @Override
        public String toLogLine() {
            return "END";
        }
    };

    /** One human readable line for the run's log file. */
    public abstract String toLogLine();

    protected static String seconds(long simMillis) {
        return String.format("%.1fs", simMillis / 1000.0);
    }
}
