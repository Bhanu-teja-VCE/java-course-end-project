package com.greencorridor.db;

import com.greencorridor.exception.RepositoryException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates the tables, procedures and sample data from the script bundled in the jar. */
public final class SchemaInstaller {

    public static final String SCRIPT = "/sql/green_corridor.sql";

    private SchemaInstaller() {
    }

    /** True if the "scenario" table exists in the connected database. */
    public static boolean isInstalled(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, "scenario", new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Runs the script. "CREATE DATABASE" and "USE" are skipped because the connection
     * already points at the configured database.
     *
     * @return number of statements executed
     */
    public static int install(Connection con) throws RepositoryException {
        List<String> statements;
        try (InputStream in = SchemaInstaller.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                throw new RepositoryException("The SQL script " + SCRIPT + " is missing from the program");
            }
            statements = SqlScript.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RepositoryException("Could not read the SQL script", e);
        }
        int count = 0;
        try (Statement st = con.createStatement()) {
            for (String sql : statements) {
                String upper = sql.toUpperCase();
                if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                    continue;
                }
                st.execute(sql);
                count++;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Setting up the database failed at statement " + (count + 1), e);
        }
        return count;
    }
}
