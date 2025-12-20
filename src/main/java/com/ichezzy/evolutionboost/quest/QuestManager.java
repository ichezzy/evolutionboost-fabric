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

    /**
     * Berechnet den effektiven Status einer Quest für einen Spieler.
     * Berücksichtigt: gespeicherter Status, Prerequisites, autoActivate, requiresUnlock.
     *
     * Logik:
     * - Wenn ACTIVE/READY/COMPLETED/AVAILABLE → diesen Status zurückgeben
     * - Wenn LOCKED oder nicht gesetzt:
     *   - Wenn requiresUnlock → LOCKED (muss per /eb quest unlock freigeschaltet werden)
     *   - Prüfe Prerequisites
     *   - Wenn erfüllt UND nicht autoActivate → AVAILABLE (Spieler kann starten)
     *   - Wenn erfüllt UND autoActivate → ACTIVE (automatisch starten)
     *   - Wenn nicht erfüllt → LOCKED
     */
    public QuestStatus getEffectiveStatus(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) {
            return QuestStatus.LOCKED;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);
        QuestStatus storedStatus = data.getStatus(questId);

        // Bereits aktiv oder weiter → diesen Status behalten
        if (storedStatus == QuestStatus.ACTIVE ||
            storedStatus == QuestStatus.READY_TO_COMPLETE ||
            storedStatus == QuestStatus.COMPLETED ||
            storedStatus == QuestStatus.AVAILABLE) {
            return storedStatus;
        }

        // LOCKED oder nicht gesetzt
        
        // Wenn Quest requiresUnlock, bleibt sie LOCKED bis explizit freigeschaltet
        if (quest.requiresUnlock()) {
            return QuestStatus.LOCKED;
        }

        // Prüfe Prerequisites
        boolean prereqsMet = true;
        for (String prereq : quest.getPrerequisites()) {
            if (!data.isCompleted(prereq)) {
                prereqsMet = false;
                break;
            }
        }

        if (!prereqsMet) {
            return QuestStatus.LOCKED;
        }

        // Prerequisites erfüllt
        if (quest.isAutoActivate()) {
            // Auto-aktivieren und speichern
            data.setStatus(questId, QuestStatus.ACTIVE);
            savePlayerData(player.getUUID());
            return QuestStatus.ACTIVE;
        } else {
            // Spieler muss selbst starten → AVAILABLE
            return QuestStatus.AVAILABLE;
        }
    }

    /**
     * Holt alle verfügbaren Quests für einen Spieler.
     */
    public Set<String> getAvailableQuests(ServerPlayer player) {
        Set<String> available = new HashSet<>();
        for (Quest quest : quests.values()) {
            QuestStatus status = getEffectiveStatus(player, quest.getFullId());
            if (status == QuestStatus.AVAILABLE) {
                available.add(quest.getFullId());
            }
        }
        return available;
    }

    /**
     * Benachrichtigt einen Spieler über verfügbare Quests.
     * Wird beim Join und wenn neue Quests verfügbar werden aufgerufen.
     */
    public void notifyAvailableQuests(ServerPlayer player) {
        Set<String> available = getAvailableQuests(player);
        
        if (available.isEmpty()) {
            return; // Keine Benachrichtigung wenn nichts verfügbar
        }

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("═══ Quests Available! ═══")
                .withStyle(ChatFormatting.GOLD));

        for (String questId : available) {
            getQuest(questId).ifPresent(quest -> {
                player.sendSystemMessage(Component.literal("  ★ " + quest.getName())
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" - /eb quest start " + quest.getQuestLine() + " " + quest.getId())
                                .withStyle(ChatFormatting.GRAY)));
            });
        }

        player.sendSystemMessage(Component.literal("═════════════════════════")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(""));
    }

    /**
     * Prüft ob ein Spieler nach einer Quest-Aktion neue Quests verfügbar hat.
     * Wird nach completeQuest aufgerufen.
     */
    public void checkAndNotifyNewQuests(ServerPlayer player, Set<String> previouslyAvailable) {
        Set<String> nowAvailable = getAvailableQuests(player);
        
        // Neue Quests = jetzt verfügbar aber vorher nicht
        Set<String> newQuests = new HashSet<>(nowAvailable);
        newQuests.removeAll(previouslyAvailable);
        
        if (newQuests.isEmpty()) {
            return;
        }

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("═══ New Quest Unlocked! ═══")
                .withStyle(ChatFormatting.GREEN));

        for (String questId : newQuests) {
            getQuest(questId).ifPresent(quest -> {
                player.sendSystemMessage(Component.literal("  ★ " + quest.getName())
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" - /eb quest start " + quest.getQuestLine() + " " + quest.getId())
                                .withStyle(ChatFormatting.GRAY)));
            });
        }

        player.sendSystemMessage(Component.literal("═══════════════════════════")
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal(""));
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

    /**
     * Komplettiert eine Quest MIT Item-Entfernung.
     * Entfernt COLLECT_ITEM Items aus dem Inventar und gibt dann Rewards.
     */
    public boolean completeQuestWithItemRemoval(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown quest: " + questId)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);

        // Prüfe ob Quest aktiv oder ready ist
        QuestStatus status = data.getStatus(questId);
        if (status != QuestStatus.ACTIVE && status != QuestStatus.READY_TO_COMPLETE) {
            player.sendSystemMessage(Component.literal("Quest is not active: " + questId)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Prüfe ob alle Objectives erfüllt sind
        if (!areAllObjectivesComplete(quest, data, questId)) {
            player.sendSystemMessage(Component.literal("Quest objectives not complete!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Speichere vorher verfügbare Quests für spätere Benachrichtigung
        Set<String> previouslyAvailable = getAvailableQuests(player);

        // COLLECT_ITEM Items aus dem Inventar entfernen (nur wenn consume=true)
        for (QuestObjective obj : quest.getObjectivesByType(QuestType.COLLECT_ITEM)) {
            String itemId = obj.getFilterString("item");
            if (itemId != null && obj.shouldConsumeItems()) {
                int required = obj.getTarget();
                boolean removed = com.ichezzy.evolutionboost.quest.hooks.QuestItemHook
                        .removeItemsFromInventory(player, itemId, required);
                
                if (!removed) {
                    player.sendSystemMessage(Component.literal("Failed to remove items: " + itemId)
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                
                player.sendSystemMessage(Component.literal("  ✗ Removed " + required + "x " + itemId)
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // Quest als COMPLETED markieren
        data.setStatus(questId, QuestStatus.COMPLETED);

        // Rewards geben
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

        // Benachrichtige über neu verfügbare Quests
        checkAndNotifyNewQuests(player, previouslyAvailable);

        return true;
    }

    /**
     * Admin-Funktion: Quest sofort abschließen ohne Objective-Prüfung.
     * Items werden NICHT aus dem Inventar entfernt.
     * Rewards werden gegeben.
     */
    public boolean forceCompleteQuest(ServerPlayer player, String questId) {
        Optional<Quest> questOpt = getQuest(questId);
        if (questOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown quest: " + questId)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = getPlayerData(player);

        // Speichere vorher verfügbare Quests für spätere Benachrichtigung
        Set<String> previouslyAvailable = getAvailableQuests(player);

        // Quest als COMPLETED markieren (ohne Objective-Prüfung!)
        data.setStatus(questId, QuestStatus.COMPLETED);

        // Rewards geben
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  Quest Completed (Admin): ")
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

        // Benachrichtige über neu verfügbare Quests
        checkAndNotifyNewQuests(player, previouslyAvailable);

        return true;
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

                        // Fortschritts-Nachricht mit Quest-Name
                        if (newProgress >= obj.getTarget()) {
                            // Objective abgeschlossen
                            player.sendSystemMessage(Component.literal("[" + quest.getName() + "] ")
                                    .withStyle(ChatFormatting.GREEN)
                                    .append(Component.literal("✓ " + obj.getDescription() + " ")
                                            .withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal("[" + newProgress + "/" + obj.getTarget() + "]")
                                            .withStyle(ChatFormatting.GREEN)));
                        } else {
                            // Fortschritt
                            player.sendSystemMessage(Component.literal("[" + quest.getName() + "] ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(obj.getDescription() + " ")
                                            .withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal("[" + newProgress + "/" + obj.getTarget() + "]")
                                            .withStyle(ChatFormatting.YELLOW)));
                        }
                    }
                }
            }

            // Prüfe ob Quest jetzt abschlussbereit
            if (anyProgress && areAllObjectivesComplete(quest, data, questId)) {
                data.setStatus(questId, QuestStatus.READY_TO_COMPLETE);
                player.sendSystemMessage(Component.literal("══════════════════════════════")
                        .withStyle(ChatFormatting.GREEN));
                player.sendSystemMessage(Component.literal("  ★ QUEST COMPLETE ★")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("  " + quest.getName())
                        .withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("  Return to claim your rewards!")
                        .withStyle(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.literal("══════════════════════════════")
                        .withStyle(ChatFormatting.GREEN));
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
        // Für jetzt: Hardcoded Quests
        // Später: Aus JSON-Dateien laden
        registerChristmasQuests();
    }

    private void registerChristmasQuests() {
        // ==================== CHRISTMAS 2024 MAIN QUESTS ====================
        // MQ1 benötigt unlock per NPC, MQ2-MQ5 werden durch Prerequisites freigeschaltet

        // MQ1: Chaos in The Toy Factory (benötigt unlock per NPC)
        registerQuest(Quest.builder("christmas", "mq1")
                .name("Chaos in The Toy Factory")
                .description("The toy factory has been corrupted! Defeat the wrapped Pokemon and collect the cursed gifts. Talk to Carol Tinseltoe for details.")
                .category(QuestCategory.MAIN)
                .sortOrder(1)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("defeat_wrapped", QuestType.DEFEAT,
                        "Defeat Wrapped Pokemon", 50,
                        Map.of(
                                "species", List.of("oddish", "gloom", "vileplume", "togepi", "togetic", "togekiss", "stonjourner"),
                                "aspects", List.of("christmas")
                        )))
                .objective(new QuestObjective("collect_black", QuestType.COLLECT_ITEM,
                        "Collect Cursed Gift Black", 25,
                        Map.of("item", "evolutionboost:cursed_gift_black")))
                .objective(new QuestObjective("collect_purple", QuestType.COLLECT_ITEM,
                        "Collect Cursed Gift Purple", 5,
                        Map.of("item", "evolutionboost:cursed_gift_purple")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 5))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:wind_up_key", 1))
                .build());

        // MQ2: Krampus' Curse 1
        registerQuest(Quest.builder("christmas", "mq2")
                .name("Krampus' Curse 1")
                .description("Krampus' minions are spreading darkness! Defeat them and collect the cursed coal. Talk to Skipper for details.")
                .category(QuestCategory.MAIN)
                .sortOrder(2)
                .autoActivate(false)
                .prerequisite("christmas:mq1")
                .objective(new QuestObjective("defeat_krampus", QuestType.DEFEAT,
                        "Defeat Krampus Pokemon", 25,
                        Map.of(
                                "species", List.of("impidimp", "morgrem", "grimmsnarl"),
                                "aspects", List.of("christmas")
                        )))
                .objective(new QuestObjective("collect_coal", QuestType.COLLECT_ITEM,
                        "Collect Cursed Coal", 50,
                        Map.of("item", "evolutionboost:cursed_coal")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 5))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "crdkeys:krampus_raid_key", 1))
                .build());

        // MQ3: Krampus' Curse 2 (Raid Quest)
        registerQuest(Quest.builder("christmas", "mq3")
                .name("Krampus' Curse 2")
                .description("Face Krampus himself and obtain his corrupted heart! Talk to Fizz for details.")
                .category(QuestCategory.MAIN)
                .sortOrder(3)
                .autoActivate(false)
                .prerequisite("christmas:mq2")
                .objective(new QuestObjective("collect_heart", QuestType.COLLECT_ITEM,
                        "Obtain Cursed Coal Heart", 1,
                        Map.of("item", "evolutionboost:cursed_coal_heart")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 5))
                .build());

        // MQ4: Purification of the Heart
        registerQuest(Quest.builder("christmas", "mq4")
                .name("Purification of the Heart")
                .description("Collect holy sparks to purify the darkness and prepare for the final confrontation. Talk to the Christmas Angel for details.")
                .category(QuestCategory.MAIN)
                .sortOrder(4)
                .autoActivate(false)
                .prerequisite("christmas:mq3")
                .objective(new QuestObjective("collect_sparks", QuestType.COLLECT_ITEM,
                        "Collect Holy Spark", 100,
                        Map.of("item", "evolutionboost:holy_spark")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 5))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "crdkeys:ice_queen_raid_key", 1))
                .build());

        // MQ5: Wrath of the Ice Queen (Raid Quest)
        // Ice Crown wird NICHT konsumiert (consume: false)
        registerQuest(Quest.builder("christmas", "mq5")
                .name("Wrath of the Ice Queen")
                .description("Defeat the Ice Queen and claim her crown as proof of your victory! Talk to Frodo for details.")
                .category(QuestCategory.MAIN)
                .sortOrder(5)
                .autoActivate(false)
                .prerequisite("christmas:mq4")
                .objective(new QuestObjective("collect_crown", QuestType.COLLECT_ITEM,
                        "Obtain Ice Crown", 1,
                        Map.of("item", "evolutionboost:ice_crown", "consume", false)))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_gold", 1))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:christmas25_medal", 1))
                .build());

        EvolutionBoost.LOGGER.info("[quests] Registered 5 Christmas main quests.");

        // ==================== CHRISTMAS 2024 SIDE QUESTS ====================
        // Alle Side Quests starten als LOCKED und werden per NPC-Command freigeschaltet

        // SQ1: The Grinch
        registerQuest(Quest.builder("christmas", "sq1")
                .name("The Grinch")
                .description("The Grinch is terrorizing the village! Defeat him to restore Christmas spirit. Talk to Santa for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(10)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("defeat_grinch", QuestType.DEFEAT,
                        "Defeat the Grinch (Infernape)", 1,
                        Map.of(
                                "species", List.of("infernape"),
                                "aspects", List.of("christmas")
                        )))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 3))
                .build());

        // SQ2: The Yeti
        registerQuest(Quest.builder("christmas", "sq2")
                .name("The Yeti")
                .description("A mighty Yeti roams the frozen tundra. Can you defeat this legendary beast? Talk to the Yeti for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(11)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("defeat_yeti", QuestType.DEFEAT,
                        "Defeat the Yeti (Empoleon)", 1,
                        Map.of(
                                "species", List.of("empoleon"),
                                "aspects", List.of("christmas")
                        )))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 3))
                .build());

        // SQ3: The Christmas Tree
        registerQuest(Quest.builder("christmas", "sq3")
                .name("The Christmas Tree")
                .description("The ancient Christmas Tree has awakened! Face this legendary guardian. Talk to Frodo for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(12)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("defeat_tree", QuestType.DEFEAT,
                        "Defeat the Christmas Tree (Torterra)", 1,
                        Map.of(
                                "species", List.of("torterra"),
                                "aspects", List.of("christmas")
                        )))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 3))
                .build());

        // SQ4a: Blizzy's Blissful Bakery - Part 1 (Collect) - benötigt unlock per NPC
        registerQuest(Quest.builder("christmas", "sq4a")
                .name("Blizzy's Blissful Bakery")
                .description("The Gingerbread Man needs ingredients for his famous treats! Talk to the Gingerbread Man for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(13)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("collect_candy", QuestType.COLLECT_ITEM,
                        "Collect Christmas Candy", 3,
                        Map.of("item", "evolutionboost:christmas_candy")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:candy_cane", 1))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 3))
                .build());

        // SQ4b: Blizzy's Blissful Bakery - Part 2 (Defeat) - wird automatisch nach SQ4a verfügbar
        registerQuest(Quest.builder("christmas", "sq4b")
                .name("Blizzy's Blissful Bakery 2")
                .description("A wild Tinkaton is causing chaos in the bakery! Defeat it to save the gingerbread house. Talk to the Gingerbread Man for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(14)
                .autoActivate(false)
                .prerequisite("christmas:sq4a")
                .objective(new QuestObjective("defeat_tinkaton", QuestType.DEFEAT,
                        "Defeat the Bakery Menace (Tinkaton)", 1,
                        Map.of(
                                "species", List.of("tinkaton"),
                                "aspects", List.of("christmas")
                        )))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:evolution_coin_silver", 3))
                .build());

        // SQ5: Keeper of the Frozen Lake - benötigt unlock per NPC
        registerQuest(Quest.builder("christmas", "sq5")
                .name("Keeper of the Frozen Lake")
                .description("Gather the mystical Spirit Dew Shards scattered across the frozen lake. Talk to Skipper for details.")
                .category(QuestCategory.SIDE)
                .sortOrder(15)
                .autoActivate(false)
                .requiresUnlock(true)
                .objective(new QuestObjective("collect_shards", QuestType.COLLECT_ITEM,
                        "Collect Spirit Dew Shards", 9,
                        Map.of("item", "evolutionboost:spirit_dew_shards")))
                .reward(new QuestReward(QuestReward.RewardType.ITEM, "evolutionboost:spirit_dew", 1))
                .build());

        EvolutionBoost.LOGGER.info("[quests] Registered 7 Christmas side quests.");
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
