package com.greencorridor.engine;

import com.greencorridor.db.Database;
import com.greencorridor.db.RunDao;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.io.RunLogWriter;
import com.greencorridor.model.SimEvent;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The consumer thread (extends Thread). It takes events out of the shared
 * {@link EventBuffer}, writes them to the run's log file and inserts them into MySQL in
 * batches. Vehicles never wait for the database: they only drop an event in the buffer.
 * It runs at the lowest thread priority because nothing on screen depends on it.
 */
public class DatabaseLogger extends Thread implements Monitorable {

    private final EventBuffer<SimEvent> buffer;
    private final int runId;                    // -1 when the run is not stored in MySQL
    private final SimulationEngine engine;

    private volatile String status = "starting";
    private volatile int eventsLogged;
    private volatile int rowsSaved;
    private volatile boolean dbHealthy;

    public DatabaseLogger(EventBuffer<SimEvent> buffer, int runId, SimulationEngine engine) {
        super("DB-Logger");
        this.buffer = buffer;
        this.runId = runId;
        this.engine = engine;
        setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
        Connection con = null;
        PreparedStatement trips = null;
        PreparedStatement preemptions = null;
        try {
            if (runId > 0) {
                try {
                    con = Database.getConnection();
                    con.setAutoCommit(false);           // one transaction per batch
                    trips = con.prepareStatement(RunDao.INSERT_TRIP);
                    preemptions = con.prepareStatement(RunDao.INSERT_PREEMPTION);
                    dbHealthy = true;
                } catch (SQLException | RepositoryException e) {
                    engine.log("Logger cannot reach MySQL, events go to the log file only: " + e.getMessage());
                }
            }
            try (RunLogWriter file = new RunLogWriter(RunLogWriter.fileFor(runId),
                    "GreenCorridor run " + (runId > 0 ? "#" + runId : "(not saved in MySQL)"))) {
                int pending = 0;
                while (true) {
                    status = "waiting in take() for events";
                    SimEvent event = buffer.take();
                    if (event == SimEvent.END) {
                        break;
                    }
                    status = "writing";
                    file.write(event);
                    eventsLogged++;
                    if (dbHealthy) {
                        pending = saveToDatabase(con, event, trips, preemptions, pending);
                    }
                }
                if (dbHealthy && pending > 0) {
                    try {
                        flush(con, trips, preemptions, pending);
                    } catch (SQLException e) {
                        dbHealthy = false;
                        engine.log("Saving the last events to MySQL failed: " + e.getMessage());
                    }
                }
                status = "finished (" + eventsLogged + " events, log: " + file.getFile().getPath() + ")";
            } catch (IOException e) {
                engine.log("Could not write the run log: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            status = "interrupted";
        } finally {
            closeQuietly(trips);
            closeQuietly(preemptions);
            closeQuietly(con);
        }
    }

    private int saveToDatabase(Connection con, SimEvent event, PreparedStatement trips,
                               PreparedStatement preemptions, int pending) {
        try {
            RunDao.addToBatch(event, runId, trips, preemptions);
            pending++;
            // Send the batch when it is full, or when we have caught up with the producers.
            if (pending >= SimConfig.DB_BATCH_SIZE || buffer.size() == 0) {
                flush(con, trips, preemptions, pending);
                pending = 0;
            }
        } catch (SQLException e) {
            dbHealthy = false;
            engine.log("Saving to MySQL failed, continuing with the log file only: " + e.getMessage());
        }
        return pending;
    }

    private void flush(Connection con, PreparedStatement trips, PreparedStatement preemptions, int count)
            throws SQLException {
        try {
            trips.executeBatch();
            preemptions.executeBatch();
            con.commit();
            rowsSaved += count;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        }
    }

    /** True while every event so far has reached MySQL. */
    public boolean isDbHealthy() {
        return dbHealthy;
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // nothing useful to do while shutting down
            }
        }
    }

    @Override
    public String getRole() {
        return "Database logger (consumer)";
    }

    @Override
    public String getStatusText() {
        return status + " | buffer " + buffer.size() + "/" + buffer.getCapacity()
                + " | logged " + eventsLogged + (runId > 0 ? ", saved to MySQL " + rowsSaved : "");
    }
}
