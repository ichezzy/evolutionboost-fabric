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
 * Command-Logging System.
 * 
 * Ordnerstruktur:
 *   config/evolutionboost/logs/commands/
 *   ├── (01) 2025-Jan/
 *   │   ├── (001) Sun-19-01-2025.txt          <- Alle Commands
 *   │   ├── (001) Sun-19-01-2025 Filtered.txt <- Nur gefilterte Commands
 *   │   └── (002) Mon-20-01-2025.txt
 *   └── (02) 2025-Feb/
 */
public final class CommandLogManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Object LOCK = new Object();
    
    // Normale Log-Datei
    private static BufferedWriter writer;
    private static LocalDate currentDate;
    
    // Gefilterte Log-Datei
    private static BufferedWriter filteredWriter;
    private static LocalDate filteredDate;
    
    private static int monthCounter = 0;

    private CommandLogManager() {}

    public static void init() {
        CommandLogConfig cfg = CommandLogConfig.get();
        if (!cfg.enabled) {
            EvolutionBoost.LOGGER.info("[cmdlog] Command-Logging deaktiviert");
            return;
        }

        try {
            Files.createDirectories(CommandLogConfig.getLogBaseDir());
            findHighestMonthCounter();
            openLogFile();
            cleanupOldMonths();
            EvolutionBoost.LOGGER.info("[cmdlog] Command-Logging initialisiert");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Init fehlgeschlagen: {}", e.getMessage());
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            closeWriter();
            closeFilteredWriter();
        }
    }

    /**
     * Loggt einen Command.
     */
    public static void log(CommandSourceStack source, String command, int result) {
        CommandLogConfig cfg = CommandLogConfig.get();
        if (!cfg.enabled) return;

        String playerName;
        boolean isConsole = false;
        boolean isCommandBlock = false;

        if (source == null) {
            playerName = "UNKNOWN";
        } else if (source.getEntity() instanceof ServerPlayer player) {
            playerName = player.getGameProfile().getName();
        } else {
            String textName = source.getTextName();
            if ("Server".equals(textName)) {
                isConsole = true;
                playerName = "CONSOLE";
            } else {
                isCommandBlock = true;
                playerName = "CommandBlock";
            }
        }

        // Config-Filter prüfen
        if (isConsole && !cfg.logConsole) return;
        if (isCommandBlock && !cfg.logCommandBlocks) return;
        
        // Blacklist prüfen
        if (!cfg.shouldLog(command)) return;

        String resultStr = result >= 1 ? "OK" : (result == 0 ? "FAIL" : "ERR");

        String line = cfg.format
                .replace("{time}", LocalTime.now().format(TIME_FMT))
                .replace("{player}", playerName)
                .replace("{command}", command)
                .replace("{result}", resultStr);

        // Normal loggen
        writeLine(line);
        
        // Zusätzlich in Filter-Datei wenn Command im Filter ist
        if (cfg.isFiltered(command)) {
            writeFilteredLine(line);
        }
    }

    // ==================== Normale Log-Datei ====================

    private static void writeLine(String line) {
        synchronized (LOCK) {
            try {
                LocalDate today = LocalDate.now();
                if (writer == null || !today.equals(currentDate)) {
                    openLogFile();
                }
                if (writer != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                EvolutionBoost.LOGGER.warn("[cmdlog] Schreibfehler: {}", e.getMessage());
            }
        }
    }

    private static void openLogFile() {
        closeWriter();
        LocalDate today = LocalDate.now();
        currentDate = today;
        
        Path monthDir = getOrCreateMonthDir(today);
        Path logFile = getDayFile(monthDir, today, false);

        try {
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Datei öffnen fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static void closeWriter() {
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
            writer = null;
        }
    }

    // ==================== Gefilterte Log-Datei ====================

    private static void writeFilteredLine(String line) {
        synchronized (LOCK) {
            try {
                LocalDate today = LocalDate.now();
                if (filteredWriter == null || !today.equals(filteredDate)) {
                    openFilteredLogFile();
                }
                if (filteredWriter != null) {
                    filteredWriter.write(line);
                    filteredWriter.newLine();
                    filteredWriter.flush();
                }
            } catch (IOException e) {
                EvolutionBoost.LOGGER.warn("[cmdlog] Filter-Schreibfehler: {}", e.getMessage());
            }
        }
    }

    private static void openFilteredLogFile() {
        closeFilteredWriter();
        LocalDate today = LocalDate.now();
        filteredDate = today;
        
        Path monthDir = getOrCreateMonthDir(today);
        Path logFile = getDayFile(monthDir, today, true);

        try {
            filteredWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Filter-Datei öffnen fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static void closeFilteredWriter() {
        if (filteredWriter != null) {
            try { filteredWriter.close(); } catch (IOException ignored) {}
            filteredWriter = null;
        }
    }

    // ==================== Datei-Management ====================

    private static void findHighestMonthCounter() {
        Path baseDir = CommandLogConfig.getLogBaseDir();
        try {
            if (!Files.exists(baseDir)) return;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        String name = entry.getFileName().toString();
                        if (name.startsWith("(") && name.contains(")")) {
                            try {
                                int num = Integer.parseInt(name.substring(1, name.indexOf(")")));
                                if (num > monthCounter) monthCounter = num;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private static Path getOrCreateMonthDir(LocalDate date) {
        Path baseDir = CommandLogConfig.getLogBaseDir();
        String yearMonth = date.getYear() + "-" + date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && entry.getFileName().toString().endsWith(yearMonth)) {
                    return entry;
                }
            }
        } catch (IOException ignored) {}
        
        monthCounter++;
        String dirName = String.format("(%02d) %s", monthCounter, yearMonth);
        Path monthDir = baseDir.resolve(dirName);
        
        try {
            Files.createDirectories(monthDir);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[cmdlog] Ordner erstellen fehlgeschlagen: {}", e.getMessage());
        }
        
        return monthDir;
    }

    /**
     * Findet oder erstellt die Tagesdatei.
     * @param filtered true für "Filtered" Suffix
     */
    private static Path getDayFile(Path monthDir, LocalDate date, boolean filtered) {
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String dateStr = String.format("%02d-%02d-%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String baseName = dayOfWeek + "-" + dateStr;
        String suffix = filtered ? " Filtered" : "";
        
        // Existierende Datei für heute suchen
        String searchPattern = "*" + baseName + suffix + ".txt";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, searchPattern)) {
            for (Path entry : stream) return entry;
        } catch (IOException ignored) {}
        
        // Für gefilterte Dateien: Gleiche Nummer wie normale Datei verwenden
        String fileNumber;
        if (filtered) {
            // Suche die normale Datei für heute und extrahiere deren Nummer
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*" + baseName + ".txt")) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    if (!name.contains("Filtered") && name.startsWith("(")) {
                        fileNumber = name.substring(0, name.indexOf(")") + 1);
                        return monthDir.resolve(fileNumber + " " + baseName + " Filtered.txt");
                    }
                }
            } catch (IOException ignored) {}
        }
        
        // Neue Datei mit Nummer erstellen (nur für normale Dateien zählen)
        int fileCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(monthDir, "*.txt")) {
            for (Path entry : stream) {
                // Nur normale Dateien zählen, nicht Filtered
                if (!entry.getFileName().toString().contains("Filtered")) {
                    fileCount++;
                }
            }
        } catch (IOException ignored) {}
        
        fileCount++;
        String fileName = String.format("(%03d) %s%s.txt", fileCount, baseName, suffix);
        return monthDir.resolve(fileName);
    }

    private static void cleanupOldMonths() {
        CommandLogConfig cfg = CommandLogConfig.get();
        if (cfg.maxMonths <= 0) return;

        Path baseDir = CommandLogConfig.getLogBaseDir();
        if (!Files.exists(baseDir)) return;

        try (Stream<Path> dirs = Files.list(baseDir)) {
            var monthDirs = dirs
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("("))
                    .sorted(Comparator.comparingInt(p -> {
                        String name = p.getFileName().toString();
                        try { return Integer.parseInt(name.substring(1, name.indexOf(")"))); }
                        catch (Exception e) { return Integer.MAX_VALUE; }
                    }))
                    .toList();

            int toDelete = monthDirs.size() - cfg.maxMonths;
            for (int i = 0; i < toDelete && i < monthDirs.size(); i++) {
                Path dirToDelete = monthDirs.get(i);
                try {
                    try (Stream<Path> files = Files.list(dirToDelete)) {
                        files.forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
                    }
                    Files.delete(dirToDelete);
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }
}
