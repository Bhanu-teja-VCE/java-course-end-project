package com.greencorridor.db;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.RepositoryException;
import com.greencorridor.model.PriorityMode;
import com.greencorridor.model.Scenario;
import com.greencorridor.model.SignalMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Create, Retrieve, Update and Delete of scenarios in MySQL.
 * Every value from the user goes through a PreparedStatement parameter, never string
 * concatenation, so the queries are safe from SQL injection.
 */
public class MySqlScenarioRepository implements ScenarioRepository {

    private static final String COLUMNS = "name, description, junction_count, main_rate_per_min, "
            + "cross_rate_per_min, signal_mode, priority_mode, main_green_sec, cross_green_sec, yellow_sec, "
            + "vehicle_mix, emergency_every_sec, duration_sec, random_seed";

    private static final String SELECT_ALL =
            "SELECT scenario_id, " + COLUMNS + " FROM scenario ORDER BY scenario_id";
    private static final String SELECT_ONE =
            "SELECT scenario_id, " + COLUMNS + " FROM scenario WHERE scenario_id = ?";
    private static final String INSERT =
            "INSERT INTO scenario (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE =
            "UPDATE scenario SET name = ?, description = ?, junction_count = ?, main_rate_per_min = ?, "
            + "cross_rate_per_min = ?, signal_mode = ?, priority_mode = ?, main_green_sec = ?, "
            + "cross_green_sec = ?, yellow_sec = ?, vehicle_mix = ?, emergency_every_sec = ?, "
            + "duration_sec = ?, random_seed = ? WHERE scenario_id = ?";
    private static final String DELETE = "DELETE FROM scenario WHERE scenario_id = ?";

    @Override
    public List<Scenario> findAll() throws RepositoryException {
        List<Scenario> list = new ArrayList<Scenario>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load scenarios", e);
        }
        return list;
    }

    @Override
    public Scenario findById(int id) throws RepositoryException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ONE)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load scenario " + id, e);
        }
    }

    @Override
    public Scenario save(Scenario scenario) throws RepositoryException, InvalidScenarioException {
        scenario.validate();
        Scenario saved = scenario.copy();
        try (Connection con = Database.getConnection()) {
            if (saved.getId() == 0) {
                try (PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps, saved);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            saved.setId(keys.getInt(1));
                        }
                    }
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(UPDATE)) {
                    bind(ps, saved);
                    ps.setInt(15, saved.getId());
                    if (ps.executeUpdate() == 0) {
                        throw new RepositoryException("Scenario " + saved.getId() + " no longer exists");
                    }
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new InvalidScenarioException("name", "A scenario called \"" + saved.getName() + "\" already exists.");
        } catch (SQLException e) {
            throw new RepositoryException("Could not save the scenario", e);
        }
        return saved;
    }

    @Override
    public boolean delete(int id) throws RepositoryException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Could not delete scenario " + id, e);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private static void bind(PreparedStatement ps, Scenario s) throws SQLException {
        ps.setString(1, s.getName());
        ps.setString(2, s.getDescription());
        ps.setInt(3, s.getJunctionCount());
        ps.setInt(4, s.getMainRatePerMin());
        ps.setInt(5, s.getCrossRatePerMin());
        ps.setString(6, s.getSignalMode().name());
        ps.setString(7, s.getPriorityMode().name());
        ps.setInt(8, s.getMainGreenSec());
        ps.setInt(9, s.getCrossGreenSec());
        ps.setInt(10, s.getYellowSec());
        ps.setString(11, s.getVehicleMix());
        ps.setInt(12, s.getEmergencyEverySec());
        ps.setInt(13, s.getDurationSec());
        ps.setInt(14, s.getRandomSeed());
    }

    private static Scenario map(ResultSet rs) throws SQLException {
        Scenario s = new Scenario(rs.getString("name"));
        s.setId(rs.getInt("scenario_id"));
        s.setDescription(rs.getString("description"));
        s.setJunctionCount(rs.getInt("junction_count"));
        s.setMainRatePerMin(rs.getInt("main_rate_per_min"));
        s.setCrossRatePerMin(rs.getInt("cross_rate_per_min"));
        s.setSignalMode(SignalMode.valueOf(rs.getString("signal_mode")));
        s.setPriorityMode(PriorityMode.valueOf(rs.getString("priority_mode")));
        s.setMainGreenSec(rs.getInt("main_green_sec"));
        s.setCrossGreenSec(rs.getInt("cross_green_sec"));
        s.setYellowSec(rs.getInt("yellow_sec"));
        s.setVehicleMix(rs.getString("vehicle_mix"));
        s.setEmergencyEverySec(rs.getInt("emergency_every_sec"));
        s.setDurationSec(rs.getInt("duration_sec"));
        s.setRandomSeed(rs.getInt("random_seed"));
        return s;
    }
}
