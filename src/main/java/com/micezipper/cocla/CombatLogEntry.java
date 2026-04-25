package com.micezipper.cocla;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CombatLogEntry {
    private LocalDateTime timestamp;
    private Long sourceEntityId;      // FK -> entities.id
    private Long summonerEntityId;    // FK -> entities.id (может быть null)
    private Long victimEntityId;      // FK -> entities.id
    private Long powerId;             // FK -> powers.id
    private List<String> impactTypes; // список типов воздействия
    private double actualImpact;
    private double pureImpact;
    private String rawLine;
    private String fileName;
    private Long dbId;                // ID после сохранения в БД

    public CombatLogEntry() {
        this.impactTypes = new ArrayList<>();
    }

    public void addImpactType(String type) {
        if (type != null && !type.isEmpty() && !type.equals("*")) {
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

    public Long getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(Long sourceEntityId) { this.sourceEntityId = sourceEntityId; }

    public Long getSummonerEntityId() { return summonerEntityId; }
    public void setSummonerEntityId(Long summonerEntityId) { this.summonerEntityId = summonerEntityId; }

    public Long getVictimEntityId() { return victimEntityId; }
    public void setVictimEntityId(Long victimEntityId) { this.victimEntityId = victimEntityId; }

    public Long getPowerId() { return powerId; }
    public void setPowerId(Long powerId) { this.powerId = powerId; }

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