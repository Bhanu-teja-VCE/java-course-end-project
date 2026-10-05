package com.greencorridor.db;

import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.PreemptionEvent;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.RunSummary;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;
import com.greencorridor.model.TripRecord;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Database access for simulation runs. Shows all three kinds of JDBC statement:
 * Statement (fixed query), PreparedStatement (parameters, batches) and
 * CallableStatement (stored procedures with IN and OUT parameters).
 */
public class RunDao {

    public static final String INSERT_TRIP = "INSERT INTO vehicle_trip (run_id, vehicle_no, vehicle_type, "
            + "is_emergency, direction, entry_sim_ms, exit_sim_ms, travel_sec, delay_sec, wait_sec, stops) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String INSERT_PREEMPTION = "INSERT INTO preemption_event (run_id, junction_no, "
            + "vehicle_no, vehicle_type, direction, requested_sim_ms, green_sim_ms, cleared_sim_ms, response_sec) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String[] RUN_COLUMNS = {
        "Run", "Scenario", "Signals", "Priority", "Junctions", "Started", "Sim time (s)", "Status",
        "Trips", "Avg delay (s)", "Emergency trips", "Emergency delay (s)", "Throughput (/min)"
    };

    private final java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String SELECT_RUNS = "SELECT run_id, scenario_name, signal_mode, priority_mode, "
            + "junction_count, started_at, sim_seconds, status, vehicles_completed, avg_delay_sec, "
            + "emergency_count, emergency_avg_delay_sec, throughput_per_min "
            + "FROM simulation_run ORDER BY run_id DESC";

    private static final String TYPE_BREAKDOWN = "SELECT vehicle_type AS \"Vehicle type\", COUNT(*) AS \"Trips\", "
            + "ROUND(AVG(travel_sec), 2) AS \"Avg travel (s)\", ROUND(AVG(delay_sec), 2) AS \"Avg delay (s)\", "
            + "ROUND(AVG(wait_sec), 2) AS \"Avg wait (s)\", ROUND(AVG(stops), 2) AS \"Avg stops\" "
            + "FROM vehicle_trip WHERE run_id = ? GROUP BY vehicle_type ORDER BY COUNT(*) DESC";

    private static final String PREEMPTIONS = "SELECT CONCAT('J', junction_no) AS \"Junction\", "
            + "CONCAT(vehicle_type, ' #', vehicle_no) AS \"Vehicle\", direction AS \"Direction\", "
            + "ROUND(requested_sim_ms / 1000, 1) AS \"Requested at (s)\", response_sec AS \"Green after (s)\", "
            + "ROUND((cleared_sim_ms - requested_sim_ms) / 1000, 1) AS \"Held for (s)\" "
            + "FROM preemption_event WHERE run_id = ? ORDER BY requested_sim_ms";

