package com.greencorridor.db;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.Scenario;

import java.util.List;

/**
 * Where scenarios are kept. The program talks only to this interface; at run time the
 * object behind it is either {@link MySqlScenarioRepository} or, without a database,
 * {@link InMemoryScenarioRepository} (dynamic method dispatch).
 */
public interface ScenarioRepository {

    List<Scenario> findAll() throws RepositoryException;

    Scenario findById(int id) throws RepositoryException;

    /** Inserts a new scenario (id 0) or updates an existing one. Returns the saved copy. */
    Scenario save(Scenario scenario) throws RepositoryException, InvalidScenarioException;

    boolean delete(int id) throws RepositoryException;

    /** True if changes survive a restart of the program. */
    boolean isPersistent();
}
