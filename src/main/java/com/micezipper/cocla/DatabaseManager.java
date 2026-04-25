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

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static final int BATCH_SIZE = 100;
    private static int totalInserted = 0;
    private static int totalSkipped = 0;
    private static int totalErrors = 0;

    // Кэш для impact типов (name -> id)
    private static final Map<String, Integer> impactTypeCache = new ConcurrentHashMap<>();

    public static void init() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(Config.jdbcUrl);
        hikariConfig.setUsername(Config.dbUser);
        hikariConfig.setPassword(Config.dbPassword);
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

        dataSource = new HikariDataSource(hikariConfig);

        try (Connection conn = dataSource.getConnection()) {
            System.out.println("Database connection established successfully");
        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private static int getOrCreateImpactType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return -1;
        }

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

    public static long countEntriesFromFile(String fileName) {
        String sql = "SELECT COUNT(*) FROM combat_logs WHERE file_name = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

    public static Timestamp getLastTimestampFromFile(String fileName) {
        String sql = "SELECT MAX(log_timestamp) FROM combat_logs WHERE file_name = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

    private static String calculateHash(String line) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(line.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            return String.valueOf(line.hashCode());
        }
    }

    public static void saveBatch(List<CombatLogEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        String mainSql = """
        INSERT IGNORE INTO combat_logs (
            log_timestamp, source_id, summoner_id, victim_id, power_id,
            actual_impact, pure_impact, raw_line, line_hash, file_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        List<List<CombatLogEntry>> batches = partition(entries, BATCH_SIZE);
        int batchInserted = 0;
        int batchErrors = 0;

        for (List<CombatLogEntry> batch : batches) {
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(mainSql, Statement.RETURN_GENERATED_KEYS)) {

                for (CombatLogEntry entry : batch) {
                    String lineHash = calculateHash(entry.getRawLine());

                    pstmt.setTimestamp(1, Timestamp.valueOf(entry.getTimestamp()));

                    // FK-поля (могут быть null)
                    if (entry.getSourceEntityId() != null) {
                        pstmt.setLong(2, entry.getSourceEntityId());
                    } else {
                        pstmt.setNull(2, java.sql.Types.BIGINT);
                    }
                    if (entry.getSummonerEntityId() != null) {
                        pstmt.setLong(3, entry.getSummonerEntityId());
                    } else {
                        pstmt.setNull(3, java.sql.Types.BIGINT);
                    }
                    if (entry.getVictimEntityId() != null) {
                        pstmt.setLong(4, entry.getVictimEntityId());
                    } else {
                        pstmt.setNull(4, java.sql.Types.BIGINT);
                    }
                    if (entry.getPowerId() != null) {
                        pstmt.setLong(5, entry.getPowerId());
                    } else {
                        pstmt.setNull(5, java.sql.Types.BIGINT);
                    }

                    pstmt.setDouble(6, entry.getActualImpact());
                    pstmt.setDouble(7, entry.getPureImpact());
                    pstmt.setString(8, entry.getRawLine());
                    pstmt.setString(9, lineHash);
                    pstmt.setString(10, entry.getFileName());

                    pstmt.addBatch();
                }

                int[] results = pstmt.executeBatch();

                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                List<CombatLogEntry> successfullyInserted = new ArrayList<>();

                for (CombatLogEntry entry : batch) {
                    if (generatedKeys.next()) {
                        entry.setDbId(generatedKeys.getLong(1));
                        successfullyInserted.add(entry);
                    }
                }

                if (!successfullyInserted.isEmpty()) {
                    saveImpactTypes(conn, successfullyInserted);
                }

                for (int result : results) {
                    if (result == 1 || result == Statement.SUCCESS_NO_INFO) {
                        batchInserted++;
                    } else if (result == 0) {
                        totalSkipped++;
                    }
                }

            } catch (SQLException e) {
                System.err.println("Batch error: " + e.getMessage());
                batchErrors++;

                int individuallySaved = saveIndividually(batch);
                batchInserted += individuallySaved;
                batchErrors += (batch.size() - individuallySaved);
            }
        }

        totalInserted += batchInserted;
        totalErrors += batchErrors;

        if (batchInserted > 0 || batchErrors > 0) {
            System.out.println("Batch results - Inserted: " + batchInserted
                    + ", Skipped: " + totalSkipped
                    + ", Errors: " + batchErrors
                    + " (Total: " + totalInserted + " inserted)");
        }
    }

    private static void saveImpactTypes(Connection conn, List<CombatLogEntry> entries) throws SQLException {
        String sql = "INSERT IGNORE INTO combat_log_impacts (log_id, impact_type_id) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (CombatLogEntry entry : entries) {
                if (entry.getDbId() == null) {
                    continue;
                }

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

    private static int saveIndividually(List<CombatLogEntry> entries) {
        String mainSql = """
        INSERT IGNORE INTO combat_logs (
            log_timestamp, source_id, summoner_id, victim_id, power_id,
            actual_impact, pure_impact, raw_line, line_hash, file_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        int saved = 0;

        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(mainSql, Statement.RETURN_GENERATED_KEYS)) {

            for (CombatLogEntry entry : entries) {
                try {
                    String lineHash = calculateHash(entry.getRawLine());

                    pstmt.setTimestamp(1, Timestamp.valueOf(entry.getTimestamp()));

                    if (entry.getSourceEntityId() != null) {
                        pstmt.setLong(2, entry.getSourceEntityId());
                    } else {
                        pstmt.setNull(2, java.sql.Types.BIGINT);
                    }
                    if (entry.getSummonerEntityId() != null) {
                        pstmt.setLong(3, entry.getSummonerEntityId());
                    } else {
                        pstmt.setNull(3, java.sql.Types.BIGINT);
                    }
                    if (entry.getVictimEntityId() != null) {
                        pstmt.setLong(4, entry.getVictimEntityId());
                    } else {
                        pstmt.setNull(4, java.sql.Types.BIGINT);
                    }
                    if (entry.getPowerId() != null) {
                        pstmt.setLong(5, entry.getPowerId());
                    } else {
                        pstmt.setNull(5, java.sql.Types.BIGINT);
                    }

                    pstmt.setDouble(6, entry.getActualImpact());
                    pstmt.setDouble(7, entry.getPureImpact());
                    pstmt.setString(8, entry.getRawLine());
                    pstmt.setString(9, lineHash);
                    pstmt.setString(10, entry.getFileName());

                    int result = pstmt.executeUpdate();
                    if (result > 0) {
                        ResultSet generatedKeys = pstmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            entry.setDbId(generatedKeys.getLong(1));
                        }

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

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Получает или создаёт запись в таблице entities. Environment (source
     * отсутствует) — возвращает 0.
     */
    public static Long getOrCreateEntity(String entityId, String entityName, String entityHandle, String entityType) {
        if (entityId == null || entityId.isEmpty() || entityId.equals("*")) {
            return 0L;
        }

        String selectSql = "SELECT id, entity_name FROM entities WHERE entity_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, entityId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                String existingName = rs.getString("entity_name");

                // Если в БД имя NULL, а у нас есть нормальное имя — обновляем
                if ((existingName == null || existingName.isEmpty()) && entityName != null && !entityName.isEmpty()) {
                    updateEntityName(conn, id, entityName, entityHandle);
                }
                return id;
            }
        } catch (SQLException e) {
            System.err.println("Error finding entity: " + e.getMessage());
        }

        String insertSql = "INSERT INTO entities (entity_id, entity_name, entity_handle, entity_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, entityId);
            pstmt.setString(2, entityName);
            pstmt.setString(3, entityHandle);
            pstmt.setString(4, entityType);
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("Error creating entity: " + e.getMessage());
        }

        return 0L;
    }

    private static void updateEntityName(Connection conn, Long id, String newName, String newHandle) {
        String sql = "UPDATE entities SET entity_name = ?, entity_handle = COALESCE(entity_handle, ?) WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, newHandle);
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating entity name: " + e.getMessage());
        }
    }

    private static void updateEntityIfChanged(Connection conn, Long id, String newName, String newHandle) {
        // Можно добавить логику обновления имени/хэндла если они изменились
        // Пока оставляем заглушку — имена редко меняются
    }

    /**
     * Получает или создаёт запись в таблице powers. powerId всегда должен быть
     * (иначе возвращаем null).
     */
    public static Long getOrCreatePower(String powerId, String powerName, String attackType) {
        if (powerId == null || powerId.isEmpty()) {
            return null;
        }

        // Пытаемся найти по power_id
        String selectSql = "SELECT id, power_name, attack_type FROM powers WHERE power_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, powerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                String existingName = rs.getString("power_name");
                // Если имя было NULL, а теперь появилось — обновляем
                if ((existingName == null || existingName.isEmpty()) && powerName != null && !powerName.isEmpty()) {
                    updatePowerName(conn, id, powerName, attackType);
                }
                return id;
            }
        } catch (SQLException e) {
            System.err.println("Error finding power: " + e.getMessage());
        }

        // Создаём новую запись
        String insertSql = "INSERT INTO powers (power_id, power_name, attack_type) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, powerId);
            pstmt.setString(2, powerName);
            pstmt.setString(3, attackType);
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("Error creating power: " + e.getMessage());
        }

        return null;
    }

    private static void updatePowerName(Connection conn, Long id, String newName, String attackType) {
        String sql = "UPDATE powers SET power_name = ?, attack_type = COALESCE(attack_type, ?) WHERE id = ? AND power_name IS NULL";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, attackType);
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating power name: " + e.getMessage());
        }
    }

    public static void printStats() {
        System.out.println("\n=== Final Statistics ===");
        System.out.println("Total inserted: " + totalInserted);
        System.out.println("Total skipped (duplicates): " + totalSkipped);
        System.out.println("Total errors: " + totalErrors);
        System.out.println("========================");
    }

    public static void shutdown() {
        printStats();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection closed");
        }
    }
}
