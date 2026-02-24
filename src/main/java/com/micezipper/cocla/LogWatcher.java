package com.micezipper.cocla;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LogWatcher implements AutoCloseable {
    private final Path logDirectory;
    private final DatabaseManager dbManager;
    private final Map<Path, Long> filePositions = new HashMap<>();
    private final Set<Path> processedFiles = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int watchInterval;
    private boolean initialScanDone = false;

    public LogWatcher(Config config, DatabaseManager dbManager) {
        this.logDirectory = Paths.get(config.getLogDirectory());
        this.dbManager = dbManager;
        this.watchInterval = config.getWatchInterval();

        if (!Files.exists(logDirectory)) {
            System.err.println("Warning: Log directory does not exist: " + logDirectory);
        }
    }

    public void start() {
        System.out.println("Starting LogWatcher...");
        
        // Сначала загружаем все существующие логи
        loadAllExistingLogs();
        
        // Запускаем периодическое сканирование для новых записей
        scheduler.scheduleAtFixedRate(this::checkForUpdates, watchInterval, watchInterval, TimeUnit.MILLISECONDS);

        System.out.println("LogWatcher started. Monitoring: " + logDirectory);
    }

    private void loadAllExistingLogs() {
        System.out.println("\n=== Performing initial scan of all existing log files ===");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDirectory)) {
            List<Path> combatLogs = new ArrayList<>();
            
            // Собираем все файлы логов
            for (Path file : stream) {
                if (Files.isRegularFile(file) && LogParser.isCombatLogFile(file)) {
                    combatLogs.add(file);
                }
            }
            
            // Сортируем по дате (старые сначала)
            combatLogs.sort((p1, p2) -> {
                String name1 = p1.getFileName().toString();
                String name2 = p2.getFileName().toString();
                return name1.compareTo(name2);
            });
            
            System.out.println("Found " + combatLogs.size() + " combat log files");
            
            int totalProcessed = 0;
            int totalNewEntries = 0;
            
            // Обрабатываем каждый файл
            for (Path file : combatLogs) {
                long fileSize = Files.size(file);
                filePositions.put(file, fileSize);
                
                System.out.print("Processing " + file.getFileName() + "... ");
                
                // Проверяем, есть ли уже записи из этого файла в БД
                long existingCount = dbManager.countEntriesFromFile(file.getFileName().toString());
                
                if (existingCount == 0) {
                    // Файл еще не загружен - загружаем полностью
                    System.out.println("new file, loading all " + fileSize + " bytes");
                    
                    List<CombatLogEntry> entries = LogParser.parseFile(file, 0, fileSize);
                    if (!entries.isEmpty()) {
                        dbManager.saveBatch(entries);
                        totalNewEntries += entries.size();
                    }
                } else {
                    // Файл уже частично загружен - проверяем, не появились ли новые строки
                    System.out.println("exists (" + existingCount + " entries), checking for new data");
                    
                    // Получаем последнюю запись из этого файла в БД
                    Timestamp lastTimestamp = dbManager.getLastTimestampFromFile(file.getFileName().toString());
                    
                    if (lastTimestamp != null) {
                        // Конвертируем java.sql.Timestamp в java.time.LocalDateTime
                        LocalDateTime lastLocalDateTime = lastTimestamp.toLocalDateTime();
                        
                        // Загружаем только записи после последнего сохраненного времени
                        List<CombatLogEntry> entries = LogParser.parseFileAfter(file, lastLocalDateTime);
                        if (!entries.isEmpty()) {
                            System.out.println("  Found " + entries.size() + " new entries");
                            dbManager.saveBatch(entries);
                            totalNewEntries += entries.size();
                        }
                    }
                }
                
                totalProcessed++;
                processedFiles.add(file);
            }
            
            System.out.println("\n=== Initial scan complete ===");
            System.out.println("Processed " + totalProcessed + " files");
            System.out.println("Added " + totalNewEntries + " new entries to database");
            System.out.println("============================\n");
            
            initialScanDone = true;
            
        } catch (IOException e) {
            System.err.println("Error during initial scan: " + e.getMessage());
        }
    }

    private void checkForUpdates() {
        if (!initialScanDone) return;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDirectory)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file) || !LogParser.isCombatLogFile(file)) {
                    continue;
                }

                long currentSize = Files.size(file);
                Long lastSize = filePositions.get(file);

                if (lastSize == null) {
                    // Новый файл появился после начального сканирования
                    System.out.println("New log file detected: " + file.getFileName());
                    
                    List<CombatLogEntry> entries = LogParser.parseFile(file, 0, currentSize);
                    if (!entries.isEmpty()) {
                        dbManager.saveBatch(entries);
                        System.out.println("  Added " + entries.size() + " entries");
                    }
                    
                    filePositions.put(file, currentSize);
                    processedFiles.add(file);
                    
                } else if (currentSize > lastSize) {
                    // Существующий файл увеличился
                    System.out.println("File updated: " + file.getFileName() + 
                                     " (" + (currentSize - lastSize) + " new bytes)");

                    List<CombatLogEntry> newEntries = LogParser.parseFile(file, lastSize, currentSize);
                    if (!newEntries.isEmpty()) {
                        dbManager.saveBatch(newEntries);
                        System.out.println("  Added " + newEntries.size() + " new entries");
                    }

                    filePositions.put(file, currentSize);
                }
            }
            
            // Проверяем, не были ли удалены старые файлы
            Iterator<Map.Entry<Path, Long>> it = filePositions.entrySet().iterator();
            while (it.hasNext()) {
                Path file = it.next().getKey();
                if (!Files.exists(file)) {
                    System.out.println("Log file removed: " + file.getFileName());
                    it.remove();
                    processedFiles.remove(file);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error checking for updates: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}