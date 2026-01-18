package com.ichezzy.evolutionboost.logging;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.CommandLogConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Verwaltet das Command-Logging-System.
 * 
 * Ordnerstruktur:
 *   config/evolutionboost/logs/commands/
 *   ├── (01) 2025-Jan/
 *   │   ├── (001) Sat-18-01-2025.txt
 *   │   ├── (002) Sun-19-01-2025.txt
 *   │   └── ...
 *   ├── (02) 2025-Feb/
 *   │   └── ...
 *   └── ...
 */
public final class CommandLogManager {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private static final Object LOCK = new Object();
    private static BufferedWriter currentWriter;
    private static LocalDate currentDate;
    private static int monthCounter = 0;

    private CommandLogManager() {}

    // ==================== Lifecycle ====================

    /**
     * Initialisiert das Logging-System beim Serverstart.
     */
    public static void init() {
        CommandLogConfig cfg = CommandLogConfig.get();
        if (!cfg.enabled) {
            EvolutionBoost.LOGGER.info("[cmdlog] Command logging is disabled");
            return;
        }

        try {
            Files.createDirectories(CommandLogConfig.getLogBaseDir());
            initMonthCounter();
            openLogFile();
            cleanupOldMonths();
            EvolutionBoost.LOGGER.info("[cmdlog] Command logging initialized");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Failed to initialize: {}", e.getMessage());
        }
    }

    /**
     * Schließt das Logging-System beim Serverstop.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            closeWriter();
        }
    }

    // ==================== Main Logging ====================

    /**
     * Loggt einen ausgeführten Command.
     * Wird vom Mixin aufgerufen.
     */
    public static void log(CommandSourceStack source, String rawCommand, int result, boolean success) {
        CommandLogConfig cfg = CommandLogConfig.get();
        
        // Quelle bestimmen
        boolean isConsole = false;
        boolean isCommandBlock = false;
        String playerName = "-";
        String uuid = "-";
        String dim = "unknown";

        if (source != null) {
            if (source.getEntity() instanceof ServerPlayer player) {
                playerName = player.getGameProfile().getName();
                uuid = player.getStringUUID();
                dim = player.level().dimension().location().toString();
            } else {
                String textName = source.getTextName();
                if ("Server".equals(textName)) {
                    isConsole = true;
                    playerName = "CONSOLE";
                } else {
                    isCommandBlock = true;
                    playerName = "CommandBlock";
                }
                if (source.getLevel() != null) {
                    dim = source.getLevel().dimension().location().toString();
                }
            }
        }

        // Command normalisieren (ohne führenden Slash)
        String command = rawCommand != null ? rawCommand : "";
        if (command.startsWith("/")) command = command.substring(1);
        
        // Filter prüfen (mit der KORRIGIERTEN Blacklist-Logik)
        if (!cfg.shouldLog(command, isConsole, isCommandBlock, success)) {
            return;
        }

        // Format erstellen
        String resultStr = success ? "OK" : "FAILED";
        
        String line = cfg.format
                .replace("{time}", LocalTime.now().format(TIME_FORMAT))
                .replace("{date}", LocalDate.now().format(DATE_FORMAT))
                .replace("{player}", playerName)
                .replace("{uuid}", uuid)
                .replace("{dim}", dim)
                .replace("{command}", command)
                .replace("{result}", resultStr);

        // Schreiben
        writeLine(line);
    }

    // ==================== File Management ====================

