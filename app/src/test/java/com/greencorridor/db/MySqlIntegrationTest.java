package com.greencorridor.db;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs against a real MySQL server, in a separate database "green_corridor_test".
 * Uses the user and password from db.properties; skipped when MySQL is not reachable.
 */
class MySqlIntegrationTest {

    private static DbConfig previous;

    @BeforeAll
    static void connect() throws Exception {
        previous = Database.getConfig();
        DbConfig test = DbConfig.load();
        test.setDatabase("green_corridor_test");
        Database.setConfig(test);
        boolean reachable;
        try (Connection con = Database.getConnection()) {
            SchemaInstaller.install(con);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "MySQL not reachable - skipping database tests");
    }

    @AfterAll
    static void restore() {
        Database.setConfig(previous);
    }

    @Test
    void scenarioCrud() throws Exception {
        MySqlScenarioRepository repo = new MySqlScenarioRepository();
        int before = repo.findAll().size();
        Scenario s = Scenario.builtInDefaults().get(5).copy();
        s.setName("JUnit scenario");
        Scenario saved = repo.save(s);                          // INSERT
        assertTrue(saved.getId() > 0);
        assertEquals(before + 1, repo.findAll().size());

        saved.setMainRatePerMin(33);
        repo.save(saved);                                       // UPDATE
        assertEquals(33, repo.findById(saved.getId()).getMainRatePerMin());

        Scenario duplicate = s.copy();
        duplicate.setId(0);
        InvalidScenarioException e = assertThrows(InvalidScenarioException.class, () -> repo.save(duplicate));
        assertEquals("name", e.getField(), "the UNIQUE constraint becomes a friendly error");

        assertTrue(repo.delete(saved.getId()));                 // DELETE
        assertNull(repo.findById(saved.getId()));
    }

    @Test
    void storedProceduresWork() throws Exception {
        RunDao dao = new RunDao();
        Scenario s = new MySqlScenarioRepository().findAll().get(0);
        int runId = dao.startRun(s, SignalMode.FIXED, PriorityMode.CORRIDOR);   // CallableStatement + OUT
        assertTrue(runId > 0);
        RunSummary summary = new RunSummary();
        dao.finishRun(runId, 60, "COMPLETED", summary);
        assertTrue(summary.isVerifiedByDatabase());
        assertEquals(0, summary.getTripsCompleted());
        List<Object[]> runs = dao.listRuns();
        assertEquals(runId, runs.get(0)[0]);
        List<Object[]> types = dao.typeBreakdown(runId);
        assertEquals("Vehicle type", types.get(0)[0], "column labels come from ResultSetMetaData");
        dao.compareModes("ALL");                                 // procedure returning a result set
        assertTrue(dao.deleteRun(runId));
    }
}