    /** Calls sp_start_run and returns the new run id from its OUT parameter. */
    public int startRun(Scenario scenario, SignalMode signalMode, PriorityMode priorityMode)
            throws RepositoryException {
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_start_run(?, ?, ?, ?, ?, ?)}")) {
            if (scenario.getId() > 0) {
                cs.setInt(1, scenario.getId());
            } else {
                cs.setNull(1, Types.INTEGER);
            }
            cs.setString(2, scenario.getName());
            cs.setString(3, signalMode.name());
            cs.setString(4, priorityMode.name());
            cs.setInt(5, scenario.getJunctionCount());
            cs.registerOutParameter(6, Types.INTEGER);
            cs.execute();
            return cs.getInt(6);
        } catch (SQLException e) {
            throw new RepositoryException("Could not create the run record", e);
        }
    }

    /**
     * Calls sp_finish_run, which computes the run's figures inside MySQL, and copies the
     * values it returns (OUT parameters) into the summary.
     */
    public void finishRun(int runId, int simSeconds, String status, RunSummary summary)
            throws RepositoryException {
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_finish_run(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, runId);
            cs.setInt(2, simSeconds);
            cs.setString(3, status);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.registerOutParameter(5, Types.DECIMAL);
            cs.registerOutParameter(6, Types.DECIMAL);
            cs.execute();
            summary.setRunId(runId);
            summary.setTripsCompleted(cs.getInt(4));
            BigDecimal avg = cs.getBigDecimal(5);
            if (avg != null) {
                summary.setAvgDelaySec(avg.doubleValue());
            }
            BigDecimal emergency = cs.getBigDecimal(6);
            if (emergency != null) {
                summary.setEmergencyAvgDelaySec(emergency.doubleValue());
            }
            summary.setVerifiedByDatabase(true);
        } catch (SQLException e) {
            throw new RepositoryException("Could not close run " + runId, e);
        }
    }

    /** Adds one event to the right batch (used by the logger thread on its own connection). */
    public static void addToBatch(Object event, int runId, PreparedStatement trips, PreparedStatement preemptions)
            throws SQLException {
        if (event instanceof TripRecord) {
            TripRecord t = (TripRecord) event;
            trips.setInt(1, runId);
            trips.setInt(2, t.getVehicleNo());
            trips.setString(3, t.getVehicleType());
            trips.setBoolean(4, t.isEmergency());
            trips.setString(5, t.getDirection().name());
            trips.setLong(6, t.getEntrySimMs());
            trips.setLong(7, t.getExitSimMs());
            trips.setDouble(8, round2(t.getTravelSec()));
            trips.setDouble(9, round2(t.getDelaySec()));
            trips.setDouble(10, round2(t.getWaitSec()));
            trips.setInt(11, t.getStops());
            trips.addBatch();
        } else if (event instanceof PreemptionEvent) {
            PreemptionEvent p = (PreemptionEvent) event;
            preemptions.setInt(1, runId);
            preemptions.setInt(2, p.getJunctionNo());
            preemptions.setInt(3, p.getVehicleNo());
            preemptions.setString(4, p.getVehicleType());
            preemptions.setString(5, p.getDirection().name());
            preemptions.setLong(6, p.getRequestedSimMs());
            if (p.getGreenSimMs() >= 0) {
                preemptions.setLong(7, p.getGreenSimMs());
            } else {
                preemptions.setNull(7, Types.BIGINT);
            }
            preemptions.setLong(8, p.getClearedSimMs());
            preemptions.setDouble(9, round2(p.getResponseSec()));
            preemptions.addBatch();
        }
    }

    /** All runs, newest first, one Object[] per row in the order of RUN_COLUMNS. */
    public List<Object[]> listRuns() throws RepositoryException {
        List<Object[]> rows = new ArrayList<Object[]>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(SELECT_RUNS)) {
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getInt("run_id"),
                    rs.getString("scenario_name"),
                    rs.getString("signal_mode"),
                    rs.getString("priority_mode"),
                    rs.getInt("junction_count"),
                    dateFormat.format(rs.getTimestamp("started_at")),
                    rs.getInt("sim_seconds"),
                    rs.getString("status"),
                    rs.getInt("vehicles_completed"),
                    rs.getBigDecimal("avg_delay_sec"),
                    rs.getInt("emergency_count"),
                    rs.getBigDecimal("emergency_avg_delay_sec"),
                    rs.getBigDecimal("throughput_per_min")
                });
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the runs", e);
        }
        return rows;
    }

    public boolean deleteRun(int runId) throws RepositoryException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM simulation_run WHERE run_id = ?")) {
            ps.setInt(1, runId);
            return ps.executeUpdate() > 0;      // trips and pre-emptions go too (ON DELETE CASCADE)
        } catch (SQLException e) {
            throw new RepositoryException("Could not delete run " + runId, e);
        }
    }

    /** Calls sp_mode_comparison, a stored procedure that returns a result set. */
    public List<ModeStat> compareModes(String signalFilter) throws RepositoryException {
        List<ModeStat> stats = new ArrayList<ModeStat>();
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_mode_comparison(?)}")) {
            cs.setString(1, signalFilter);
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    stats.add(new ModeStat(PriorityMode.valueOf(rs.getString("priority_mode")),
                            rs.getInt("runs"),
                            toDouble(rs.getBigDecimal("avg_delay_sec")),
                            toDouble(rs.getBigDecimal("emergency_avg_delay_sec")),
                            toDouble(rs.getBigDecimal("throughput_per_min"))));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not compare the priority modes", e);
        }
        return stats;
    }

    /** Per-vehicle-type figures of one run. First row holds the column names. */
    public List<Object[]> typeBreakdown(int runId) throws RepositoryException {
        return query(TYPE_BREAKDOWN, runId);
    }

    /** Every pre-emption of one run. First row holds the column names. */
    public List<Object[]> preemptions(int runId) throws RepositoryException {
        return query(PREEMPTIONS, runId);
    }

    /** Runs a one-parameter query and returns header + rows, using ResultSetMetaData for the names. */
    private List<Object[]> query(String sql, int runId) throws RepositoryException {
        List<Object[]> rows = new ArrayList<Object[]>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int n = meta.getColumnCount();
                Object[] header = new Object[n];
                for (int i = 0; i < n; i++) {
                    header[i] = meta.getColumnLabel(i + 1);
                }
                rows.add(header);
                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int i = 0; i < n; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Query failed", e);
        }
        return rows;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
