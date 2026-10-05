package com.greencorridor.db;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * MySQL connection settings, kept in "db.properties" next to the program so every lab
 * machine can use its own password. Read with FileReader and written with FileWriter.
 */
public class DbConfig {

    public static final String FILE_NAME = "db.properties";

    private String host = "localhost";
    private int port = 3306;
    private String database = "green_corridor";
    private String user = "root";
    private String password = "";

    /** Loads the settings file, or returns the defaults if there is none. */
    public static DbConfig load() {
        DbConfig config = new DbConfig();
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return config;
        }
        Properties props = new Properties();
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
            config.host = props.getProperty("host", config.host).trim();
            config.port = Integer.parseInt(props.getProperty("port", String.valueOf(config.port)).trim());
            config.database = props.getProperty("database", config.database).trim();
            config.user = props.getProperty("user", config.user).trim();
            config.password = props.getProperty("password", config.password);
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read " + FILE_NAME + " (" + e.getMessage() + "); using defaults.");
        }
        return config;
    }

    public void save() throws IOException {
        Properties props = new Properties();
        props.setProperty("host", host);
        props.setProperty("port", String.valueOf(port));
        props.setProperty("database", database);
        props.setProperty("user", user);
        props.setProperty("password", password);
        try (FileWriter writer = new FileWriter(FILE_NAME)) {
            props.store(writer, "GreenCorridor - MySQL connection settings");
        }
    }

    /** JDBC URL. The database is created automatically if it does not exist yet. */
    public String getUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC&connectTimeout=5000";
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String describe() {
        return user + "@" + host + ":" + port + "/" + database;
    }
}
