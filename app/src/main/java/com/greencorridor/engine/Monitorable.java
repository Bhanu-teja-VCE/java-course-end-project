package com.greencorridor.engine;

/** Anything that runs on its own thread and can describe itself in the Thread Monitor. */
public interface Monitorable {

    /** Short role, for example "Signal controller" or "Car". */
    String getRole();

    /** What the thread is doing right now. */
    String getStatusText();
}
