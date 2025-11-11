package com.ichezzy.evolutionboost.logging;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CommandLogManager {
    private static BufferedWriter writer;
    private static CommandLogConfig cfg;
    private static final Object LOCK = new Object();

    // ---- Lifecycle ----
    public static void init(CommandLogConfig config) {
        cfg = config;
        if (!cfg.enabled) return;
        try {
            Files.createDirectories(CommandLogConfig.logsDir());
            if (cfg.rollOnServerStart) openNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openNewFile() {
        synchronized (LOCK) {
            close();
            String base = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path file = CommandLogConfig.logsDir().resolve(base + ".log");
            try {
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    private static boolean enabledFor(String rawCommand, boolean success) {
        if (cfg == null || !cfg.enabled) return false;
        if (!cfg.logFailedExecutions && !success) return false;

        String base = rawCommand == null ? "" : (rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand);
        String head = base.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        if (!cfg.whitelist.isEmpty()
                && cfg.whitelist.stream().map(s -> s.toLowerCase(Locale.ROOT)).noneMatch(wh -> wh.equals(head))) {
            return false;
        }
        if (cfg.blacklist.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(bl -> bl.equals(head))) {
            return false;
        }
        return true;
    }

    // ---- Helper: aus Object sicher einen CommandSourceStack machen ----
    public static CommandSourceStack tryToStack(Object source) {
        if (source instanceof CommandSourceStack css) return css;
        return null;
    }
}
