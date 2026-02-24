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

    private static DatabaseManager dbManager; // Добавляем статическое поле

    // Паттерн для разбора строки лога
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(.+?)::(.+)$"
    );

    // Метод для установки DatabaseManager
    public static void setDatabaseManager(DatabaseManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        dbManager = manager;
    }

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

            // Парсим timestamp
            String timestampStr = m.group(1);
            entry.setTimestamp(parseTimestamp(timestampStr));

            // Разбираем остальные поля
            String rest = m.group(2);
            String[] fields = rest.split(",", -1);

            if (fields.length < 11) {
                System.err.println("Not enough fields: " + fields.length + " in line: " + line);
                return null;
            }

            // Source
            parseSource(entry, getSafe(fields, 0), getSafe(fields, 1));

            // Summoner
            parseSummon(entry, getSafe(fields, 2), getSafe(fields, 3));

            // Victim
            parseVictim(entry, getSafe(fields, 4), getSafe(fields, 5));

            // Power
            entry.setPowerName(emptyToNull(getSafe(fields, 6)));
            entry.setPowerId(emptyToNull(getSafe(fields, 7)));

            // Attack type
            entry.setAttackType(emptyToNull(getSafe(fields, 8)));

            // Impact type (может содержать |)
            String impactTypeStr = getSafe(fields, 9);
            entry.addImpactType(impactTypeStr);

            // Impact значения
            entry.setActualImpact(parseDouble(getSafe(fields, 10)));
            entry.setPureImpact(parseDouble(getSafe(fields, 11)));

            return entry;

        } catch (Exception e) {
            System.err.println("Error parsing line: " + line + " - " + e.getMessage());
            return null;
        }
    }

    private static void saveEntityType(String entityId, String entityName, String entityType) {
        dbManager.saveEntityType(entityId, entityName, entityType);
    }

    private static void parseSource(CombatLogEntry entry, String name, String idPart) {
        entry.setSourceName(emptyToNull(name));

        if (idPart == null || idPart.isEmpty() || idPart.equals("*")) {
            return;
        }

        if (idPart.startsWith("P[")) {
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split("@", 2);
            entry.setSourceId(parts[0]);

            // СОХРАНЯЕМ ИГРОКА
            if (entry.getSourceName() != null && entry.getSourceId() != null) {
                saveEntityType(entry.getSourceId(), entry.getSourceName(), "Player");
            }

            if (parts.length > 1) {
                String[] ownerParts = parts[1].split(" ", 2);
                if (ownerParts.length > 1) {
                    entry.setSourceOwner(ownerParts[1]);
                } else {
                    entry.setSourceOwner(ownerParts[0]);
                }
            }
        } else if (idPart.startsWith("C[")) {
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split(" ", 2);
            entry.setSourceId(parts[0]);
            entry.setSourceOwner(parts.length > 1 ? parts[1] : null);

            // СОХРАНЯЕМ МОБА/ОБЪЕКТ
            if (entry.getSourceName() != null && entry.getSourceId() != null) {
                saveEntityType(entry.getSourceId(), entry.getSourceName(), "Creature");
            }
        }
    }

    private static void parseSummon(CombatLogEntry entry, String name, String idPart) {
        entry.setSummonName(emptyToNull(name));

        if (idPart == null || idPart.isEmpty() || idPart.equals("*")) {
            return;
        }

        if (idPart.startsWith("P[")) {
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split("@", 2);
            entry.setSummonId(parts[0]);

            // СОХРАНЯЕМ ПРИЗЫВАТЕЛЯ
            if (entry.getSummonName() != null && entry.getSummonId() != null) {
                saveEntityType(entry.getSummonId(), entry.getSummonName(), "Player");
            }

        } else {
            entry.setSummonId(idPart);
        }
    }

    private static void parseVictim(CombatLogEntry entry, String name, String idPart) {
        entry.setVictimName((name == null || name.isEmpty() || name.equals("*")) ? "SELF" : name);

        if (idPart == null || idPart.isEmpty() || idPart.equals("*")) {
            entry.setVictimId("SELF");
            return;
        }

        if (idPart.startsWith("P[")) {
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split("@", 2);
            entry.setVictimId(parts[0]);

            // СОХРАНЯЕМ ИГРОКА (жертву)
            if (entry.getVictimName() != null && !entry.getVictimName().equals("SELF") && entry.getVictimId() != null) {
                saveEntityType(entry.getVictimId(), entry.getVictimName(), "Player");
            }

        } else if (idPart.startsWith("C[")) {
            String inner = idPart.substring(2, idPart.length() - 1);
            String[] parts = inner.split(" ", 2);
            entry.setVictimId(parts[0]);

            // СОХРАНЯЕМ МОБА/ОБЪЕКТ (жертву)
            if (entry.getVictimName() != null && !entry.getVictimName().equals("SELF") && entry.getVictimId() != null) {
                saveEntityType(entry.getVictimId(), entry.getVictimName(), "Creature");
            }

        } else {
            entry.setVictimId(idPart);
        }
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
