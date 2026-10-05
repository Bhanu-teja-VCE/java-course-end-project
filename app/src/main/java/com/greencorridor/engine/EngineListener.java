package com.greencorridor.engine;

import com.greencorridor.model.RunSummary;

/**
 * Callbacks from the engine to the user interface. Both methods are called from
 * simulation threads, so a Swing implementation must hand the work to the event
 * dispatch thread with SwingUtilities.invokeLater().
 */
public interface EngineListener {

    void onLog(String message);

    void onRunFinished(RunSummary summary);
}
