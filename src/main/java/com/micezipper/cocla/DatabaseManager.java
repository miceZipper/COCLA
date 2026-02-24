package com.micezipper.cocla;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;
    private static final int BATCH_SIZE = 100;
    private int totalInserted = 0;
    private int totalSkipped = 0;
    private int totalErrors = 0;
    
    // Кэш для impact типов (name -> id)
    private final Map<String, Integer> impactTypeCache = new ConcurrentHashMap<>();

    public DatabaseManager(Config config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");

        this.dataSource = new HikariDataSource(hikariConfig);
        
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("Database connection established successfully");
            loadImpactTypeCache();
        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void loadImpactTypeCache() {
        String sql = "SELECT id, name FROM impact_types";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                impactTypeCache.put(rs.getString("name"), rs.getInt("id"));
            }
            System.out.println("Loaded " + impactTypeCache.size() + " impact types from database");
            
        } catch (SQLException e) {
            System.err.println("Failed to load impact types: " + e.getMessage());
        }
    }

    private int getOrCreateImpactType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return -1;
        
        Integer cachedId = impactTypeCache.get(typeName);
        if (cachedId != null) {
            return cachedId;
        }

        String insertSql = "INSERT IGNORE INTO impact_types (name) VALUES (?)";
        String selectSql = "SELECT id FROM impact_types WHERE name = ?";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, typeName);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, typeName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int id = rs.getInt("id");
                    impactTypeCache.put(typeName, id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get/create impact type '" + typeName + "': " + e.getMessage());
        }

        return -1;
    }

    public long countEntriesFromFile(String fileName) {
        String sql = "SELECT COUNT(*) FROM combat_logs WHERE file_name = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, fileName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Error counting entries from file: " + e.getMessage());
        }
        
        return 0;
    }

    public Timestamp getLastTimestampFromFile(String fileName) {
        String sql = "SELECT MAX(log_timestamp) FROM combat_logs WHERE file_name = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, fileName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getTimestamp(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting last timestamp: " + e.getMessage());
        }
        
        return null;
    }

    private String calculateHash(String line) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(line.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            return String.valueOf(line.hashCode());
        }
    }

    public void saveBatch(List<CombatLogEntry> entries) {
        if (entries.isEmpty()) return;

        String mainSql = """
            INSERT IGNORE INTO combat_logs (
                log_timestamp, source_name, source_id, source_owner,
                summoner_name, summoner_id, victim_name, victim_id,
                power_name, power_id, attack_type,
                actual_impact, pure_impact, raw_line, line_hash, file_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        List<List<CombatLogEntry>> batches = partition(entries, BATCH_SIZE);
        int batchInserted = 0;
        int batchErrors = 0;

        for (List<CombatLogEntry> batch : batches) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(mainSql, Statement.RETURN_GENERATED_KEYS)) {

                for (CombatLogEntry entry : batch) {
                    String lineHash = calculateHash(entry.getRawLine());
                    
                    // Все поля могут быть NULL, поэтому используем setObject
                    pstmt.setTimestamp(1, Timestamp.valueOf(entry.getTimestamp()));
                    pstmt.setString(2, entry.getSourceName());  // может быть null
                    pstmt.setString(3, entry.getSourceId());    // может быть null
                    pstmt.setString(4, entry.getSourceOwner()); // может быть null
                    pstmt.setString(5, entry.getSummonName());// может быть null
                    pstmt.setString(6, entry.getSummonId());  // может быть null
                    pstmt.setString(7, entry.getVictimName());  // всегда "SELF" или имя
                    pstmt.setString(8, entry.getVictimId());    // всегда "SELF" или ID
                    pstmt.setString(9, entry.getPowerName());   // может быть null
                    pstmt.setString(10, entry.getPowerId());    // может быть null
                    pstmt.setString(11, entry.getAttackType()); // может быть null
                    pstmt.setDouble(12, entry.getActualImpact());
                    pstmt.setDouble(13, entry.getPureImpact());
                    pstmt.setString(14, entry.getRawLine());
                    pstmt.setString(15, lineHash);
                    pstmt.setString(16, entry.getFileName());

                    pstmt.addBatch();
                }

                int[] results = pstmt.executeBatch();
                
                // Получаем сгенерированные ID
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                List<CombatLogEntry> successfullyInserted = new ArrayList<>();
                
                for (CombatLogEntry entry : batch) {
                    if (generatedKeys.next()) {
                        entry.setDbId(generatedKeys.getLong(1));
                        successfullyInserted.add(entry);
                    }
                }

                // Сохраняем impact types только для успешно вставленных записей
                if (!successfullyInserted.isEmpty()) {
                    saveImpactTypes(conn, successfullyInserted);
                }

                // Подсчитываем результаты
                for (int result : results) {
                    if (result == 1 || result == Statement.SUCCESS_NO_INFO) {
                        batchInserted++;
                    } else if (result == 0) {
                        // Дубликат
                        totalSkipped++;
                    }
                }

            } catch (SQLException e) {
                System.err.println("Batch error: " + e.getMessage());
                batchErrors++;
                
                // Если batch упал, пробуем по одному
                int individuallySaved = saveIndividually(batch);
                batchInserted += individuallySaved;
                batchErrors += (batch.size() - individuallySaved);
            }
        }

        totalInserted += batchInserted;
        totalErrors += batchErrors;
        
        if (batchInserted > 0 || batchErrors > 0) {
            System.out.println("Batch results - Inserted: " + batchInserted + 
                             ", Skipped: " + totalSkipped + 
                             ", Errors: " + batchErrors + 
                             " (Total: " + totalInserted + " inserted)");
        }
    }
    
    public void saveEntityType(String entityId, String entityName, String entityType) {
    if (entityId == null || entityId.isEmpty() || entityName == null || entityName.isEmpty()) {
        return;
    }
    
    String sql = """
        INSERT INTO entity_types (entity_id, entity_name, entity_type)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE
            entity_name = VALUES(entity_name),
            entity_type = VALUES(entity_type)
        """;
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, entityId);
        pstmt.setString(2, entityName);
        pstmt.setString(3, entityType);
        pstmt.executeUpdate();
        
    } catch (SQLException e) {
        System.err.println("Failed to save entity type: " + e.getMessage());
    }
}

    private void saveImpactTypes(Connection conn, List<CombatLogEntry> entries) throws SQLException {
        String sql = "INSERT IGNORE INTO combat_log_impacts (log_id, impact_type_id) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (CombatLogEntry entry : entries) {
                if (entry.getDbId() == null) continue;
                
                for (String impactType : entry.getImpactTypes()) {
                    int typeId = getOrCreateImpactType(impactType);
                    if (typeId != -1) {
                        pstmt.setLong(1, entry.getDbId());
                        pstmt.setInt(2, typeId);
                        pstmt.addBatch();
                    }
                }
            }
            pstmt.executeBatch();
        }
    }

    private int saveIndividually(List<CombatLogEntry> entries) {
        String mainSql = """
            INSERT IGNORE INTO combat_logs (
                log_timestamp, source_name, source_id, source_owner,
                summoner_name, summoner_id, victim_name, victim_id,
                power_name, power_id, attack_type,
                actual_impact, pure_impact, raw_line, line_hash, file_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        int saved = 0;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(mainSql, Statement.RETURN_GENERATED_KEYS)) {

            for (CombatLogEntry entry : entries) {
                try {
                    String lineHash = calculateHash(entry.getRawLine());
                    
                    pstmt.setTimestamp(1, Timestamp.valueOf(entry.getTimestamp()));
                    pstmt.setString(2, entry.getSourceName());
                    pstmt.setString(3, entry.getSourceId());
                    pstmt.setString(4, entry.getSourceOwner());
                    pstmt.setString(5, entry.getSummonName());
                    pstmt.setString(6, entry.getSummonId());
                    pstmt.setString(7, entry.getVictimName());
                    pstmt.setString(8, entry.getVictimId());
                    pstmt.setString(9, entry.getPowerName());
                    pstmt.setString(10, entry.getPowerId());
                    pstmt.setString(11, entry.getAttackType());
                    pstmt.setDouble(12, entry.getActualImpact());
                    pstmt.setDouble(13, entry.getPureImpact());
                    pstmt.setString(14, entry.getRawLine());
                    pstmt.setString(15, lineHash);
                    pstmt.setString(16, entry.getFileName());

                    int result = pstmt.executeUpdate();
                    if (result > 0) {
                        ResultSet generatedKeys = pstmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            entry.setDbId(generatedKeys.getLong(1));
                        }
                        
                        // Сохраняем impact types
                        for (String impactType : entry.getImpactTypes()) {
                            int typeId = getOrCreateImpactType(impactType);
                            if (typeId != -1) {
                                try (PreparedStatement impactStmt = conn.prepareStatement(
                                        "INSERT IGNORE INTO combat_log_impacts (log_id, impact_type_id) VALUES (?, ?)")) {
                                    impactStmt.setLong(1, entry.getDbId());
                                    impactStmt.setInt(2, typeId);
                                    impactStmt.executeUpdate();
                                }
                            }
                        }
                        
                        saved++;
                    } else {
                        totalSkipped++;
                    }
                    
                } catch (SQLException e) {
                    System.err.println("Error saving individual entry: " + e.getMessage());
                    System.err.println("Problematic line: " + entry.getRawLine().substring(0, Math.min(100, entry.getRawLine().length())) + "...");
                    totalErrors++;
                }
            }

        } catch (SQLException e) {
            System.err.println("Connection error in individual save: " + e.getMessage());
        }

        if (saved > 0) {
            System.out.println("Individual insert - Saved: " + saved + " of " + entries.size());
        }
        
        return saved;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public void printStats() {
        System.out.println("\n=== Final Statistics ===");
        System.out.println("Total inserted: " + totalInserted);
        System.out.println("Total skipped (duplicates): " + totalSkipped);
        System.out.println("Total errors: " + totalErrors);
        System.out.println("Impact types in cache: " + impactTypeCache.size());
        System.out.println("========================");
    }

    @Override
    public void close() {
        printStats();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection closed");
        }
    }
}