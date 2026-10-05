package com.greencorridor.ui;

import com.greencorridor.db.InMemoryScenarioRepository;
import com.greencorridor.db.MySqlScenarioRepository;
import com.greencorridor.db.RunDao;
import com.greencorridor.db.ScenarioRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * State shared by all tabs: are we connected to MySQL, and which repository to use.
 * Tabs register a listener and refresh when the connection changes.
 * Used only on the Swing event dispatch thread.
 */
public class AppContext {

    private ScenarioRepository scenarios = new InMemoryScenarioRepository();
    private RunDao runDao;
    private String status = "Connecting to MySQL...";
    private final List<Runnable> listeners = new ArrayList<Runnable>();

    public boolean isOnline() {
        return runDao != null;
    }

    public void goOnline(String description) {
        scenarios = new MySqlScenarioRepository();
        runDao = new RunDao();
        status = "MySQL connected: " + description;
        fire();
    }

    public void goOffline(String reason) {
        if (!(scenarios instanceof InMemoryScenarioRepository)) {
            scenarios = new InMemoryScenarioRepository();
        }
        runDao = null;
        status = "Offline (runs are not saved): " + reason;
        fire();
    }

    public ScenarioRepository getScenarios() {
        return scenarios;
    }

    /** Null when offline. */
    public RunDao getRunDao() {
        return runDao;
    }

    public String getStatus() {
        return status;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Tells every tab that the data changed (connection, scenarios, runs). */
    public void fire() {
        for (Runnable r : listeners) {
            r.run();
        }
    }
}
