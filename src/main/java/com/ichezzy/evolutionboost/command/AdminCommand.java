package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.permission.PermissionRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-Befehle für Server-Management.
 * 
 * /eb admin tpspawn online       - Teleportiert alle Online-Spieler zum Spawn
 * /eb admin tpspawn offline      - Setzt Offline-Spieler Position zum Spawn
 * /eb admin tpspawn all          - Beides
 * /eb admin tpspawn <player>     - Teleportiert einen Spieler zum Spawn
 * /eb admin info                 - Zeigt Server-Info
 * /eb admin gc                   - Führt Garbage Collection aus
 * /eb admin permissions          - Zeigt alle verfügbaren Permissions
 */
public final class AdminCommand {
    private AdminCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TPSPAWN_TYPE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("online", "offline", "all"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var adminTree = Commands.literal("admin")
                .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.admin", 3, false))

                // /eb admin tpspawn <type>
                .then(Commands.literal("tpspawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TPSPAWN_TYPE_SUGGEST)
                                .executes(ctx -> tpSpawn(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type"))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> tpSpawnPlayer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"))))))

                // /eb admin info
                .then(Commands.literal("info")
                        .executes(ctx -> showInfo(ctx.getSource())))

                // /eb admin gc
                .then(Commands.literal("gc")
                        .executes(ctx -> runGC(ctx.getSource())))

                // /eb admin cache clear
                .then(Commands.literal("cache")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearCaches(ctx.getSource()))))

                // /eb admin permissions
                .then(Commands.literal("permissions")
                        .executes(ctx -> showPermissions(ctx.getSource())))

                .executes(ctx -> showHelp(ctx.getSource()));

        dispatcher.register(Commands.literal("evolutionboost").then(adminTree));
        dispatcher.register(Commands.literal("eb").then(adminTree.build()));
    }

    // ==================== Help ====================

    private static int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("🔧 Admin Commands")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        src.sendSuccess(() -> Component.literal(""), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin tpspawn online")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - TP all online players to spawn")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin tpspawn offline")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Set offline players pos to spawn")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin tpspawn all")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Both online and offline")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin tpspawn player <name>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - TP specific player")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin info")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Server info")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin gc")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Run garbage collection")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin cache clear")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Clear all caches")
                        .withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb admin permissions")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show all LuckPerms permissions")
                        .withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    // ==================== Permissions ====================

    private static int showPermissions(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("═══════ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("EvolutionBoost Permissions").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" ═══════").withStyle(ChatFormatting.GOLD)), false);

        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("Use these with LuckPerms:")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        src.sendSuccess(() -> Component.literal("/lp user <player> permission set <perm> true")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        src.sendSuccess(() -> Component.literal(""), false);

        String lastCategory = "";
        for (Map.Entry<String, String> entry : PermissionRegistry.PERMISSIONS.entrySet()) {
            String perm = entry.getKey();
            String desc = entry.getValue();

            // Kategorie extrahieren (evolutionboost.X)
            String[] parts = perm.split("\\.");
            String category = parts.length > 1 ? parts[1].toUpperCase() : "OTHER";

            if (!category.equals(lastCategory)) {
                final String cat = category;
                src.sendSuccess(() -> Component.literal("▸ " + cat)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
                lastCategory = category;
            }

            final String permFinal = perm;
            final String descFinal = desc;
            src.sendSuccess(() -> Component.literal("  " + permFinal)
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                                    "/lp user @s permission set " + permFinal + " true"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to copy LuckPerms command")))), false);
            src.sendSuccess(() -> Component.literal("    " + descFinal)
                    .withStyle(ChatFormatting.GRAY), false);
        }

        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("════════════════════════════════════")
                .withStyle(ChatFormatting.GOLD), false);

        return 1;
    }

    // ==================== TP to Spawn ====================

    private static int tpSpawn(CommandSourceStack src, String type) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getSharedSpawnPos();

        int onlineCount = 0;
        int offlineCount = 0;

        // Online-Spieler teleportieren
        if ("online".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(overworld, 
                        spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("⚠ You have been teleported to spawn by an admin.")
                        .withStyle(ChatFormatting.YELLOW));
                onlineCount++;
            }
        }

        // Offline-Spieler Position setzen
        if ("offline".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            offlineCount = setOfflinePlayersToSpawn(server, overworld, spawnPos);
        }

        final int finalOnline = onlineCount;
        final int finalOffline = offlineCount;

        if ("online".equalsIgnoreCase(type)) {
            src.sendSuccess(() -> Component.literal("✓ Teleported " + finalOnline + " online players to spawn")
                    .withStyle(ChatFormatting.GREEN), true);
        } else if ("offline".equalsIgnoreCase(type)) {
            src.sendSuccess(() -> Component.literal("✓ Set spawn position for " + finalOffline + " offline players")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            src.sendSuccess(() -> Component.literal("✓ Teleported " + finalOnline + " online, set " + finalOffline + " offline players to spawn")
                    .withStyle(ChatFormatting.GREEN), true);
        }

        EvolutionBoost.LOGGER.info("[admin] {} teleported players to spawn: {} online, {} offline", 
                src.getTextName(), finalOnline, finalOffline);

        return 1;
    }

    private static int tpSpawnPlayer(CommandSourceStack src, ServerPlayer target) {
        ServerLevel overworld = src.getServer().overworld();
        BlockPos spawnPos = overworld.getSharedSpawnPos();

        target.teleportTo(overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                target.getYRot(), target.getXRot());

        target.sendSystemMessage(Component.literal("⚠ You have been teleported to spawn by an admin.")
                .withStyle(ChatFormatting.YELLOW));

        src.sendSuccess(() -> Component.literal("✓ Teleported " + target.getGameProfile().getName() + " to spawn")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    /**
     * Setzt die Position aller Offline-Spieler auf den Spawn.
     * Modifiziert die playerdata NBT-Dateien.
     */
    private static int setOfflinePlayersToSpawn(MinecraftServer server, ServerLevel overworld, BlockPos spawnPos) {
        // Playerdata Ordner finden - liegt im World-Ordner
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path playerDataDir = worldDir.resolve("playerdata");
        int count = 0;

        // Alle Online-Spieler UUIDs sammeln (diese nicht modifizieren)
        List<String> onlineUUIDs = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            onlineUUIDs.add(p.getUUID().toString());
        }

        try {
            File[] files = playerDataDir.toFile().listFiles((dir, name) -> name.endsWith(".dat"));
            if (files == null) return 0;

            for (File file : files) {
                String uuid = file.getName().replace(".dat", "");
                
                // Überspringe Online-Spieler
                if (onlineUUIDs.contains(uuid)) continue;

                try {
                    // NBT laden
                    CompoundTag nbt = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    
                    // Pos als Liste setzen (das ist das korrekte Format)
                    var posList = nbt.getList("Pos", 6); // 6 = Double
                    if (posList != null && posList.size() >= 3) {
                        posList.set(0, net.minecraft.nbt.DoubleTag.valueOf(spawnPos.getX() + 0.5));
                        posList.set(1, net.minecraft.nbt.DoubleTag.valueOf(spawnPos.getY()));
                        posList.set(2, net.minecraft.nbt.DoubleTag.valueOf(spawnPos.getZ() + 0.5));
                    }
                    
                    // Dimension auf Overworld setzen
                    nbt.putString("Dimension", "minecraft:overworld");
                    
                    // Speichern
                    NbtIo.writeCompressed(nbt, file.toPath());
                    count++;
                    
                } catch (Exception e) {
                    EvolutionBoost.LOGGER.warn("[admin] Failed to modify playerdata for {}: {}", uuid, e.getMessage());
                }
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[admin] Error accessing playerdata directory: {}", e.getMessage());
        }

        return count;
    }

    // ==================== Server Info ====================

    private static int showInfo(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        Runtime runtime = Runtime.getRuntime();

        long maxMem = runtime.maxMemory() / 1024 / 1024;
        long totalMem = runtime.totalMemory() / 1024 / 1024;
        long freeMem = runtime.freeMemory() / 1024 / 1024;
        long usedMem = totalMem - freeMem;

        src.sendSuccess(() -> Component.literal("🔧 Server Info")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal("Memory: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(usedMem + "MB")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" / " + maxMem + "MB")
                        .withStyle(ChatFormatting.GRAY)), false);

        // Memory bar
        int usedPercent = (int) ((usedMem * 100) / maxMem);
        int barFilled = usedPercent / 5; // 20 segments
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            bar.append(i < barFilled ? "█" : "░");
        }
        ChatFormatting barColor = usedPercent > 80 ? ChatFormatting.RED : 
                                  usedPercent > 60 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        
        src.sendSuccess(() -> Component.literal("[" + bar + "] " + usedPercent + "%")
                .withStyle(barColor), false);

        src.sendSuccess(() -> Component.literal("TPS: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f", getTPS(server)))
                        .withStyle(ChatFormatting.GREEN)), false);

        src.sendSuccess(() -> Component.literal("Players: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(server.getPlayerCount() + "/" + server.getMaxPlayers())
                        .withStyle(ChatFormatting.AQUA)), false);

        src.sendSuccess(() -> Component.literal("Loaded Chunks: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(getLoadedChunks(server)))
                        .withStyle(ChatFormatting.AQUA)), false);

        src.sendSuccess(() -> Component.literal("Entities: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(getEntityCount(server)))
                        .withStyle(ChatFormatting.AQUA)), false);

        return 1;
    }

    private static double getTPS(MinecraftServer server) {
        // In 1.21.1 gibt es keine direkte getAverageTickTime() Methode
        // Wir nutzen die tickTimes Array wenn verfügbar, sonst Schätzung
        try {
            long[] tickTimes = server.getTickTimesNanos();
            if (tickTimes != null && tickTimes.length > 0) {
                long sum = 0;
                for (long t : tickTimes) {
                    sum += t;
                }
                double avgNanos = (double) sum / tickTimes.length;
                double avgMs = avgNanos / 1_000_000.0;
                return Math.min(20.0, 1000.0 / Math.max(avgMs, 50.0));
            }
        } catch (Exception ignored) {}
        
        // Fallback: Annahme 20 TPS
        return 20.0;
    }

    private static int getLoadedChunks(MinecraftServer server) {
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getChunkSource().getLoadedChunksCount();
        }
        return total;
    }

    private static int getEntityCount(MinecraftServer server) {
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getAllEntities().spliterator().estimateSize();
        }
        return total;
    }

    // ==================== Garbage Collection ====================

    private static int runGC(CommandSourceStack src) {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        System.gc();

        long after = runtime.totalMemory() - runtime.freeMemory();
        long freed = (before - after) / 1024 / 1024;

        src.sendSuccess(() -> Component.literal("✓ Garbage collection completed")
                .withStyle(ChatFormatting.GREEN), false);
        src.sendSuccess(() -> Component.literal("  Freed: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(freed + " MB")
                        .withStyle(ChatFormatting.AQUA)), false);

        EvolutionBoost.LOGGER.info("[admin] {} ran GC, freed {} MB", src.getTextName(), freed);

        return 1;
    }

    // ==================== Cache Clear ====================

    private static int clearCaches(CommandSourceStack src) {
        // Dex Cache leeren
        com.ichezzy.evolutionboost.dex.DexDataManager.invalidateCache(null);
        
        src.sendSuccess(() -> Component.literal("✓ All caches cleared")
                .withStyle(ChatFormatting.GREEN), false);

        return 1;
    }
}
