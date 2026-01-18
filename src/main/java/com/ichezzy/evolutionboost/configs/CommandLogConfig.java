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
 * Konfiguration für das Command-Logging-System.
 * 
 * Pfad: /config/evolutionboost/command_log.json
 * Logs: /config/evolutionboost/logs/commands/(01) 2025-Jan/(001) Sat-18-01-2025.txt
 * 
 * ===================================================================================
 * BLACKLIST ERKLÄRUNG:
 * ===================================================================================
 * Die Blacklist prüft ob ein Command MIT DIESEM TEXT BEGINNT.
 * 
 * Beispiele:
 *   Blacklist: ["login"]
 *   → Blockt: "login", "login password123"
 *   → Blockt NICHT: "eb login", "relogin"
 * 
 *   Blacklist: ["eb rewards"]
 *   → Blockt: "eb rewards", "eb rewards claim", "eb rewards claim daily"
 *   → Blockt NICHT: "eb boost", "eb help"
 * 
 *   Blacklist: ["eb rewards claim daily"]
 *   → Blockt NUR: "eb rewards claim daily" (exakt)
 *   → Blockt NICHT: "eb rewards claim weekly", "eb rewards"
 * 
 * TIPP: Je spezifischer der Eintrag, desto weniger wird geblockt.
 *       Je kürzer der Eintrag, desto mehr wird geblockt.
 * ===================================================================================
 */
public final class CommandLogConfig {

    // ==================== Haupt-Schalter ====================
    
    /** Ob Command-Logging aktiviert ist */
    public boolean enabled = true;
    
    /** Ob Konsolen-Commands geloggt werden */
    public boolean logConsole = true;
    
    /** Ob Command-Block-Commands geloggt werden */
    public boolean logCommandBlocks = false;
    
    /** Ob fehlgeschlagene Commands geloggt werden */
    public boolean logFailed = true;

    // ==================== Filter ====================
    
    /**
     * Blacklist: Commands die mit diesen Texten BEGINNEN werden NICHT geloggt.
     * 
     * Beispiele:
     *   "login"              → blockt alle login-Commands
     *   "eb rewards claim"   → blockt alle "eb rewards claim ..." Commands
     *   "eb"                 → blockt ALLE eb-Commands (zu breit!)
     * 
     * Standard: Login-Commands für Auth-Plugins
     */
    public List<String> blacklist = new ArrayList<>(List.of(
            "login",
            "register", 
            "changepassword",
            "l",
            "log"
    ));

    // ==================== Format ====================
    
    /**
     * Log-Format. Verfügbare Platzhalter:
     *   {time}    - Zeitstempel (HH:mm:ss)
     *   {date}    - Datum (yyyy-MM-dd)
     *   {player}  - Spielername (oder "CONSOLE" / "CommandBlock")
     *   {uuid}    - Spieler-UUID (oder "-")
     *   {dim}     - Dimension (z.B. minecraft:overworld)
     *   {command} - Voller Command mit Argumenten
     *   {result}  - OK oder FAILED
     */
    public String format = "[{time}] {player}: /{command} -> {result}";

    // ==================== Datei-Management ====================
    
    /**
     * Maximale Anzahl Monatsordner behalten (0 = unbegrenzt).
     * Älteste Monate werden gelöscht wenn Limit erreicht.
     */
    public int maxMonths = 12;

    // ==================== Singleton & IO ====================

    private static volatile CommandLogConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private CommandLogConfig() {}

    public static CommandLogConfig get() {
        CommandLogConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized CommandLogConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("command_log.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, CommandLogConfig.class);
                    if (INSTANCE == null) {
                        INSTANCE = defaults();
                    }
                }
            } else {
                INSTANCE = defaults();
                save();
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Failed to load config: {}", e.getMessage());
            INSTANCE = defaults();
        }

        // Fallbacks
        if (INSTANCE.blacklist == null) INSTANCE.blacklist = new ArrayList<>();
        if (INSTANCE.format == null || INSTANCE.format.isBlank()) {
            INSTANCE.format = "[{time}] {player}: /{command} -> {result}";
        }

        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("command_log.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Failed to save config: {}", e.getMessage());
        }
    }

    public static synchronized void reload() {
        INSTANCE = null;
        loadOrCreate();
        EvolutionBoost.LOGGER.info("[cmdlog] Config reloaded");
    }

    private static CommandLogConfig defaults() {
        CommandLogConfig c = new CommandLogConfig();
        c.enabled = true;
        c.logConsole = true;
        c.logCommandBlocks = false;
        c.logFailed = true;
        c.blacklist = new ArrayList<>(List.of("login", "register", "changepassword", "l", "log"));
        c.format = "[{time}] {player}: /{command} -> {result}";
        c.maxMonths = 12;
        return c;
    }

    // ==================== Helper Methods ====================

    /**
     * Prüft ob ein Command geloggt werden soll.
     * 
     * @param command Der Command OHNE führenden Slash (z.B. "eb rewards claim daily")
     * @param isConsole true wenn von Konsole ausgeführt
     * @param isCommandBlock true wenn von Command Block ausgeführt
     * @param success true wenn erfolgreich ausgeführt
     */
    public boolean shouldLog(String command, boolean isConsole, boolean isCommandBlock, boolean success) {
        if (!enabled) return false;
        if (!logConsole && isConsole) return false;
        if (!logCommandBlocks && isCommandBlock) return false;
        if (!logFailed && !success) return false;

        // Command normalisieren (lowercase, ohne führenden Slash)
        String normalized = command.toLowerCase().trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // Blacklist prüfen: Command beginnt mit Blacklist-Eintrag?
        for (String blocked : blacklist) {
            String blockedNormalized = blocked.toLowerCase().trim();
            if (blockedNormalized.startsWith("/")) {
                blockedNormalized = blockedNormalized.substring(1);
            }
            
            // Prüfe ob Command mit dem Blacklist-Eintrag BEGINNT
            // "eb rewards claim daily" beginnt mit "eb rewards claim" → geblockt
            // "eb rewards claim daily" beginnt mit "eb rewards claim daily" → geblockt
            // "eb rewards claim daily" beginnt NICHT mit "eb rewards claim weekly" → nicht geblockt
            if (normalized.equals(blockedNormalized) || normalized.startsWith(blockedNormalized + " ")) {
                return false; // Geblockt!
            }
        }

        return true;
    }

    /**
     * Gibt den Basis-Pfad zum Logverzeichnis zurück.
     */
    public static Path getLogBaseDir() {
        return Path.of("config", "evolutionboost", "logs", "commands");
    }
}
