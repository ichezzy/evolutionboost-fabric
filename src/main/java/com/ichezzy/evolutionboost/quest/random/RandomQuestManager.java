package com.ichezzy.evolutionboost.quest.random;

import com.google.gson.*;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.NotificationConfig;
import com.ichezzy.evolutionboost.configs.RandomQuestConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager für das Random Quest System.
 * Generiert deterministische Quests basierend auf dem Datum,
 * trackt Spieler-Fortschritt und verwaltet Streaks.
 */
public class RandomQuestManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RandomQuestManager INSTANCE;

    private MinecraftServer server;
    private Path dataDir;

    /** Spieler-Fortschritt: UUID -> Period -> Progress */
    private final Map<UUID, Map<RandomQuestPeriod, QuestProgress>> playerProgress = new ConcurrentHashMap<>();

    /** Spieler-Streaks: UUID -> Period -> Streak-Daten */
    private final Map<UUID, Map<RandomQuestPeriod, StreakData>> playerStreaks = new ConcurrentHashMap<>();

    /** Cache für generierte Quests pro Periode und Seed */
    private final Map<String, GeneratedQuest> questCache = new ConcurrentHashMap<>();

    // ==================== Singleton ====================

    public static RandomQuestManager get() {
        if (INSTANCE == null) {
            INSTANCE = new RandomQuestManager();
        }
        return INSTANCE;
    }

    // ==================== Initialization ====================

    public void init(MinecraftServer server) {
        this.server = server;
        this.dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("evolutionboost");

        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[random-quests] Failed to create data directory", e);
        }

        loadProgress();
        loadStreaks();

        EvolutionBoost.LOGGER.info("[random-quests] Initialized with {} players tracked", playerProgress.size());
    }

    public void shutdown() {
        saveProgress();
        saveStreaks();
    }

    // ==================== Quest Generation ====================

    /**
     * Generiert die Quest für eine bestimmte Periode.
     * Verwendet einen deterministischen Seed basierend auf dem Datum.
     */
    public GeneratedQuest getQuest(RandomQuestPeriod period) {
        String seed = generateSeedWithSuffix(period);
        String cacheKey = period.getId() + ":" + seed;

        return questCache.computeIfAbsent(cacheKey, k -> generateQuest(period, seed));
    }

    /**
     * Generiert den Basis-Seed basierend auf der aktuellen Zeit.
     */
    private String generateSeed(RandomQuestPeriod period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        return switch (period) {
            case DAILY -> String.format("%d-%03d", now.getYear(), now.getDayOfYear());
            case WEEKLY -> {
                // Woche beginnt am Montag
                ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield String.format("%d-W%02d", weekStart.getYear(), weekStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            }
            case MONTHLY -> String.format("%d-%02d", now.getYear(), now.getMonthValue());
        };
    }

    /**
     * Generiert den Seed mit optionalem Admin-Suffix.
     */
    private String generateSeedWithSuffix(RandomQuestPeriod period) {
        String baseSeed = generateSeed(period);
        int suffix = seedSuffix.getOrDefault(period, 0);
        return suffix > 0 ? baseSeed + "-R" + suffix : baseSeed;
    }

    /**
     * Generiert eine Quest basierend auf Seed und Periode.
     */
    private GeneratedQuest generateQuest(RandomQuestPeriod period, String seed) {
        RandomQuestConfig.PeriodConfig config = RandomQuestConfig.get().getPeriodConfig(period);
        Random random = new Random(seed.hashCode());

        List<GeneratedObjective> objectives = new ArrayList<>();
        Set<String> usedTypes = new HashSet<>(); // Verhindere Duplikate

        // Weighted Random Selection
        List<RandomQuestConfig.ObjectiveTemplate> availablePool = new ArrayList<>(config.pool);

        for (int i = 0; i < config.objectiveCount && !availablePool.isEmpty(); i++) {
            // Gesamtgewicht berechnen
            int totalWeight = availablePool.stream().mapToInt(t -> t.weight).sum();
            int roll = random.nextInt(totalWeight);

            // Weighted Selection
            RandomQuestConfig.ObjectiveTemplate selected = null;
            int cumulative = 0;
            for (RandomQuestConfig.ObjectiveTemplate template : availablePool) {
                cumulative += template.weight;
                if (roll < cumulative) {
                    selected = template;
                    break;
                }
            }

            if (selected == null) continue;

            // Duplikate vermeiden (gleicher Typ + gleicher Pokémon-Typ)
            String typeKey = selected.type;
            if (usedTypes.contains(typeKey)) {
                availablePool.remove(selected);
                i--; // Retry
                continue;
            }
            usedTypes.add(typeKey);

            // Amount berechnen
            int amount = selected.amountMin + random.nextInt(selected.amountMax - selected.amountMin + 1);

            // Pokémon-Typ oder Natur auswählen falls nötig
            String parameter = null;
            RandomQuestObjectiveType objType = RandomQuestObjectiveType.fromId(selected.type);

            if (objType != null) {
                if (objType.requiresPokemonType() && selected.types != null && !selected.types.isEmpty()) {
                    parameter = selected.types.get(random.nextInt(selected.types.size()));
                } else if (objType.requiresNature() && selected.natures != null && !selected.natures.isEmpty()) {
                    parameter = selected.natures.get(random.nextInt(selected.natures.size()));
                }
            }

            objectives.add(new GeneratedObjective(selected.type, amount, parameter));
            availablePool.remove(selected);
        }

        return new GeneratedQuest(period, seed, objectives);
    }

    // ==================== Progress Tracking ====================

    /**
     * Holt den Fortschritt eines Spielers für eine Periode.
     */
    public QuestProgress getProgress(UUID playerId, RandomQuestPeriod period) {
        GeneratedQuest quest = getQuest(period);

        Map<RandomQuestPeriod, QuestProgress> periodProgress = playerProgress
                .computeIfAbsent(playerId, k -> new EnumMap<>(RandomQuestPeriod.class));

        QuestProgress progress = periodProgress.get(period);

        // Neue Quest? Reset Progress
        if (progress == null || !progress.seed.equals(quest.seed)) {
            progress = new QuestProgress(quest.seed, quest.objectives.size());
            periodProgress.put(period, progress);
        }

        return progress;
    }

    /**
     * Fügt Fortschritt für einen Spieler hinzu.
     * @return true wenn ein Objective abgeschlossen wurde
     */
    public boolean addProgress(UUID playerId, RandomQuestObjectiveType type, int amount, String parameter) {
        boolean anyCompleted = false;

        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            GeneratedQuest quest = getQuest(period);
            QuestProgress progress = getProgress(playerId, period);

            if (progress.completed) continue;

            for (int i = 0; i < quest.objectives.size(); i++) {
                GeneratedObjective obj = quest.objectives.get(i);

                if (!obj.type.equals(type.getId())) continue;

                // Parameter-Check (Typ oder Natur)
                if (obj.parameter != null && parameter != null) {
                    if (!obj.parameter.equalsIgnoreCase(parameter)) continue;
                }

                int oldProgress = progress.objectiveProgress[i];
                int newProgress = Math.min(oldProgress + amount, obj.amount);
                progress.objectiveProgress[i] = newProgress;

                if (oldProgress < obj.amount && newProgress >= obj.amount) {
                    anyCompleted = true;
                }
            }
        }

        if (anyCompleted) {
            saveProgress();
        }

        return anyCompleted;
    }

    /**
     * Prüft ob alle Objectives einer Quest abgeschlossen sind.
     */
    public boolean isQuestComplete(UUID playerId, RandomQuestPeriod period) {
        GeneratedQuest quest = getQuest(period);
        QuestProgress progress = getProgress(playerId, period);

        for (int i = 0; i < quest.objectives.size(); i++) {
            if (progress.objectiveProgress[i] < quest.objectives.get(i).amount) {
                return false;
            }
        }

        return true;
    }

    /**
     * Schließt eine Quest ab und gibt Rewards.
     * @return true wenn erfolgreich
     */
    public boolean turnInQuest(ServerPlayer player, RandomQuestPeriod period) {
        UUID playerId = player.getUUID();

        if (!isQuestComplete(playerId, period)) {
            return false;
        }

        QuestProgress progress = getProgress(playerId, period);
        if (progress.completed) {
            return false; // Bereits abgegeben
        }

        // Als abgeschlossen markieren
        progress.completed = true;

        // Coin-Berechnung: Base + Streak-Bonus
        int coinAmount = period.getBaseCoinAmount();
        if (period.hasStreak()) {
            StreakData streak = getStreak(playerId, period);
            // Bonus ist der aktuelle Streak, gecapped bei maxStreak
            int bonus = Math.min(streak.currentStreak, period.getMaxStreak());
            coinAmount = period.getBaseCoinAmount() + bonus;
            // Streak erhöhen (für nächsten Tag)
            streak.currentStreak++;
            streak.lastCompletionSeed = progress.seed;
        }

        // Rewards geben
        giveRewards(player, period, coinAmount);

        saveProgress();
        saveStreaks();

        return true;
    }

    // ==================== Streaks ====================

    /**
     * Holt die Streak-Daten eines Spielers.
     */
    public StreakData getStreak(UUID playerId, RandomQuestPeriod period) {
        // Keine Streaks für Perioden ohne Streak-Support
        if (!period.hasStreak()) {
            return new StreakData();
        }

        Map<RandomQuestPeriod, StreakData> periodStreaks = playerStreaks
                .computeIfAbsent(playerId, k -> new EnumMap<>(RandomQuestPeriod.class));

        StreakData streak = periodStreaks.computeIfAbsent(period, k -> new StreakData());

        // Streak-Reset prüfen (wenn letzte Completion nicht die vorherige Periode war)
        String currentSeed = generateSeed(period);
        String previousSeed = generatePreviousSeed(period);

        if (streak.lastCompletionSeed != null && 
            !streak.lastCompletionSeed.equals(previousSeed) && 
            !streak.lastCompletionSeed.equals(currentSeed)) {
            // Streak gebrochen!
            streak.currentStreak = 0;
            streak.lastCompletionSeed = null;
        }

        return streak;
    }

    /**
     * Generiert den Seed für die vorherige Periode.
     */
    private String generatePreviousSeed(RandomQuestPeriod period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        return switch (period) {
            case DAILY -> {
                ZonedDateTime yesterday = now.minusDays(1);
                yield String.format("%d-%03d", yesterday.getYear(), yesterday.getDayOfYear());
            }
            case WEEKLY -> {
                ZonedDateTime lastWeek = now.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield String.format("%d-W%02d", lastWeek.getYear(), lastWeek.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            }
            case MONTHLY -> {
                ZonedDateTime lastMonth = now.minusMonths(1);
                yield String.format("%d-%02d", lastMonth.getYear(), lastMonth.getMonthValue());
            }
        };
    }

    // ==================== Rewards ====================

    private void giveRewards(ServerPlayer player, RandomQuestPeriod period, int coinAmount) {
        // Coins geben
        String coinId = period.getCoinItem();
        giveItem(player, coinId, coinAmount);

        // Nachricht
        player.sendSystemMessage(Component.literal("✓ ")
                .withStyle(period.getColor())
                .append(Component.literal(period.getDisplayName() + " Quest completed!")
                        .withStyle(ChatFormatting.WHITE)));

        if (period.hasStreak()) {
            StreakData streak = getStreak(player.getUUID(), period);
            // streak.currentStreak wurde bereits erhöht, also -1 für den aktuellen Tag
            int currentDay = streak.currentStreak;
            int bonus = coinAmount - period.getBaseCoinAmount();
            int maxBonus = period.getMaxStreak();
            
            player.sendSystemMessage(Component.literal("  Day " + currentDay + " Streak")
                    .withStyle(ChatFormatting.GRAY)
                    .append(bonus > 0 
                            ? Component.literal(" (+" + bonus + " bonus)")
                                    .withStyle(ChatFormatting.GREEN) 
                            : Component.literal("")));
            player.sendSystemMessage(Component.literal("  Reward: +" + coinAmount + " ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(getCoinName(period))
                            .withStyle(period.getColor()))
                    .append(bonus < maxBonus 
                            ? Component.literal(" (next: +" + (coinAmount + 1) + ")")
                                    .withStyle(ChatFormatting.DARK_GRAY)
                            : Component.literal(" (max!)")
                                    .withStyle(ChatFormatting.GOLD)));
        } else {
            player.sendSystemMessage(Component.literal("  Reward: +" + coinAmount + " ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(getCoinName(period))
                            .withStyle(period.getColor())));
        }

        // Bonus Rewards (Chance-basiert)
        Random random = new Random();
        for (RandomQuestConfig.BonusReward bonus : RandomQuestConfig.get().bonusRewardPool) {
            if (!bonus.periods.contains(period.getId())) continue;

            if (random.nextDouble() < bonus.chance) {
                giveItem(player, bonus.item, bonus.amount);
                player.sendSystemMessage(Component.literal("  Bonus: +" + bonus.amount + " ")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(getItemName(bonus.item))
                                .withStyle(ChatFormatting.WHITE)));
            }
        }
    }

    private void giveItem(ServerPlayer player, String itemId, int amount) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item != null) {
                ItemStack stack = new ItemStack(item, amount);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[random-quests] Failed to give item {}: {}", itemId, e.getMessage());
        }
    }

    private String getCoinName(RandomQuestPeriod period) {
        return switch (period) {
            case DAILY -> "Bronze Coin";
            case WEEKLY -> "Silver Coin";
            case MONTHLY -> "Gold Coin";
        };
    }

    private String getItemName(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            return item.getDescription().getString();
        } catch (Exception e) {
            return itemId;
        }
    }

    // ==================== Reset Notification ====================

    /**
     * Benachrichtigt alle Online-Spieler über neue Quests.
     * Sollte bei Server-Tick um 00:00 UTC aufgerufen werden.
     */
    public void notifyNewQuests(RandomQuestPeriod period) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            notifyPlayerNewQuest(player, period);
        }

        // Quest-Cache für diese Periode leeren
        questCache.entrySet().removeIf(e -> e.getKey().startsWith(period.getId() + ":"));
    }

    /**
     * Benachrichtigt einen Spieler über eine neue Quest.
     */
    private void notifyPlayerNewQuest(ServerPlayer player, RandomQuestPeriod period) {
        // Prüfe ob Notifications aktiviert sind
        if (!NotificationConfig.isEnabled(player.getUUID(), NotificationConfig.NotificationType.QUESTS)) return;

        player.sendSystemMessage(Component.literal("✦ New " + period.getDisplayName() + " Quest available!")
                .withStyle(period.getColor(), ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  Use /eb quest " + period.getId() + " to view")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Benachrichtigt einen Spieler beim Login über verfügbare/abholbereite Random Quests.
     */
    public void notifyOnLogin(ServerPlayer player) {
        // Prüfe ob Notifications aktiviert sind
        if (!NotificationConfig.isEnabled(player.getUUID(), NotificationConfig.NotificationType.QUESTS)) return;

        UUID playerId = player.getUUID();
        boolean hasNotification = false;

        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            QuestProgress progress = getProgressIfExists(playerId, period);
            
            if (progress == null) {
                // Quest noch nie angeschaut = verfügbar
                if (!hasNotification) {
                    player.sendSystemMessage(Component.literal("✦ Random Quests:")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    hasNotification = true;
                }
                player.sendSystemMessage(Component.literal("  • " + period.getDisplayName() + " Quest available!")
                        .withStyle(period.getColor()));
            } else if (!progress.completed && isQuestCompleteInternal(progress, period)) {
                // Quest fertig aber noch nicht abgegeben
                if (!hasNotification) {
                    player.sendSystemMessage(Component.literal("✦ Random Quests:")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    hasNotification = true;
                }
                player.sendSystemMessage(Component.literal("  • " + period.getDisplayName() + " Quest ready to turn in!")
                        .withStyle(period.getColor(), ChatFormatting.BOLD));
            }
        }

        if (hasNotification) {
            player.sendSystemMessage(Component.literal("  Use /eb quest <daily|weekly|monthly> to view")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Holt Progress nur wenn er existiert (ohne neuen zu erstellen).
     */
    private QuestProgress getProgressIfExists(UUID playerId, RandomQuestPeriod period) {
        Map<RandomQuestPeriod, QuestProgress> periodProgress = playerProgress.get(playerId);
        if (periodProgress == null) return null;

        QuestProgress progress = periodProgress.get(period);
        if (progress == null) return null;

        // Prüfen ob der Seed noch aktuell ist
        String currentSeed = generateSeedWithSuffix(period);
        if (!currentSeed.equals(progress.seed)) {
            return null; // Alte Quest, zählt als "nicht vorhanden"
        }

        return progress;
    }

    /**
     * Prüft intern ob alle Objectives erfüllt sind.
     */
    private boolean isQuestCompleteInternal(QuestProgress progress, RandomQuestPeriod period) {
        GeneratedQuest quest = getQuest(period);
        for (int i = 0; i < quest.objectives.size(); i++) {
            if (progress.objectiveProgress[i] < quest.objectives.get(i).amount) {
                return false;
            }
        }
        return true;
    }

    // ==================== Persistence ====================

    private void loadProgress() {
        Path file = dataDir.resolve("random_quest_progress.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID playerId = UUID.fromString(entry.getKey());
                JsonObject periods = entry.getValue().getAsJsonObject();

                Map<RandomQuestPeriod, QuestProgress> periodMap = new EnumMap<>(RandomQuestPeriod.class);

                for (Map.Entry<String, JsonElement> periodEntry : periods.entrySet()) {
                    RandomQuestPeriod period = RandomQuestPeriod.fromId(periodEntry.getKey());
                    if (period == null) continue;

                    QuestProgress progress = GSON.fromJson(periodEntry.getValue(), QuestProgress.class);
                    periodMap.put(period, progress);
                }

                playerProgress.put(playerId, periodMap);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[random-quests] Failed to load progress", e);
        }
    }

    private void saveProgress() {
        Path file = dataDir.resolve("random_quest_progress.json");

        try {
            JsonObject root = new JsonObject();

            for (Map.Entry<UUID, Map<RandomQuestPeriod, QuestProgress>> entry : playerProgress.entrySet()) {
                JsonObject periods = new JsonObject();

                for (Map.Entry<RandomQuestPeriod, QuestProgress> periodEntry : entry.getValue().entrySet()) {
                    periods.add(periodEntry.getKey().getId(), GSON.toJsonTree(periodEntry.getValue()));
                }

                root.add(entry.getKey().toString(), periods);
            }

            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[random-quests] Failed to save progress", e);
        }
    }

    private void loadStreaks() {
        Path file = dataDir.resolve("random_quest_streaks.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID playerId = UUID.fromString(entry.getKey());
                JsonObject periods = entry.getValue().getAsJsonObject();

                Map<RandomQuestPeriod, StreakData> periodMap = new EnumMap<>(RandomQuestPeriod.class);

                for (Map.Entry<String, JsonElement> periodEntry : periods.entrySet()) {
                    RandomQuestPeriod period = RandomQuestPeriod.fromId(periodEntry.getKey());
                    if (period == null) continue;

                    StreakData streak = GSON.fromJson(periodEntry.getValue(), StreakData.class);
                    periodMap.put(period, streak);
                }

                playerStreaks.put(playerId, periodMap);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[random-quests] Failed to load streaks", e);
        }
    }

    private void saveStreaks() {
        Path file = dataDir.resolve("random_quest_streaks.json");

        try {
            JsonObject root = new JsonObject();

            for (Map.Entry<UUID, Map<RandomQuestPeriod, StreakData>> entry : playerStreaks.entrySet()) {
                JsonObject periods = new JsonObject();

                for (Map.Entry<RandomQuestPeriod, StreakData> periodEntry : entry.getValue().entrySet()) {
                    periods.add(periodEntry.getKey().getId(), GSON.toJsonTree(periodEntry.getValue()));
                }

                root.add(entry.getKey().toString(), periods);
            }

            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            EvolutionBoost.LOGGER.error("[random-quests] Failed to save streaks", e);
        }
    }

    // ==================== Inner Classes ====================

    public static class GeneratedQuest {
        public final RandomQuestPeriod period;
        public final String seed;
        public final List<GeneratedObjective> objectives;

        public GeneratedQuest(RandomQuestPeriod period, String seed, List<GeneratedObjective> objectives) {
            this.period = period;
            this.seed = seed;
            this.objectives = objectives;
        }
    }

    public static class GeneratedObjective {
        public final String type;
        public final int amount;
        public final String parameter; // Pokémon-Typ oder Natur

        public GeneratedObjective(String type, int amount, String parameter) {
            this.type = type;
            this.amount = amount;
            this.parameter = parameter;
        }

        /**
         * Gibt eine formatierte Beschreibung zurück.
         */
        public String getDescription() {
            RandomQuestObjectiveType objType = RandomQuestObjectiveType.fromId(type);
            if (objType == null) return type + " x" + amount;

            String format = objType.getDisplayFormat();

            if (objType.requiresPokemonType() && parameter != null) {
                // Capitalize type name
                String typeName = parameter.substring(0, 1).toUpperCase() + parameter.substring(1);
                return String.format(format, amount, typeName);
            } else if (objType.requiresNature() && parameter != null) {
                String natureName = parameter.substring(0, 1).toUpperCase() + parameter.substring(1);
                return String.format(format, amount, natureName);
            } else {
                return String.format(format, amount);
            }
        }
    }

    public static class QuestProgress {
        public String seed;
        public int[] objectiveProgress;
        public boolean completed;

        public QuestProgress() {}

        public QuestProgress(String seed, int objectiveCount) {
            this.seed = seed;
            this.objectiveProgress = new int[objectiveCount];
            this.completed = false;
        }
    }

    public static class StreakData {
        public int currentStreak = 0;
        public String lastCompletionSeed;
    }

    // ==================== Admin Reset ====================

    /** Seed-Suffix für Admin-Resets (erhöht sich bei jedem Reset) */
    private final Map<RandomQuestPeriod, Integer> seedSuffix = new EnumMap<>(RandomQuestPeriod.class);

    /**
     * Setzt die Quest für eine Periode für einen Spieler zurück.
     * Löscht Progress und generiert neue Quest beim nächsten Abruf.
     */
    public void resetPlayerQuest(UUID playerId, RandomQuestPeriod period) {
        Map<RandomQuestPeriod, QuestProgress> periodProgress = playerProgress.get(playerId);
        if (periodProgress != null) {
            periodProgress.remove(period);
        }
        saveProgress();
        EvolutionBoost.LOGGER.info("[random-quests] Reset {} quest for player {}", period.getId(), playerId);
    }

    /**
     * Setzt alle Quests für einen Spieler zurück.
     */
    public void resetPlayerAllQuests(UUID playerId) {
        playerProgress.remove(playerId);
        saveProgress();
        EvolutionBoost.LOGGER.info("[random-quests] Reset all quests for player {}", playerId);
    }

    /**
     * Erzwingt einen globalen Quest-Reroll für eine Periode.
     * Alle Spieler bekommen neue Quests, alter Progress wird gelöscht.
     */
    public void forceGlobalReroll(RandomQuestPeriod period) {
        // Seed-Suffix erhöhen für neuen Seed
        int newSuffix = seedSuffix.getOrDefault(period, 0) + 1;
        seedSuffix.put(period, newSuffix);

        // Quest-Cache leeren
        questCache.entrySet().removeIf(e -> e.getKey().startsWith(period.getId() + ":"));

        // Alle Spieler-Progress für diese Periode löschen
        for (Map<RandomQuestPeriod, QuestProgress> periodProgress : playerProgress.values()) {
            periodProgress.remove(period);
        }

        saveProgress();
        EvolutionBoost.LOGGER.info("[random-quests] Forced global reroll for {} (suffix: {})", 
                period.getId(), newSuffix);
    }

    /**
     * Erzwingt einen globalen Quest-Reroll für alle Perioden.
     */
    public void forceGlobalRerollAll() {
        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            int newSuffix = seedSuffix.getOrDefault(period, 0) + 1;
            seedSuffix.put(period, newSuffix);
        }

        questCache.clear();
        playerProgress.clear();

        saveProgress();
        EvolutionBoost.LOGGER.info("[random-quests] Forced global reroll for ALL periods");
    }

    /**
     * Erzwingt die Fertigstellung einer Random Quest für einen Spieler.
     * Setzt alle Objectives auf Maximum und gibt Rewards.
     */
    public void forceCompleteQuest(ServerPlayer player, RandomQuestPeriod period) {
        UUID playerId = player.getUUID();
        GeneratedQuest quest = getQuest(period);
        QuestProgress progress = getProgress(playerId, period);

        // Alle Objectives auf Maximum setzen
        for (int i = 0; i < quest.objectives.size(); i++) {
            progress.objectiveProgress[i] = quest.objectives.get(i).amount;
        }

        // Quest abgeben (gibt auch Rewards)
        turnInQuest(player, period);

        EvolutionBoost.LOGGER.info("[random-quests] Admin force-completed {} quest for {}", 
                period.getId(), player.getName().getString());
    }

    /**
     * Setzt den Fortschritt für ein bestimmtes Objective.
     * @return true wenn erfolgreich, false wenn Index ungültig
     */
    public boolean setObjectiveProgress(UUID playerId, RandomQuestPeriod period, int objectiveIndex, int amount) {
        GeneratedQuest quest = getQuest(period);
        
        if (objectiveIndex < 0 || objectiveIndex >= quest.objectives.size()) {
            return false;
        }

        QuestProgress progress = getProgress(playerId, period);
        progress.objectiveProgress[objectiveIndex] = amount;
        
        saveProgress();
        EvolutionBoost.LOGGER.info("[random-quests] Admin set {} objective #{} to {} for {}", 
                period.getId(), objectiveIndex, amount, playerId);
        
        return true;
    }

    /**
     * Gibt den Server zurück (für externe Nutzung).
     */
    public MinecraftServer getServer() {
        return server;
    }
}
