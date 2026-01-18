package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import com.ichezzy.evolutionboost.item.TicketManager;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.permission.PermissionRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
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
import net.minecraft.world.level.Level;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-Befehle für Server-Management.
 * 
 * /eb admin tp <dimension>          - Teleportiert zu einer Dimension
 * /eb admin return                  - Teleportiert zum Overworld-Spawn
 * /eb admin setspawn <target>       - Setzt Event-Spawn an aktueller Position
 * /eb admin tpspawn online          - Teleportiert alle Online-Spieler zum Spawn
 * /eb admin tpspawn offline         - Setzt Offline-Spieler Position zum Spawn
 * /eb admin tpspawn all             - Beides
 * /eb admin tpspawn player <player> - Teleportiert einen Spieler zum Spawn
 * /eb admin info                    - Zeigt Server-Info
 * /eb admin gc                      - Führt Garbage Collection aus
 * /eb admin permissions             - Zeigt alle verfügbaren Permissions
 */
public final class AdminCommand {
    private AdminCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TPSPAWN_TYPE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("online", "offline", "all"), builder);

    private static final SuggestionProvider<CommandSourceStack> SETSPAWN_TARGET_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("halloween", "safari", "christmas"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var adminTree = Commands.literal("admin")
                .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.admin", 3, false))

                // /eb admin tp <dimension> - Teleport zu beliebiger Dimension
                .then(Commands.literal("tp")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> teleportToDimension(
                                        ctx.getSource(),
                                        DimensionArgument.getDimension(ctx, "dimension")))))

                // /eb admin return - Zurück zum Overworld-Spawn
                .then(Commands.literal("return")
                        .executes(ctx -> returnToOverworld(ctx.getSource())))

                // /eb admin setspawn <halloween|safari|christmas>
                .then(Commands.literal("setspawn")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(SETSPAWN_TARGET_SUGGEST)
                                .executes(ctx -> setEventSpawn(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target")))))

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
        
        src.sendSuccess(() -> Component.literal("/eb admin tp <dimension>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Teleport to any dimension")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin return")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Return to Overworld spawn")
                        .withStyle(ChatFormatting.GRAY)), false);
        
        src.sendSuccess(() -> Component.literal("/eb admin setspawn <target>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Set event spawn (halloween/safari/christmas)")
                        .withStyle(ChatFormatting.GRAY)), false);
        
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
        
        src.sendSuccess(() -> Component.literal("/eb admin tpspawn player <n>")
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

    // ==================== Dimension Teleport ====================

    private static int teleportToDimension(CommandSourceStack src, ServerLevel destination) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            // Spawn-Position für die Dimension ermitteln
            BlockPos spawnPos;
            String dimPath = destination.dimension().location().getPath();
            
            // Erst prüfen ob es ein bekanntes Event-Ziel ist
            TicketManager.Target target = TicketManager.Target.from(dimPath);
            if (target != null) {
                spawnPos = TicketManager.getSpawn(target);
            } else {
                // Prüfe Config für event:* Dimensionen
                EvolutionBoostConfig.Spawn configSpawn = EvolutionBoostConfig.get().getSpawn(dimPath);
                if (configSpawn != null) {
                    spawnPos = configSpawn.toBlockPos();
                } else {
                    // Fallback: Dimension-Spawn oder 0,80,0
                    spawnPos = destination.getSharedSpawnPos();
                    if (spawnPos.getY() < 1) {
                        spawnPos = new BlockPos(0, 80, 0);
                    }
                }
            }

            player.teleportTo(destination, 
                    spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 
                    player.getYRot(), player.getXRot());

            src.sendSuccess(() -> Component.literal("✓ Teleported to " + destination.dimension().location())
                    .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int returnToOverworld(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);

            if (overworld == null) {
                src.sendFailure(Component.literal("Could not find Overworld."));
                return 0;
            }

            BlockPos spawnPos = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, 
                    spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 
                    player.getYRot(), player.getXRot());

            src.sendSuccess(() -> Component.literal("✓ Returned to Overworld spawn")
                    .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int setEventSpawn(CommandSourceStack src, String targetName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            
            TicketManager.Target target = switch (targetName.toLowerCase()) {
                case "halloween" -> TicketManager.Target.HALLOWEEN;
                case "safari" -> TicketManager.Target.SAFARI;
                case "christmas" -> TicketManager.Target.CHRISTMAS;
                default -> null;
            };

            if (target == null) {
                src.sendFailure(Component.literal("Unknown target. Use: halloween, safari, christmas"));
                return 0;
            }

            BlockPos pos = player.blockPosition();
            TicketManager.setSpawn(target, pos);
            
            src.sendSuccess(() -> Component.literal("✓ " + target.key() + " spawn set to " + 
                    pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
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
        src.sendSuccess(() -> Component.literal("════════════════════════════════").withStyle(ChatFormatting.GOLD), false);

        return 1;
    }

    // ==================== TpSpawn ====================

    private static int tpSpawn(CommandSourceStack src, String type) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        
        if (overworld == null) {
            src.sendFailure(Component.literal("Could not find Overworld."));
            return 0;
        }

        BlockPos spawnPos = overworld.getSharedSpawnPos();
        int onlineCount = 0;
        int offlineCount = 0;

        switch (type.toLowerCase()) {
            case "online" -> {
                onlineCount = tpOnlinePlayers(server, overworld, spawnPos);
            }
            case "offline" -> {
                offlineCount = setOfflinePlayersToSpawn(server, spawnPos);
            }
            case "all" -> {
                onlineCount = tpOnlinePlayers(server, overworld, spawnPos);
                offlineCount = setOfflinePlayersToSpawn(server, spawnPos);
            }
            default -> {
                src.sendFailure(Component.literal("Unknown type. Use: online, offline, all"));
                return 0;
            }
        }

        final int finalOnline = onlineCount;
        final int finalOffline = offlineCount;

        src.sendSuccess(() -> Component.literal("✓ Teleport to spawn complete!")
                .withStyle(ChatFormatting.GREEN), false);
        
        if (finalOnline > 0) {
            src.sendSuccess(() -> Component.literal("  Online players teleported: " + finalOnline)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (finalOffline > 0) {
            src.sendSuccess(() -> Component.literal("  Offline players modified: " + finalOffline)
                    .withStyle(ChatFormatting.GRAY), false);
        }

        EvolutionBoost.LOGGER.info("[admin] {} ran tpspawn {}: {} online, {} offline", 
                src.getTextName(), type, finalOnline, finalOffline);

        return 1;
    }

    private static int tpSpawnPlayer(CommandSourceStack src, ServerPlayer target) {
        ServerLevel overworld = src.getServer().getLevel(Level.OVERWORLD);
        
        if (overworld == null) {
            src.sendFailure(Component.literal("Could not find Overworld."));
            return 0;
        }

        BlockPos spawnPos = overworld.getSharedSpawnPos();
        target.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 
                target.getYRot(), target.getXRot());

        src.sendSuccess(() -> Component.literal("✓ Teleported " + target.getName().getString() + " to spawn")
                .withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int tpOnlinePlayers(MinecraftServer server, ServerLevel overworld, BlockPos spawnPos) {
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 
                    p.getYRot(), p.getXRot());
            count++;
        }
        return count;
    }

    private static int setOfflinePlayersToSpawn(MinecraftServer server, BlockPos spawnPos) {
        // Pfad zu playerdata
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).getParent();
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
