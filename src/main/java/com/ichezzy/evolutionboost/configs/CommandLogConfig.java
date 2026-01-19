package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-Logging Konfiguration.
 * 
 * WICHTIG: Config wird bei JEDEM Aufruf von get() frisch geladen!
 * Änderungen an der JSON-Datei werden sofort wirksam ohne Neustart.
 * 
 * Pfad: config/evolutionboost/command_log.json
 * Logs: config/evolutionboost/logs/commands/(01) 2025-Jan/(001) Sun-19-01-2025.txt
 * 
 * ===================================================================================
 * BLACKLIST - Commands die NICHT geloggt werden:
 * ===================================================================================
 * 
 * Die Blacklist prüft ob ein Command MIT DEM EINTRAG BEGINNT (case-insensitive).
 * 
 * Beispiel: blacklist = ["login", "eb rewards claim"]
 *   ✓ Blockt: "login", "login passwort123", "eb rewards claim daily"
 *   ✗ Blockt NICHT: "relogin", "eb rewards"
 * 
 * ===================================================================================
 * FILTER - Commands die SEPARAT geloggt werden:
 * ===================================================================================
 * 
 * Commands im Filter werden zusätzlich in einer separaten Datei gespeichert.
 * Gut für Staff-Überwachung (z.B. /givepokemon, /op, /gamemode).
 * 
 * Beispiel: filter = ["givepokemon", "pokegive", "op", "deop"]
 * 
 * Gefilterte Commands landen in: "(001) Sun-19-01-2025 Filtered.txt"
 * ===================================================================================
 */
public final class CommandLogConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Path.of("config", "evolutionboost", "command_log.json");

    // ==================== Config-Felder ====================
    
    /** Master-Schalter: Logging komplett ein/aus */
    public boolean enabled = true;
    
    /** Konsolen-Commands loggen? */
    public boolean logConsole = true;
    
    /** Command-Block-Commands loggen? */
    public boolean logCommandBlocks = false;
    
    /** 
     * Blacklist: Commands die mit diesen Texten BEGINNEN werden NICHT geloggt.
     * Standard: leer (alle Commands werden geloggt)
     */
    public List<String> blacklist = new ArrayList<>();
    
    /**
     * Filter: Commands die mit diesen Texten BEGINNEN werden ZUSÄTZLICH
     * in einer separaten Datei gespeichert (für Staff-Überwachung).
     */
    public List<String> filter = new ArrayList<>();

    /** Log-Format mit Platzhaltern: {time}, {player}, {command}, {result} */
    public String format = "[{time}] {player}: /{command} -> {result}";

    /** Maximale Anzahl Monatsordner behalten (0 = unbegrenzt) */
    public int maxMonths = 12;

    // ==================== Laden ====================

    public static CommandLogConfig get() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            
            if (Files.exists(CONFIG_FILE)) {
                try (BufferedReader br = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                    CommandLogConfig cfg = GSON.fromJson(br, CommandLogConfig.class);
                    if (cfg != null) {
                        if (cfg.blacklist == null) cfg.blacklist = new ArrayList<>();
                        if (cfg.filter == null) cfg.filter = new ArrayList<>();
                        if (cfg.format == null || cfg.format.isBlank()) {
                            cfg.format = "[{time}] {player}: /{command} -> {result}";
                        }
                        return cfg;
                    }
                }
            }
            
            CommandLogConfig cfg = createDefault();
            save(cfg);
            return cfg;
            
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Config-Fehler: {}", e.getMessage());
            return createDefault();
        }
    }

    public static void save(CommandLogConfig cfg) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Speichern fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static CommandLogConfig createDefault() {
        CommandLogConfig cfg = new CommandLogConfig();
        cfg.enabled = true;
        cfg.logConsole = true;
        cfg.logCommandBlocks = false;
        cfg.blacklist = new ArrayList<>(); // Leer - keine Standard-Einträge
        cfg.filter = new ArrayList<>();    // Leer - User füllt selbst
        cfg.format = "[{time}] {player}: /{command} -> {result}";
        cfg.maxMonths = 12;
        return cfg;
    }

    // ==================== Prüf-Methoden ====================

    /**
     * Prüft ob ein Command geloggt werden soll (nicht auf Blacklist).
     */
    public boolean shouldLog(String command) {
        if (!enabled) return false;
        if (command == null || command.isBlank()) return false;

        String normalized = command.toLowerCase().trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);

        for (String blocked : blacklist) {
            if (blocked == null || blocked.isBlank()) continue;
            
            String blockedNorm = blocked.toLowerCase().trim();
            if (blockedNorm.startsWith("/")) blockedNorm = blockedNorm.substring(1);
            
            if (normalized.equals(blockedNorm) || normalized.startsWith(blockedNorm + " ")) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Prüft ob ein Command im Filter ist (separat geloggt werden soll).
     */
    public boolean isFiltered(String command) {
        if (filter == null || filter.isEmpty()) return false;
        if (command == null || command.isBlank()) return false;

        String normalized = command.toLowerCase().trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);

        for (String filtered : filter) {
            if (filtered == null || filtered.isBlank()) continue;
            
            String filteredNorm = filtered.toLowerCase().trim();
            if (filteredNorm.startsWith("/")) filteredNorm = filteredNorm.substring(1);
            
            if (normalized.equals(filteredNorm) || normalized.startsWith(filteredNorm + " ")) {
                return true;
            }
        }
        return false;
    }

    public static Path getLogBaseDir() {
        return Path.of("config", "evolutionboost", "logs", "commands");
    }
}