    /**
     * Zählt existierende Monatsordner und setzt den Counter entsprechend.
     */
    private static void initMonthCounter() throws IOException {
        Path baseDir = CommandLogConfig.getLogBaseDir();
        Files.createDirectories(baseDir);
        
        // Finde den höchsten existierenden Counter
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    // Format: "(01) 2025-Jan"
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

    private static void writeLine(String line) {
        synchronized (LOCK) {
            try {
                // Prüfen ob neuer Tag → neue Datei
                LocalDate today = LocalDate.now();
                if (!today.equals(currentDate)) {
                    openLogFile();
                }

                if (currentWriter != null) {
                    currentWriter.write(line);
                    currentWriter.newLine();
                    currentWriter.flush();
                }
            } catch (IOException e) {
                EvolutionBoost.LOGGER.warn("[cmdlog] Failed to write: {}", e.getMessage());
            }
        }
    }

    /**
     * Öffnet eine neue Logdatei.
     * Struktur: (01) 2025-Jan / (001) Sat-18-01-2025.txt
     */
    private static void openLogFile() {
        closeWriter();
        
        LocalDate today = LocalDate.now();
        currentDate = today;
        
        // Monatsordner erstellen/finden
        Path monthDir = getOrCreateMonthDir(today);
        
        // Datei im Monatsordner erstellen
        Path logFile = createDayFile(monthDir, today);

        try {
            currentWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            EvolutionBoost.LOGGER.debug("[cmdlog] Opened log file: {}", logFile);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Failed to open log file: {}", e.getMessage());
        }
    }

    /**
     * Erstellt oder findet den Monatsordner für das gegebene Datum.
     * Format: "(01) 2025-Jan"
     */
    private static Path getOrCreateMonthDir(LocalDate date) {
        Path baseDir = CommandLogConfig.getLogBaseDir();
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
            EvolutionBoost.LOGGER.warn("[cmdlog] Error checking month dirs: {}", e.getMessage());
        }
        
        // Neuen Monatsordner erstellen
        monthCounter++;
        String dirName = String.format("(%02d) %s", monthCounter, yearMonth);
        Path monthDir = baseDir.resolve(dirName);
        
        try {
            Files.createDirectories(monthDir);
            EvolutionBoost.LOGGER.info("[cmdlog] Created new month folder: {}", dirName);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Failed to create month dir: {}", e.getMessage());
        }
        
        return monthDir;
    }

    /**
     * Erstellt die Tagesdatei im Monatsordner.
     * Format: "(001) Sat-18-01-2025.txt"
     */
    private static Path createDayFile(Path monthDir, LocalDate date) {
        // Wochentag abkürzen
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        // Datum formatieren: DD-MM-YYYY
        String dateStr = String.format("%02d-%02d-%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String baseName = dayOfWeek + "-" + dateStr;
        
        // Prüfe ob eine Datei für heute bereits existiert
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*" + baseName + ".txt")) {
            for (Path entry : stream) {
                // Datei für heute existiert - anhängen
                return entry;
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Error checking day files: {}", e.getMessage());
        }
        
        // Zähle existierende Dateien für Nummerierung
        int fileCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*.txt")) {
            for (Path entry : stream) {
                fileCount++;
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Error counting files: {}", e.getMessage());
        }
        
        // Neue Datei erstellen mit Nummer
        fileCount++;
        String fileName = String.format("(%03d) %s.txt", fileCount, baseName);
        return monthDir.resolve(fileName);
    }

    private static void closeWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException ignored) {}
            currentWriter = null;
        }
    }

    /**
     * Löscht alte Monatsordner wenn maxMonths überschritten.
     */
    private static void cleanupOldMonths() {
        CommandLogConfig cfg = CommandLogConfig.get();
        if (cfg.maxMonths <= 0) return;

        Path baseDir = CommandLogConfig.getLogBaseDir();
        if (!Files.exists(baseDir)) return;

        try (Stream<Path> dirs = Files.list(baseDir)) {
            var monthDirs = dirs
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("("))
                    .sorted(Comparator.comparing(p -> {
                        // Sortiere nach Nummer im Ordnernamen
                        String name = p.getFileName().toString();
                        try {
                            return Integer.parseInt(name.substring(1, name.indexOf(")")));
                        } catch (Exception e) {
                            return 0;
                        }
                    }))
                    .toList();

            int toDelete = monthDirs.size() - cfg.maxMonths;
            if (toDelete > 0) {
                for (int i = 0; i < toDelete; i++) {
                    Path dirToDelete = monthDirs.get(i);
                    try {
                        // Lösche alle Dateien im Ordner
                        try (Stream<Path> files = Files.list(dirToDelete)) {
                            files.forEach(f -> {
                                try {
                                    Files.delete(f);
                                } catch (IOException ignored) {}
                            });
                        }
                        // Lösche den Ordner selbst
                        Files.delete(dirToDelete);
                        EvolutionBoost.LOGGER.info("[cmdlog] Deleted old month folder: {}", dirToDelete.getFileName());
                    } catch (IOException e) {
                        EvolutionBoost.LOGGER.warn("[cmdlog] Failed to delete: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[cmdlog] Failed to cleanup: {}", e.getMessage());
        }
    }

    // ==================== Mixin Helper ====================

    /**
     * Helper für Mixin - konvertiert Object zu CommandSourceStack.
     */
    public static CommandSourceStack toStack(Object source) {
        return source instanceof CommandSourceStack css ? css : null;
    }
}
