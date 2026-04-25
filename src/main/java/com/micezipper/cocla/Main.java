package com.micezipper.cocla;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Champions Online Combat Log Analyzer - Headless Data Forwarder\nVersion 1.0\n");

        //load config and app
        Config.init(args);
        DatabaseManager.init();
        LogWatcher.init();

        // add shutdown hook for the correct exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            DatabaseManager.shutdown();
            LogWatcher.shutdown();
        }));

        System.out.println("COCLA is running. Press Ctrl+C to stop.");

        // loop
        Thread.currentThread().join();
    }

}
