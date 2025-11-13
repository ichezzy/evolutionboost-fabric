package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CommandLogConfig {
    public boolean enabled = true;
    public boolean includeConsole = true;
    public String format = "{ts} {source} {name}({uuid}) @{dim} ran: /{command}{args} -> {result}";
    public String timestampPattern = "yyyy-MM-dd HH:mm:ss.SSSX";
    public List<String> whitelist = new ArrayList<>(); // leer = alles
    public List<String> blacklist = Arrays.asList("login","register","changepassword");
    public boolean logArguments = true;
    public boolean logFailedExecutions = true;
    public boolean rollOnServerStart = true;

    public transient DateTimeFormatter dtf = DateTimeFormatter.ofPattern(timestampPattern);

    public static Path configDir() { return Path.of("config", "evolutionboost"); }
    public static Path logsDir()   { return configDir().resolve(Path.of("logs","commands")); }
    public static Path configPath(){ return configDir().resolve("command_log.json"); }

    public static CommandLogConfig loadOrCreate() {
        try {
            Files.createDirectories(configDir());
            if (Files.notExists(configPath())) {
                CommandLogConfig def = new CommandLogConfig();
                try (Writer w = Files.newBufferedWriter(configPath(), StandardCharsets.UTF_8)) {
                    gson().toJson(def, w);
                }
                return def;
            }
            try (Reader r = Files.newBufferedReader(configPath(), StandardCharsets.UTF_8)) {
                CommandLogConfig cfg = gson().fromJson(r, CommandLogConfig.class);
                cfg.dtf = DateTimeFormatter.ofPattern(cfg.timestampPattern);
                return cfg;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new CommandLogConfig();
        }
    }

    private static Gson gson() { return new GsonBuilder().setPrettyPrinting().create(); }
}
