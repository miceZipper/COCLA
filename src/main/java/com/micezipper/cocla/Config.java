package com.micezipper.cocla;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    public static String dbHost, dbPort, dbName, dbUser, dbPassword, logDirectory, jdbcUrl;
    public static int watchInterval;
    public static boolean useSSL, allowPublicKeyRetrieval;

    public static final String DEFAULT_CONFIG_PATH = "config" + File.separator + "cocla" + File.separator + "config.properties";

    public static void init(String[] args) {
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
        rebuildJdbcUrl();
    }

    private static void loadFromFile(String path) {
        File configFile = new File(path);
        if (!configFile.exists()) {
            System.out.println("Configuration file is not found or not specified. Creating the default one");
            configFile = new File(DEFAULT_CONFIG_PATH);
            try {
                configFile.createNewFile();
            } catch (IOException ex) {
                System.getLogger(Config.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }

        try (InputStream input = new FileInputStream(configFile)) {
            Properties props = new Properties();
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
        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
        }

        rebuildJdbcUrl();
    }

    private static void rebuildJdbcUrl() {
        jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=%s&allowPublicKeyRetrieval=%s&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false",
                dbHost, dbPort, dbName, useSSL, allowPublicKeyRetrieval);
    }
}
