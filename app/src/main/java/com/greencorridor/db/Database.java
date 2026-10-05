package com.greencorridor.db;

import com.greencorridor.exception.DatabaseUnavailableException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Hands out JDBC connections through DriverManager.
 * MySQL Connector/J is a Type 4 driver: pure Java, talking MySQL's network protocol
 * directly, so nothing else has to be installed on the client.
 */
public final class Database {

    public static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static volatile DbConfig config = DbConfig.load();

    private Database() {
    }

    public static Connection getConnection() throws DatabaseUnavailableException {
        return getConnection(config);
    }

    /** Opens a connection with the given settings (used by "Test connection"). */
    public static Connection getConnection(DbConfig c) throws DatabaseUnavailableException {
        try {
            Class.forName(DRIVER_CLASS);              // load the driver class (registers itself)
            DriverManager.setLoginTimeout(5);
            return DriverManager.getConnection(c.getUrl(), c.getUser(), c.getPassword());
        } catch (ClassNotFoundException e) {
            throw new DatabaseUnavailableException("MySQL JDBC driver is not on the classpath", e);
        } catch (SQLException e) {
            throw new DatabaseUnavailableException("Cannot connect to MySQL as " + c.describe(), e);
        }
    }

    public static DbConfig getConfig() {
        return config;
    }

    public static void setConfig(DbConfig newConfig) {
        config = newConfig;
    }
}
