package com.greencorridor.db;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.model.Scenario;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Keeps scenarios in an ArrayList when MySQL is not available (offline mode). */
public class InMemoryScenarioRepository implements ScenarioRepository {

    private final List<Scenario> scenarios = new ArrayList<Scenario>();
    private int nextId = 1;

    public InMemoryScenarioRepository() {
        for (Scenario s : Scenario.builtInDefaults()) {
            s.setId(nextId++);
            scenarios.add(s);
        }
    }

    @Override
    public synchronized List<Scenario> findAll() {
        List<Scenario> copies = new ArrayList<Scenario>();
        for (Scenario s : scenarios) {
            copies.add(s.copy());
        }
        return copies;
    }

    @Override
    public synchronized Scenario findById(int id) {
        for (Scenario s : scenarios) {
            if (s.getId() == id) {
                return s.copy();
            }
        }
        return null;
    }

    @Override
    public synchronized Scenario save(Scenario scenario) throws InvalidScenarioException {
        scenario.validate();
        for (Scenario s : scenarios) {
            if (s.getId() != scenario.getId() && s.getName().equalsIgnoreCase(scenario.getName())) {
                throw new InvalidScenarioException("name", "A scenario called \"" + scenario.getName() + "\" already exists.");
            }
        }
        Scenario stored = scenario.copy();
        if (stored.getId() == 0) {
            stored.setId(nextId++);
            scenarios.add(stored);
        } else {
            boolean replaced = false;
            for (int i = 0; i < scenarios.size(); i++) {
                if (scenarios.get(i).getId() == stored.getId()) {
                    scenarios.set(i, stored);
                    replaced = true;
                }
            }
            if (!replaced) {
                scenarios.add(stored);
            }
        }
        return stored.copy();
    }

    @Override
    public synchronized boolean delete(int id) {
        Iterator<Scenario> it = scenarios.iterator();
        while (it.hasNext()) {
            if (it.next().getId() == id) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}
