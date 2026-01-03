package com.ichezzy.evolutionboost.dex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.CaughtCount;
import com.cobblemon.mod.common.api.pokedex.Dexes;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig.Milestone;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig.RewardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Dex-Reward Claims pro Spieler.
 * Der Pokédex-Stand wird direkt von Cobblemon abgefragt!
 *
 * Persistenz: <world>/evolutionboost/dex_claims.json
 */
public final class DexDataManager {
    private DexDataManager() {}

    // Speichert nur CLAIMS, nicht den Pokédex-Stand
    private static final Map<UUID, PlayerClaimData> CLAIMS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_NAMES = new ConcurrentHashMap<>();

    // Tracking für bereits benachrichtigte Milestones (um Spam zu vermeiden)
    private static final Map<UUID, Set<String>> NOTIFIED_MILESTONES = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ==================== Initialization ====================

    public static void init(MinecraftServer server) {
        SERVER = server;
        loadData();
        EvolutionBoost.LOGGER.info("[dex] DexDataManager initialized with {} player entries.", CLAIMS.size());
    }

    public static void shutdown() {
        saveData();
        EvolutionBoost.LOGGER.info("[dex] DexDataManager saved and shut down.");
    }

    // ==================== Cobblemon Pokédex Integration ====================

    private static PokedexManager getPokedex(ServerPlayer player) {
        return Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
    }

