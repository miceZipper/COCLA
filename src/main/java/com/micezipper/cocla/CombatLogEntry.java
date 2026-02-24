package com.micezipper.cocla;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CombatLogEntry {
    private LocalDateTime timestamp;
    private String sourceName;
    private String sourceId;
    private String sourceOwner;
    private String summonerName;
    private String summonerId;
    private String victimName;
    private String victimId;
    private String powerName;
    private String powerId;
    private String attackType;
    private List<String> impactTypes; // теперь список
    private double actualImpact;
    private double pureImpact;
    private String rawLine;
    private String fileName;
    private Long dbId; // ID после сохранения в БД

    public CombatLogEntry() {
        this.impactTypes = new ArrayList<>();
    }

    // Добавляем impact type
    public void addImpactType(String type) {
        if (type != null && !type.isEmpty() && !type.equals("*")) {
            // Разделяем по | если есть несколько
            String[] parts = type.split("\\|");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    this.impactTypes.add(trimmed);
                }
            }
        }
    }

    // Геттеры и сеттеры
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceOwner() { return sourceOwner; }
    public void setSourceOwner(String sourceOwner) { this.sourceOwner = sourceOwner; }

    public String getSummonName() { return summonerName; }
    public void setSummonName(String summonerName) { this.summonerName = summonerName; }

    public String getSummonId() { return summonerId; }
    public void setSummonId(String summonerId) { this.summonerId = summonerId; }

    public String getVictimName() { return victimName; }
    public void setVictimName(String victimName) { this.victimName = victimName; }

    public String getVictimId() { return victimId; }
    public void setVictimId(String victimId) { this.victimId = victimId; }

    public String getPowerName() { return powerName; }
    public void setPowerName(String powerName) { this.powerName = powerName; }

    public String getPowerId() { return powerId; }
    public void setPowerId(String powerId) { this.powerId = powerId; }

    public String getAttackType() { return attackType; }
    public void setAttackType(String attackType) { this.attackType = attackType; }

    public List<String> getImpactTypes() { return impactTypes; }
    public void setImpactTypes(List<String> impactTypes) { this.impactTypes = impactTypes; }

    public double getActualImpact() { return actualImpact; }
    public void setActualImpact(double actualImpact) { this.actualImpact = actualImpact; }

    public double getPureImpact() { return pureImpact; }
    public void setPureImpact(double pureImpact) { this.pureImpact = pureImpact; }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }
}