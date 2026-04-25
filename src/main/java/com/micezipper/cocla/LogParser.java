package com.micezipper.cocla;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    // Паттерн для разбора строки лога
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(.+?)::(.+)$"
    );

    public static CombatLogEntry parseLine(String line, String fileName) {
        CombatLogEntry entry = new CombatLogEntry();
        entry.setRawLine(line);
        entry.setFileName(fileName);

        try {
            Matcher m = LOG_PATTERN.matcher(line);
            if (!m.find()) {
                System.err.println("Pattern mismatch for line: " + line);
                return null;
            }

            String timestampStr = m.group(1);
            entry.setTimestamp(parseTimestamp(timestampStr));

            String rest = m.group(2);
            String[] fields = rest.split(",", -1);

            if (fields.length < 11) {
                System.err.println("Not enough fields: " + fields.length + " in line: " + line);
                return null;
            }

            // Source — получаем entity_id
            var idPartSource = getSafe(fields, 1);
            if (idPartSource == null || idPartSource.isEmpty() || idPartSource.equals("*")) {
                // Нет ID — это Environment (лава, падение)
                entry.setSourceEntityId(DatabaseManager.getOrCreateEntity(
                        "0", "Environment", null, "Environment"));
            } else {
                String[] sourceInfo = parseEntityInfo(getSafe(fields, 0), getSafe(fields, 1));
                entry.setSourceEntityId(DatabaseManager.getOrCreateEntity(
                        sourceInfo[0], sourceInfo[1], sourceInfo[2], sourceInfo[3]));
            }
            // Summoner
            String[] summonInfo = parseEntityInfo(getSafe(fields, 2), getSafe(fields, 3));
            if (summonInfo[0] != null && !summonInfo[0].equals("*") && !summonInfo[0].isEmpty()) {
                entry.setSummonerEntityId(DatabaseManager.getOrCreateEntity(
                        summonInfo[0], summonInfo[1], summonInfo[2], summonInfo[3]));
            }

            // Victim — если victim пустой, используем source (self-targeting)
            String[] victimInfo = parseEntityInfo(getSafe(fields, 4), getSafe(fields, 5));

            // Если victim не указан явно — это self-targeting, копируем source
            if (victimInfo[0] == null || victimInfo[0].isEmpty() || victimInfo[0].equals("*")) {
                entry.setVictimEntityId(entry.getSourceEntityId());
            } else {
                entry.setVictimEntityId(DatabaseManager.getOrCreateEntity(
                        victimInfo[0], victimInfo[1], victimInfo[2], victimInfo[3]));
            }

            // Power
            String powerName = emptyToNull(getSafe(fields, 6));
            String powerIdStr = emptyToNull(getSafe(fields, 7));
            String attackType = emptyToNull(getSafe(fields, 8));

            entry.setPowerId(DatabaseManager.getOrCreatePower(powerIdStr, powerName, attackType));

            // Impact types
            String impactTypeStr = getSafe(fields, 9);
            entry.addImpactType(impactTypeStr);

            // Values
            entry.setActualImpact(parseDouble(getSafe(fields, 10)));
            entry.setPureImpact(parseDouble(getSafe(fields, 11)));

            return entry;

        } catch (Exception e) {
            System.err.println("Error parsing line: " + line + " - " + e.getMessage());
            return null;
        }
    }

    private static String normalizeEntityName(String name) {
        if (name == null || name.isEmpty() || name.equals("*")) {
            return null;
        }
        // Если имя выглядит как технический ID — это баг игры, игнорируем его
        if (name.startsWith("C[") || name.startsWith("P[")) {
            return null;
        }
        return name;
    }

    /**
     * Разбирает entity из name и idPart. Возвращает массив: [entityId,
     * entityName, entityHandle, entityType]
     */
    private static String[] parseEntityInfo(String name, String idPart) {
        String entityId;
        String entityName = normalizeEntityName(name);
        String entityHandle = null;
        String entityType;

        if (idPart.startsWith("P[")) {
            entityType = "Player";
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split("@", 2);
            entityId = parts[0];
            if (parts.length > 1) {
                String[] ownerParts = parts[1].split(" ", 2);
                entityHandle = ownerParts[0];
            }
        } else if (idPart.startsWith("C[")) {
            entityType = "Creature";
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split(" ", 2);
            entityId = parts[0];
        } else {
            entityType = "Creature";
            entityId = idPart;
        }

        return new String[]{entityId, entityName, entityHandle, entityType};
    }

    // Остальные методы остаются без изменений...
    private static LocalDateTime parseTimestamp(String ts) {
        try {
            String[] parts = ts.split("[:.]");
            if (parts.length >= 7) {
                int year = 2000 + Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                int hour = Integer.parseInt(parts[3]);
                int minute = Integer.parseInt(parts[4]);
                int second = Integer.parseInt(parts[5]);
                int tenth = Integer.parseInt(parts[6]);

                return LocalDateTime.of(year, month, day, hour, minute, second, tenth * 100_000_000);
            }
        } catch (NumberFormatException e) {
            // Игнорируем
        }
        throw new RuntimeException("Failed to parse timestamp: " + ts);
    }

    private static String getSafe(String[] fields, int index) {
        return (index < fields.length) ? fields[index] : null;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty() || s.equals("*")) ? null : s;
    }

    private static double parseDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static boolean isCombatLogFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("Combatlog_") && fileName.endsWith(".Log");
    }

    public static List<CombatLogEntry> parseFile(Path filePath, long startPos, long endPos) {
        List<CombatLogEntry> entries = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(filePath);

            int startLine = (int) (lines.size() * startPos / Math.max(Files.size(filePath), 1));
            int endLine = (int) (lines.size() * endPos / Math.max(Files.size(filePath), 1));

            startLine = Math.max(0, startLine - 10);
            endLine = Math.min(lines.size(), endLine + 10);

            for (int i = startLine; i < endLine && i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }

                CombatLogEntry entry = parseLine(line, filePath.getFileName().toString());
                if (entry != null) {
                    entries.add(entry);
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        }

        return entries;
    }

    public static List<CombatLogEntry> parseFileAfter(Path filePath, LocalDateTime afterTimestamp) {
        List<CombatLogEntry> entries = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(filePath);

            for (String line : lines) {
                try {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    // Быстрая проверка timestamp
                    String timestampStr = line.substring(0, line.indexOf("::"));
                    LocalDateTime lineTime = parseTimestamp(timestampStr);

                    if (lineTime.isAfter(afterTimestamp)) {
                        CombatLogEntry entry = parseLine(line, filePath.getFileName().toString());
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                } catch (Exception e) {
                    // Пропускаем строки с ошибками
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
        }

        return entries;
    }
}