    /**
     * Gibt die Anzahl der gefangenen Pokémon (unique Species) zurück.
     */
    public static int getCaughtCount(ServerPlayer player, ResourceLocation dexId) {
        try {
            PokedexManager pokedex = getPokedex(player);
            if (dexId != null) {
                return pokedex.getDexCalculatedValue(dexId, CaughtCount.INSTANCE);
            } else {
                return pokedex.getGlobalCalculatedValue(CaughtCount.INSTANCE);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[dex] Error getting caught count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Berechnet den Pokédex-Fortschritt als Prozent.
     * Nutzt unsere eigene totalPokemonCount Config statt Cobblemon's (die alle Formen zählt).
     */
    public static float getCaughtPercent(ServerPlayer player, ResourceLocation dexId) {
        int caught = getCaughtCount(player, dexId);
        int total = getTotalCount(dexId);
        if (total <= 0) return 0f;
        return (caught * 100f) / total;
    }

    /**
     * Gibt die Gesamtanzahl der Pokémon zurück.
     */
    public static int getTotalCount(ResourceLocation dexId) {
        try {
            if (dexId != null) {
                var def = Dexes.INSTANCE.getDexEntryMap().get(dexId);
                if (def != null) {
                    return def.getEntries().size();
                }
            }
            return DexRewardsConfig.get().totalPokemonCount;
        } catch (Exception e) {
            return DexRewardsConfig.get().totalPokemonCount;
        }
    }

    public static List<ResourceLocation> getAvailableDexes() {
        try {
            return new ArrayList<>(Dexes.INSTANCE.getDexEntryMap().keySet());
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== Milestone Checking ====================

    public static boolean hasReachedMilestone(ServerPlayer player, Milestone milestone) {
        ResourceLocation dexId = milestone.dexId != null ? ResourceLocation.tryParse(milestone.dexId) : null;
        float percent = getCaughtPercent(player, dexId);
        return percent >= milestone.percent;
    }

    public static boolean hasClaimedMilestone(UUID uuid, String milestoneId) {
        PlayerClaimData data = CLAIMS.get(uuid);
        return data != null && data.claimedMilestones.contains(milestoneId.toLowerCase());
    }

    public static boolean canClaimMilestone(ServerPlayer player, String milestoneId) {
        if (!DexRewardsConfig.isEnabled()) return false;
        Optional<Milestone> opt = DexRewardsConfig.get().getMilestone(milestoneId);
        if (opt.isEmpty()) return false;
        Milestone milestone = opt.get();
        return hasReachedMilestone(player, milestone) && !hasClaimedMilestone(player.getUUID(), milestoneId);
    }

    /**
     * Prüft alle Milestones und benachrichtigt bei neuen Erreichungen.
     */
    public static void checkMilestonesAndNotify(ServerPlayer player) {
        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) return;

        UUID uuid = player.getUUID();
        PLAYER_NAMES.put(uuid, player.getGameProfile().getName());

        Set<String> notified = NOTIFIED_MILESTONES.computeIfAbsent(uuid, k -> new HashSet<>());
        DexRewardsConfig config = DexRewardsConfig.get();

        for (Milestone milestone : config.milestones) {
            String id = milestone.id.toLowerCase();

            if (hasClaimedMilestone(uuid, id) || notified.contains(id)) {
                continue;
            }

            if (hasReachedMilestone(player, milestone)) {
                notified.add(id);

                player.sendSystemMessage(
                        Component.literal("🎉 Milestone reached: ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(milestone.name)
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                );
                player.sendSystemMessage(
                        Component.literal("   → ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("/eb dex claim " + milestone.id)
                                        .withStyle(ChatFormatting.GREEN))
                );
            }
        }
    }

    // ==================== Claiming ====================

    public static boolean claimMilestone(ServerPlayer player, String milestoneId) {
        if (player == null) return false;

        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("❌ Dex rewards are currently disabled.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID uuid = player.getUUID();
        Optional<Milestone> opt = DexRewardsConfig.get().getMilestone(milestoneId);

        if (opt.isEmpty()) {
            player.sendSystemMessage(Component.literal("❌ Unknown milestone: " + milestoneId)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Milestone milestone = opt.get();

        if (!hasReachedMilestone(player, milestone)) {
            ResourceLocation dexId = milestone.dexId != null ? ResourceLocation.tryParse(milestone.dexId) : null;
            float percent = getCaughtPercent(player, dexId);
            player.sendSystemMessage(Component.literal("❌ Not reached! ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("(" + String.format("%.1f", percent) + "% / " + milestone.percent + "%)")
                            .withStyle(ChatFormatting.GRAY)));
            return false;
        }

        if (hasClaimedMilestone(uuid, milestoneId)) {
            player.sendSystemMessage(Component.literal("⚠ Already claimed!")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        // Rewards geben
        boolean allGiven = giveRewards(player, milestone.rewards);

        if (allGiven) {
            PlayerClaimData data = CLAIMS.computeIfAbsent(uuid, k -> new PlayerClaimData());
            data.claimedMilestones.add(milestoneId.toLowerCase());
            saveData();

            player.sendSystemMessage(Component.literal("✨ " + milestone.name + " claimed!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (RewardItem reward : milestone.rewards) {
                String itemName = reward.id.contains(":") ? reward.id.split(":")[1] : reward.id;
                player.sendSystemMessage(Component.literal("   + " + reward.count + "x " + itemName)
                        .withStyle(ChatFormatting.GREEN));
            }

            // Hinweis auf Pokemon-Reward
            if (milestone.pokemonReward != null) {
                player.sendSystemMessage(Component.literal("🎁 Bonus: " + milestone.pokemonReward.description)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                player.sendSystemMessage(Component.literal("   → /eb dex pokemon " + milestone.id + " <species>")
                        .withStyle(ChatFormatting.GRAY));
            }

            EvolutionBoost.LOGGER.info("[dex] {} claimed milestone '{}'",
                    player.getGameProfile().getName(), milestoneId);
            return true;
        } else {
            player.sendSystemMessage(Component.literal("❌ Inventory full!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static List<Milestone> getClaimableMilestones(ServerPlayer player) {
        if (!DexRewardsConfig.isEnabled()) return List.of();
        
        List<Milestone> claimable = new ArrayList<>();
        DexRewardsConfig config = DexRewardsConfig.get();

        for (Milestone m : config.milestones) {
            if (hasReachedMilestone(player, m) && !hasClaimedMilestone(player.getUUID(), m.id)) {
                claimable.add(m);
            }
        }
        return claimable;
    }

    public static Set<String> getClaimedMilestones(UUID uuid) {
        PlayerClaimData data = CLAIMS.get(uuid);
        return data != null ? Collections.unmodifiableSet(data.claimedMilestones) : Set.of();
    }

    // ==================== Pokemon Reward Methods ====================

    public static boolean hasClaimedPokemonReward(UUID uuid, String milestoneId) {
        PlayerClaimData data = CLAIMS.get(uuid);
        return data != null && data.claimedPokemonRewards.contains(milestoneId.toLowerCase());
    }

    public static boolean canClaimPokemonReward(ServerPlayer player, String milestoneId) {
        if (!DexRewardsConfig.isEnabled()) return false;
        
        Optional<Milestone> opt = DexRewardsConfig.get().getMilestone(milestoneId);
        if (opt.isEmpty()) return false;

        Milestone milestone = opt.get();
        return milestone.pokemonReward != null
                && hasReachedMilestone(player, milestone)
                && hasClaimedMilestone(player.getUUID(), milestoneId)
                && !hasClaimedPokemonReward(player.getUUID(), milestoneId);
    }

    public static List<Milestone> getClaimablePokemonRewards(ServerPlayer player) {
        if (!DexRewardsConfig.isEnabled()) return List.of();
        
        List<Milestone> claimable = new ArrayList<>();
        DexRewardsConfig config = DexRewardsConfig.get();

        for (Milestone m : config.milestones) {
            if (m.pokemonReward != null && canClaimPokemonReward(player, m.id)) {
                claimable.add(m);
            }
        }
        return claimable;
    }

    public static Set<String> getClaimedPokemonRewards(UUID uuid) {
        PlayerClaimData data = CLAIMS.get(uuid);
        return data != null ? Collections.unmodifiableSet(data.claimedPokemonRewards) : Set.of();
    }

    public static void markPokemonRewardClaimed(UUID uuid, String milestoneId) {
        PlayerClaimData data = CLAIMS.computeIfAbsent(uuid, k -> new PlayerClaimData());
        data.claimedPokemonRewards.add(milestoneId.toLowerCase());
        saveData();
    }

    // ==================== Notifications ====================

    /**
     * Benachrichtigt einen Spieler beim Login über verfügbare Rewards.
     */
    public static void notifyOnJoin(ServerPlayer player) {
        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) return;
        
        // Check notification setting
        if (!com.ichezzy.evolutionboost.configs.NotificationConfig.isEnabled(player.getUUID(),
                com.ichezzy.evolutionboost.configs.NotificationConfig.NotificationType.DEX)) {
            return;
        }

        List<Milestone> claimable = getClaimableMilestones(player);
        List<Milestone> claimablePokemon = getClaimablePokemonRewards(player);

        if (!claimable.isEmpty()) {
            List<String> names = claimable.stream().map(m -> m.name).toList();
            player.sendSystemMessage(Component.literal("📖 Dex milestones available: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(String.join(", ", names))
                            .withStyle(ChatFormatting.YELLOW)));
            player.sendSystemMessage(Component.literal("   → /eb dex info")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (!claimablePokemon.isEmpty()) {
            player.sendSystemMessage(Component.literal("🎁 Pokémon rewards available: " + claimablePokemon.size())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ==================== Admin Functions ====================

    /**
     * Invalidiert interne Caches.
     * @param uuid Spieler-UUID oder null für alle Spieler
     */
    public static void invalidateCache(UUID uuid) {
        if (uuid == null) {
            NOTIFIED_MILESTONES.clear();
            EvolutionBoost.LOGGER.debug("[dex] All notification caches cleared");
        } else {
            NOTIFIED_MILESTONES.remove(uuid);
            EvolutionBoost.LOGGER.debug("[dex] Notification cache cleared for {}", uuid);
        }
    }

    public static void resetRewards(UUID uuid, String type) {
        PlayerClaimData data = CLAIMS.get(uuid);
        if (data == null) {
            data = new PlayerClaimData();
            CLAIMS.put(uuid, data);
        }

        if ("all".equalsIgnoreCase(type)) {
            data.claimedMilestones.clear();
            data.claimedPokemonRewards.clear();
        } else if ("pokemon".equalsIgnoreCase(type)) {
            data.claimedPokemonRewards.clear();
        } else {
            data.claimedMilestones.remove(type.toLowerCase());
            data.claimedPokemonRewards.remove(type.toLowerCase());
        }

        NOTIFIED_MILESTONES.remove(uuid);
        saveData();
    }

    // ==================== Reward Giving ====================

    private static boolean giveRewards(ServerPlayer player, List<RewardItem> rewards) {
        if (rewards == null || rewards.isEmpty()) return true;

        var registry = player.server.registryAccess().registryOrThrow(Registries.ITEM);
        List<ItemStack> stacks = new ArrayList<>();

        for (RewardItem reward : rewards) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(reward.id);
                if (loc == null) continue;

                Optional<Item> optItem = registry.getOptional(loc);
                if (optItem.isEmpty()) continue;

                Item item = optItem.get();
                int remaining = reward.count;

                while (remaining > 0) {
                    int stackSize = Math.min(remaining, item.getDefaultMaxStackSize());
                    stacks.add(new ItemStack(item, stackSize));
                    remaining -= stackSize;
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[dex] Error creating item {}: {}", reward.id, e.getMessage());
            }
        }

        // Prüfen ob genug Platz
        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                emptySlots++;
            }
        }

        if (emptySlots < stacks.size()) {
            return false;
        }

        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        return true;
    }

    // ==================== Persistence ====================

    private static Path getDataFile() {
        if (SERVER == null) {
            return Path.of("config", "evolutionboost", "dex_claims.json");
        }
        Path root = SERVER.getWorldPath(LevelResource.ROOT);
        Path sub = root.resolve("evolutionboost");
        try { Files.createDirectories(sub); } catch (Exception ignored) {}
        return sub.resolve("dex_claims.json");
    }

    private static void loadData() {
        Path file = getDataFile();
        if (!Files.exists(file)) {
            EvolutionBoost.LOGGER.info("[dex] No dex_claims.json found, starting fresh.");
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ClaimsDataFile loaded = GSON.fromJson(br, ClaimsDataFile.class);
            if (loaded != null && loaded.players != null) {
                CLAIMS.clear();
                PLAYER_NAMES.clear();

                for (ClaimsDataFile.PlayerEntry entry : loaded.players) {
                    try {
                        UUID uuid = UUID.fromString(entry.uuid);
                        PlayerClaimData data = new PlayerClaimData();

                        if (entry.milestones != null) {
                            data.claimedMilestones.addAll(entry.milestones);
                        }
                        if (entry.pokemon != null) {
                            data.claimedPokemonRewards.addAll(entry.pokemon);
                        }

                        CLAIMS.put(uuid, data);
                        if (entry.name != null) {
                            PLAYER_NAMES.put(uuid, entry.name);
                        }
                    } catch (Exception e) {
                        EvolutionBoost.LOGGER.warn("[dex] Failed to load entry for {}: {}",
                                entry.uuid, e.getMessage());
                    }
                }
                EvolutionBoost.LOGGER.info("[dex] Loaded {} player claim entries", CLAIMS.size());
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[dex] Failed to load dex_claims.json: {}", e.getMessage());
        }
    }

    private static void saveData() {
        try {
            Path file = getDataFile();
            Files.createDirectories(file.getParent());

            ClaimsDataFile out = new ClaimsDataFile();
            out.players = new ArrayList<>();

            for (Map.Entry<UUID, PlayerClaimData> entry : CLAIMS.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerClaimData data = entry.getValue();

                ClaimsDataFile.PlayerEntry pe = new ClaimsDataFile.PlayerEntry();
                pe.uuid = uuid.toString();
                pe.name = PLAYER_NAMES.getOrDefault(uuid, "Unknown");
                pe.milestones = new ArrayList<>(data.claimedMilestones);
                pe.pokemon = new ArrayList<>(data.claimedPokemonRewards);

                Collections.sort(pe.milestones);
                Collections.sort(pe.pokemon);

                out.players.add(pe);
            }

            out.players.sort(Comparator.comparing(p -> p.name != null ? p.name : ""));

            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(out, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[dex] Failed to save dex_claims.json: {}", e.getMessage());
        }
    }

    // ==================== Data Classes ====================

    private static final class PlayerClaimData {
        final Set<String> claimedMilestones = new HashSet<>();
        final Set<String> claimedPokemonRewards = new HashSet<>();
    }

    private static final class ClaimsDataFile {
        List<PlayerEntry> players = new ArrayList<>();

        static final class PlayerEntry {
            String uuid;
            String name;
            List<String> milestones;
            List<String> pokemon;
        }
    }
}
