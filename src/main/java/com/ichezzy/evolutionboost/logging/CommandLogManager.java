package com.ichezzy.evolutionboost.logging;

import com.ichezzy.evolutionboost.configs.CommandLogConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

public class CommandLogManager {
    private static BufferedWriter writer;
    private static CommandLogConfig cfg;
    private static final Object LOCK = new Object();
    private static int monthCounter = 0;  // Für die (01), (02) Nummerierung

    // ---- Lifecycle ----
    public static void init(CommandLogConfig config) {
        cfg = config;
        if (!cfg.enabled) return;
        try {
            // Monats-Counter initialisieren basierend auf existierenden Ordnern
            initMonthCounter();
            if (cfg.rollOnServerStart) openNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Zählt existierende Monatsordner und setzt den Counter entsprechend.
     */
    private static void initMonthCounter() throws IOException {
        Path baseDir = CommandLogConfig.logsDir();
        Files.createDirectories(baseDir);
        
        // Finde den höchsten existierenden Counter
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    // Format: "(01) 2025-Dec"
                    if (name.startsWith("(") && name.contains(")")) {
                        try {
                            int num = Integer.parseInt(name.substring(1, name.indexOf(")")));
                            if (num > monthCounter) {
                                monthCounter = num;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    /**
     * Erstellt eine neue Logdatei mit verbesserter Ordnerstruktur.
     * Format: (01) 2025-Dec / (001) Sat-19-12-2025.txt
     */
    public static void openNewFile() {
        synchronized (LOCK) {
            close();
            
            LocalDate now = LocalDate.now();
            
            // Monatsordner erstellen/finden
            Path monthDir = getOrCreateMonthDir(now);
            
            // Datei im Monatsordner erstellen
            Path file = createDayFile(monthDir, now);
            
            try {
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, 
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Erstellt oder findet den Monatsordner für das gegebene Datum.
     * Format: "(01) 2025-Dec"
     */
    private static Path getOrCreateMonthDir(LocalDate date) {
        Path baseDir = CommandLogConfig.logsDir();
        String yearMonth = date.getYear() + "-" + date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        
        // Prüfe ob ein Ordner für diesen Monat bereits existiert
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    if (name.endsWith(yearMonth)) {
                        return entry; // Ordner existiert bereits
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Neuen Monatsordner erstellen
        monthCounter++;
        String dirName = String.format("(%02d) %s", monthCounter, yearMonth);
        Path monthDir = baseDir.resolve(dirName);
        
        try {
            Files.createDirectories(monthDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return monthDir;
    }

    /**
     * Erstellt die Tagesdatei im Monatsordner.
     * Format: "(001) Sat-19-12-2025.txt"
     */
    private static Path createDayFile(Path monthDir, LocalDate date) {
        // Wochentag abkürzen
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        // Datum formatieren: DD-MM-YYYY
        String dateStr = String.format("%02d-%02d-%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String baseName = dayOfWeek + "-" + dateStr;
        
        // Zähle existierende Dateien für Nummerierung
        int fileCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*.txt")) {
            for (Path entry : stream) {
                fileCount++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Prüfe ob eine Datei für heute bereits existiert
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*" + baseName + "*.txt")) {
            for (Path entry : stream) {
                // Datei für heute existiert - anhängen statt neu erstellen
                return entry;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Neue Datei erstellen mit Nummer
        fileCount++;
        String fileName = String.format("(%03d) %s.txt", fileCount, baseName);
        return monthDir.resolve(fileName);
    }

    public static void close() {
        synchronized (LOCK) {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
                writer = null;
            }
        }
    }

    // ---- Hooks vom Mixin ----
    public static void logBefore(CommandSourceStack source, String rawCommand) {
        // optionales Pre-Log; aktuell nicht genutzt
    }

    public static void logAfter(CommandSourceStack source, String rawCommand, int result, boolean success) {
        if (!enabledFor(rawCommand, success)) return;

        String ts = OffsetDateTime.now().format(cfg.dtf);

        // --- Quelle bestimmen + Felder setzen ---
        String src; String name = "-"; String uuid = "-"; String dim = "-";

        if (source != null && source.getEntity() instanceof ServerPlayer p) {
            src  = "PLAYER";
            name = p.getGameProfile().getName();
            uuid = p.getStringUUID();
            dim  = p.serverLevel().dimension().location().toString();
        } else if (source != null) {
            // Konsole vs. Command-Block über getTextName()
            String textName = source.getTextName(); // meist "Server" für Konsole
            if ("Server".equals(textName)) {
                src = "CONSOLE";
                name = "Server";
                if (!cfg.includeConsole) return;
            } else {
                src = "CMD_BLOCK";
                name = (textName != null && !textName.isEmpty()) ? textName : "CommandBlock";
            }
            if (source.getLevel() != null) {
                dim = source.getLevel().dimension().location().toString();
            }
        } else {
            src = "UNKNOWN";
        }

        // --- Command + Args extrahieren ---
        String base = rawCommand == null ? "" : (rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand);
        String[] split = base.split("\\s+", 2);
        String cmd = split.length > 0 ? split[0] : base;
        String args = (split.length > 1 && cfg.logArguments) ? " " + split[1] : "";

        // --- Zeile rendern ---
        String line = cfg.format
                .replace("{ts}", ts)
                .replace("{source}", src)
                .replace("{name}", name)
                .replace("{uuid}", uuid)
                .replace("{dim}", dim)
                .replace("{command}", cmd)
                .replace("{args}", args)
                .replace("{result}", success ? "OK(" + result + ")" : "ERR(" + result + ")");

        // --- Schreiben ---
        synchronized (LOCK) {
            try {
                if (writer == null) openNewFile();
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Prüft ob ein Command geloggt werden soll.
     * 
     * Blacklist-Logik:
     * - Prüft den ERSTEN Teil des Commands (vor dem ersten Leerzeichen)
     * - Beispiel: "/login password123" -> prüft "login"
     * - Beispiel: "/eb boost add" -> prüft "eb"
     * - Case-insensitive
     * 
     * Whitelist-Logik:
     * - Wenn leer: alle Commands erlaubt
     * - Wenn nicht leer: NUR Commands in der Liste erlaubt
     * 
     * Blacklist hat Priorität über Whitelist.
     */
    private static boolean enabledFor(String rawCommand, boolean success) {
        if (cfg == null || !cfg.enabled) return false;
        if (!cfg.logFailedExecutions && !success) return false;

        // Command-Head extrahieren (erster Teil vor Leerzeichen)
        String base = rawCommand == null ? "" : (rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand);
        String head = base.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        // Blacklist prüfen (hat Priorität)
        for (String bl : cfg.blacklist) {
            if (bl.toLowerCase(Locale.ROOT).equals(head)) {
                return false; // Command ist blacklisted
            }
        }

        // Whitelist prüfen (nur wenn nicht leer)
        if (!cfg.whitelist.isEmpty()) {
            boolean found = false;
            for (String wh : cfg.whitelist) {
                if (wh.toLowerCase(Locale.ROOT).equals(head)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false; // Command nicht in Whitelist
            }
        }

        return true;
    }

    // ---- Helper: aus Object sicher einen CommandSourceStack machen ----
    public static CommandSourceStack tryToStack(Object source) {
        if (source instanceof CommandSourceStack css) return css;
        return null;
    }
}
