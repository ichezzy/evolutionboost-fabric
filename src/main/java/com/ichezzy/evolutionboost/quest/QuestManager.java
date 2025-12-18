package com.ichezzy.evolutionboost.quest;

import com.google.gson.*;
import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentraler Manager für das Quest-System.
 * Singleton - wird beim Server-Start initialisiert.
 */
public final class QuestManager {
    private static QuestManager INSTANCE;

    private MinecraftServer server;

    // Quest-Definitionen: fullId -> Quest
    private final Map<String, Quest> quests = new ConcurrentHashMap<>();

    // Questlines: questLine -> List<Quest> (sortiert)
    private final Map<String, List<Quest>> questLines = new ConcurrentHashMap<>();

    // Spieler-Fortschritt: UUID -> PlayerQuestData
    private final Map<UUID, PlayerQuestData> playerData = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private QuestManager() {}

    public static QuestManager get() {
        if (INSTANCE == null) {
            INSTANCE = new QuestManager();
        }
        return INSTANCE;
    }

    // ==================== Lifecycle ====================

    public void init(MinecraftServer server) {
        this.server = server;
        loadQuests();
        loadAllPlayerData();
        EvolutionBoost.LOGGER.info("[quests] QuestManager initialized with {} quests.", quests.size());
    }

    public void shutdown() {
        saveAllPlayerData();
        EvolutionBoost.LOGGER.info("[quests] QuestManager shutdown, saved {} player progress files.", playerData.size());
    }

    // ==================== Quest Registration ====================

    /**
     * Registriert eine Quest.
     */
    public void registerQuest(Quest quest) {
        quests.put(quest.getFullId(), quest);

        // Zur Questline hinzufügen
        questLines.computeIfAbsent(quest.getQuestLine(), k -> new ArrayList<>()).add(quest);

        // Questline sortieren
        questLines.get(quest.getQuestLine()).sort(Comparator.comparingInt(Quest::getSortOrder));
    }

    /**
     * Holt eine Quest nach ID.
     */
    public Optional<Quest> getQuest(String fullId) {
        return Optional.ofNullable(quests.get(fullId));
    }

    /**
     * Holt alle Quests einer Questline.
     */
    public List<Quest> getQuestLine(String questLine) {
        return questLines.getOrDefault(questLine, Collections.emptyList());
    }

    /**
     * Holt alle registrierten Questlines.
     */
    public Set<String> getQuestLines() {
        return Collections.unmodifiableSet(questLines.keySet());
    }

    /**
     * Holt alle registrierten Quests.
     */
    public Collection<Quest> getAllQuests() {
        return Collections.unmodifiableCollection(quests.values());
    }

    // ==================== Player Data ====================

