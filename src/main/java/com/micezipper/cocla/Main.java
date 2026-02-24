package com.micezipper.cocla;

public class Main {

    public static void main(String[] args) {
        System.out.println("Champions Online Combat Log Analyzer - Headless Data Forwarder\nVersion 1.0\n");

        // Загружаем конфигурацию
        Config config = new Config(args);

        // Показываем конфигурацию
        System.out.println("Configuration:\n  DB Host: " + config.getDbHost()
                + "\n  DB Name: " + config.getDbName()
                + "\n  Log Directory: " + config.getLogDirectory()
                + "\n  Watch Interval: " + config.getWatchInterval() + "ms");

        // Запускаем приложение
        try (DatabaseManager dbManager = new DatabaseManager(config); LogWatcher watcher = new LogWatcher(config, dbManager)) {

            // Устанавливаем DatabaseManager в LogParser
            LogParser.setDatabaseManager(dbManager);

            watcher.start();

            // Добавляем shutdown hook для корректного завершения
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
            }));

            System.out.println("COCLA is running. Press Ctrl+C to stop.");

            // Бесконечный цикл
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            System.out.println("Interrupted, shutting down...");
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }

}
