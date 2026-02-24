package com.micezipper.cocla;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private String dbHost, dbPort, dbName, dbUser, dbPassword, logDirectory;
    private int watchInterval;
    private boolean useSSL, allowPublicKeyRetrieval;

    private static final String DEFAULT_CONFIG_PATH = "config" + File.separator + "cocla" + File.separator + "config.properties";

    public Config() {
        loadFromFile(DEFAULT_CONFIG_PATH);
    }

    public Config(String configPath) {
        loadFromFile(configPath);
    }

    public Config(String[] args) {
        // Сначала загружаем из файла по умолчанию
        loadFromFile(DEFAULT_CONFIG_PATH);

        // Переопределяем аргументами командной строки
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--db-host" -> {
                    if (i + 1 < args.length) {
                        dbHost = args[++i];
                    }
                }
                case "--db-port" -> {
                    if (i + 1 < args.length) {
                        dbPort = args[++i];
                    }
                }
                case "--db-name" -> {
                    if (i + 1 < args.length) {
                        dbName = args[++i];
                    }
                }
                case "--db-user" -> {
                    if (i + 1 < args.length) {
                        dbUser = args[++i];
                    }
                }
                case "--db-password" -> {
                    if (i + 1 < args.length) {
                        dbPassword = args[++i];
                    }
                }
                case "--log-dir" -> {
                    if (i + 1 < args.length) {
                        logDirectory = args[++i];
                    }
                }
                case "--config" -> {
                    if (i + 1 < args.length) {
                        loadFromFile(args[++i]);
                    }
                }
            }
        }
    }

    private void loadFromFile(String path) {
        Properties props = new Properties();
        File configFile = new File(path);

        if (!configFile.exists()) {
            System.err.println("Config file not found: " + path + ", using defaults");
            setDefaults();
        } else try (InputStream input = new FileInputStream(configFile)) {
            props.load(input);

            dbHost = props.getProperty("db.host", "localhost");
            dbPort = props.getProperty("db.port", "3306");
            dbName = props.getProperty("db.name", "cocla");
            dbUser = props.getProperty("db.user", "cocla");
            dbPassword = props.getProperty("db.password", "cocla");
            logDirectory = props.getProperty("log.directory", "C:/Program Files (x86)/Steam/steamapps/common/Champions Online/Champions Online/Live/logs/Client/");
            watchInterval = Integer.parseInt(props.getProperty("watch.interval", "5000"));
            useSSL = Boolean.parseBoolean(props.getProperty("db.useSSL", "false"));
            allowPublicKeyRetrieval = Boolean.parseBoolean(props.getProperty("db.allowPublicKeyRetrieval", "true"));

        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            setDefaults();
        }
    }

    private void setDefaults() {
        dbHost = "db.server.mzp";
        dbPort = "3306";
        dbName = "cocla";
        dbUser = "root";
        dbPassword = "";
        logDirectory = "C:/Program Files (x86)/Steam/steamapps/common/Champions Online/Champions Online/Live/logs/Client/";
        watchInterval = 5000;
        useSSL = false;
        allowPublicKeyRetrieval = true;
    }

    public String getJdbcUrl() {
        return String.format("jdbc:mysql://%s:%s/%s?useSSL=%s&allowPublicKeyRetrieval=%s&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false",
                dbHost, dbPort, dbName, useSSL, allowPublicKeyRetrieval);
    }

    // Геттеры
    public String getDbHost() {
        return dbHost;
    }

    public String getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getLogDirectory() {
        return logDirectory;
    }

    public int getWatchInterval() {
        return watchInterval;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    public boolean isAllowPublicKeyRetrieval() {
        return allowPublicKeyRetrieval;
    }
}