    /**
     * Holt oder erstellt Spieler-Daten.
     */
    public PlayerQuestData getPlayerData(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(),
                uuid -> new PlayerQuestData(uuid, player.getName().getString()));
    }

    /**
     * Holt Spieler-Daten nach UUID (kann null sein wenn nie online).
     */
    public PlayerQuestData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    // ==================== Quest Status Management ====================

    /**
     * Aktiviert eine Quest für einen Spieler.
     * @return true wenn erfolgreich aktiviert
     */
    public boolean activateQuest(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Quest] Unknown quest: " + questId)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);

        // Prüfe ob bereits aktiv oder abgeschlossen
        QuestStatus currentStatus = data.getStatus(questId);
        if (currentStatus == QuestStatus.ACTIVE) {
            player.sendSystemMessage(Component.literal("[Quest] Quest already active!")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (currentStatus == QuestStatus.COMPLETED) {
            player.sendSystemMessage(Component.literal("[Quest] Quest already completed!")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        // Prüfe Prerequisites
        for (String prereq : quest.getPrerequisites()) {
            if (!data.isCompleted(prereq)) {
                player.sendSystemMessage(Component.literal("[Quest] You must complete '" + prereq + "' first!")
                        .withStyle(ChatFormatting.RED));
                return false;
            }
        }

        // Aktivieren
        data.setStatus(questId, QuestStatus.ACTIVE);

        // Benachrichtigung
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  Quest Started: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(quest.getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.literal("  " + quest.getDescription())
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD));

        savePlayerData(player.getUUID());
        return true;
    }

    /**
     * Schließt eine Quest für einen Spieler ab und gibt Rewards.
     * @return true wenn erfolgreich
     */
    public boolean completeQuest(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) return false;

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);

        // Prüfe ob alle Objectives erfüllt
        if (!areAllObjectivesComplete(quest, data, questId)) {
            player.sendSystemMessage(Component.literal("[Quest] Not all objectives completed!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Status setzen
        data.setStatus(questId, QuestStatus.COMPLETED);

        // Rewards vergeben
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  Quest Completed: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(quest.getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.literal("  Rewards:").withStyle(ChatFormatting.AQUA));

        for (QuestReward reward : quest.getRewards()) {
            if (reward.grantTo(player)) {
                player.sendSystemMessage(Component.literal("    • " + reward.getDisplayText())
                        .withStyle(ChatFormatting.WHITE));
            }
        }

        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD));

        savePlayerData(player.getUUID());

        // Auto-aktiviere nächste Quest wenn sequentiell
        autoActivateNextQuest(player, quest);

        return true;
    }

    /**
     * Prüft ob alle Objectives einer Quest erfüllt sind.
     */
    public boolean areAllObjectivesComplete(Quest quest, PlayerQuestData data, String questId) {
        for (QuestObjective obj : quest.getObjectives()) {
            int progress = data.getObjectiveProgress(questId, obj.getId());
            if (progress < obj.getTarget()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Auto-aktiviert die nächste sequentielle Quest.
     */
    private void autoActivateNextQuest(ServerPlayer player, Quest completedQuest) {
        if (!completedQuest.getCategory().isSequential()) return;

        List<Quest> line = getQuestLine(completedQuest.getQuestLine());
        int idx = line.indexOf(completedQuest);
        if (idx < 0 || idx >= line.size() - 1) return;

        Quest next = line.get(idx + 1);
        if (next.isAutoActivate()) {
            activateQuest(player, next.getFullId());
        }
    }

    /**
     * Setzt eine Quest zurück.
     */
    public void resetQuest(ServerPlayer player, String questId) {
        PlayerQuestData data = getPlayerData(player);
        data.resetQuest(questId);
        savePlayerData(player.getUUID());
    }

    // ==================== Progress Tracking ====================

    /**
     * Verarbeitet Fortschritt für ein bestimmtes Objective.
     * Wird von Hooks aufgerufen.
     *
     * @param player Der Spieler
     * @param type Der Quest-Typ
     * @param matchFn Funktion die prüft ob ein Objective matched
     * @param amount Fortschritt (meist 1)
     */
    public void processProgress(ServerPlayer player, QuestType type,
                                java.util.function.Predicate<QuestObjective> matchFn, int amount) {
        PlayerQuestData data = getPlayerData(player);

        for (String questId : data.getActiveQuests()) {
            Optional<Quest> questOpt = getQuest(questId);
            if (questOpt.isEmpty()) continue;

            Quest quest = questOpt.get();
            boolean anyProgress = false;

            for (QuestObjective obj : quest.getObjectivesByType(type)) {
                if (matchFn.test(obj)) {
                    int current = data.getObjectiveProgress(questId, obj.getId());
                    if (current < obj.getTarget()) {
                        int newProgress = data.incrementObjectiveProgress(questId, obj.getId(), amount);
                        anyProgress = true;

                        // Fortschritts-Nachricht
                        if (newProgress >= obj.getTarget()) {
                            player.sendSystemMessage(Component.literal("[Quest] ")
                                    .withStyle(ChatFormatting.GREEN)
                                    .append(Component.literal("Objective complete: " + obj.getDescription())
                                            .withStyle(ChatFormatting.WHITE)));
                        } else {
                            player.sendSystemMessage(Component.literal("[Quest] ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(obj.getDescription() + ": " + newProgress + "/" + obj.getTarget())
                                            .withStyle(ChatFormatting.WHITE)));
                        }
                    }
                }
            }

            // Prüfe ob Quest jetzt abschlussbereit
            if (anyProgress && areAllObjectivesComplete(quest, data, questId)) {
                data.setStatus(questId, QuestStatus.READY_TO_COMPLETE);
                player.sendSystemMessage(Component.literal("[Quest] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Quest ready to turn in: " + quest.getName())
                                .withStyle(ChatFormatting.YELLOW)));
            }
        }

        if (player.tickCount % 100 == 0) { // Nicht bei jedem Progress speichern
            savePlayerData(player.getUUID());
        }
    }

    /**
     * Manueller Fortschritt für CUSTOM Quest-Typ.
     */
    public void addCustomProgress(ServerPlayer player, String questId, String objectiveId, int amount) {
        PlayerQuestData data = getPlayerData(player);
        if (!data.isActive(questId)) return;

        data.incrementObjectiveProgress(questId, objectiveId, amount);
        savePlayerData(player.getUUID());
    }

    // ==================== Persistence ====================

    private Path getQuestsDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EvolutionBoost.MOD_ID)
                .resolve("quests");
    }

    private Path getPlayerDataDir() {
        if (server == null) return getQuestsDir().resolve("players");
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("evolutionboost")
                .resolve("quest_progress");
    }

    private void loadQuests() {
        // Für jetzt: Hardcoded Christmas Quests
        // Später: Aus JSON-Dateien laden
        registerChristmasQuests();
    }

    private void registerChristmasQuests() {
        // MQ1: The Frozen Guardians
        registerQuest(Quest.builder("christmas", "mq1")
                .name("The Frozen Guardians")
                .description("Defeat the corrupted Christmas Pokemon and collect cursed gifts.")
                .category(QuestCategory.MAIN)
                .sortOrder(1)
                .autoActivate(false)
                .objective(new QuestObjective("defeat_pokemon", QuestType.DEFEAT,
                        "Defeat Christmas Pokemon", 50,
                        Map.of(
                                "species", List.of("stonjourner", "oddish", "gloom", "vileplume", "togepi", "togetic", "togekiss"),
                                "aspects", List.of("christmas")
                        )))
                .objective(new QuestObjective("collect_purple", QuestType.COLLECT_ITEM,
                        "Collect Purple Presents", 25,
                        Map.of("item", "evolutionboost:cursed_gift_purple")))
                .objective(new QuestObjective("collect_black", QuestType.COLLECT_ITEM,
                        "Collect Black Presents", 5,
                        Map.of("item", "evolutionboost:cursed_gift_black")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:holy_spark", 100))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:christmas_loot_sack", 5))
                .reward(new QuestReward(QuestReward.RewardType.XP, "", 500))
                .build());

        // Placeholder für weitere Quests
        // TODO: MQ2, MQ3, etc.
    }

    public void loadAllPlayerData() {
        try {
            Path dir = getPlayerDataDir();
            if (!Files.exists(dir)) return;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = GSON.fromJson(reader, Map.class);
                        if (map != null) {
                            PlayerQuestData data = PlayerQuestData.fromMap(map);
                            playerData.put(data.getPlayerId(), data);
                        }
                    } catch (Exception e) {
                        EvolutionBoost.LOGGER.warn("[quests] Failed to load player data: {}", file.getFileName(), e);
                    }
                }
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[quests] Failed to load player data directory", e);
        }
    }

    public void saveAllPlayerData() {
        for (UUID playerId : playerData.keySet()) {
            savePlayerData(playerId);
        }
    }

    public void savePlayerData(UUID playerId) {
        PlayerQuestData data = playerData.get(playerId);
        if (data == null) return;

        try {
            Path dir = getPlayerDataDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(playerId.toString() + ".json");

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data.toMap(), writer);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[quests] Failed to save player data for {}", playerId, e);
        }
    }

    // ==================== Info Display ====================

    /**
     * Zeigt Quest-Info für einen Spieler.
     */
    public void showQuestInfo(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown quest: " + questId).withStyle(ChatFormatting.RED));
            return;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);
        QuestStatus status = data.getStatus(questId);

        player.sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(quest.getName())
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(quest.getDescription()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Status: ")
                .append(Component.literal(status.name()).withStyle(getStatusColor(status))));
        player.sendSystemMessage(Component.literal("───────────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("Objectives:").withStyle(ChatFormatting.AQUA));

        for (QuestObjective obj : quest.getObjectives()) {
            int progress = data.getObjectiveProgress(questId, obj.getId());
            boolean complete = progress >= obj.getTarget();
            ChatFormatting color = complete ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            String check = complete ? "✓" : "○";

            player.sendSystemMessage(Component.literal("  " + check + " " + obj.getDescription() + ": ")
                    .withStyle(color)
                    .append(Component.literal(progress + "/" + obj.getTarget())
                            .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        }

        player.sendSystemMessage(Component.literal("───────────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("Rewards:").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (QuestReward reward : quest.getRewards()) {
            player.sendSystemMessage(Component.literal("  • " + reward.getDisplayText()).withStyle(ChatFormatting.WHITE));
        }

        player.sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
    }

    private ChatFormatting getStatusColor(QuestStatus status) {
        return switch (status) {
            case LOCKED -> ChatFormatting.DARK_GRAY;
            case AVAILABLE -> ChatFormatting.WHITE;
            case ACTIVE -> ChatFormatting.YELLOW;
            case READY_TO_COMPLETE -> ChatFormatting.GOLD;
            case COMPLETED -> ChatFormatting.GREEN;
        };
    }
}
