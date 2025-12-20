package com.ichezzy.evolutionboost.quest.hooks;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.quest.PlayerQuestData;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.QuestStatus;
import com.ichezzy.evolutionboost.quest.QuestType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hook für Item-bezogene Quest-Objectives.
 * COLLECT_ITEM wird getrackt wenn Items ins Inventar kommen.
 */
public final class QuestItemHook {
    private QuestItemHook() {}

    public static void register() {
        // Fabric hat kein direktes "Item Pickup" Event
        // Wir müssen das über einen Mixin oder periodischen Check lösen
        // Für jetzt: Manuelle Checks oder Mixin nötig

        EvolutionBoost.LOGGER.info("[quests] QuestItemHook registered (requires periodic check or mixin).");
    }

    /**
     * Wird aufgerufen wenn ein Spieler ein Item aufhebt.
     * Muss von einem Mixin aufgerufen werden.
     */
    public static void onItemPickup(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;

        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemIdStr = itemId.toString();
            int amount = stack.getCount();

            QuestManager.get().processProgress(player, QuestType.COLLECT_ITEM,
                    obj -> obj.matchesItem(itemIdStr),
                    amount);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onItemPickup: {}", e.getMessage());
        }
    }

    /**
     * Prüft das Inventar eines Spielers auf Quest-Items.
     * Wird periodisch aufgerufen (alle 2 Sekunden) und bei /eb quest progress.
     */
    public static void checkInventoryForQuests(ServerPlayer player) {
        try {
            QuestManager qm = QuestManager.get();
            PlayerQuestData data = qm.getPlayerData(player);

            // Für jede aktive Quest mit COLLECT_ITEM Objectives
            for (String questId : data.getActiveQuests()) {
                qm.getQuest(questId).ifPresent(quest -> {
                    boolean anyProgressMade = false;
                    
                    for (var obj : quest.getObjectivesByType(QuestType.COLLECT_ITEM)) {
                        String requiredItem = obj.getFilterString("item");
                        if (requiredItem == null) continue;

                        // Zähle Items im Inventar
                        int count = countItemsInInventory(player, requiredItem);
                        int currentProgress = data.getObjectiveProgress(questId, obj.getId());

                        // Setze Fortschritt auf Inventar-Anzahl (falls anders)
                        if (count != currentProgress) {
                            data.setObjectiveProgress(questId, obj.getId(), Math.min(count, obj.getTarget()));
                            
                            // Nachricht wenn Target neu erreicht
                            if (currentProgress < obj.getTarget() && count >= obj.getTarget()) {
                                player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.literal("[" + quest.getName() + "] ")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN)
                                                .append(net.minecraft.network.chat.Component.literal(
                                                                "✓ " + obj.getDescription())
                                                        .withStyle(net.minecraft.ChatFormatting.WHITE))
                                                .append(net.minecraft.network.chat.Component.literal(
                                                                " [" + obj.getTarget() + "/" + obj.getTarget() + "]")
                                                        .withStyle(net.minecraft.ChatFormatting.GREEN))
                                );
                                anyProgressMade = true;
                            }
                        }
                    }
                    
                    // Prüfe ob ALLE Objectives der Quest erfüllt sind
                    if (anyProgressMade && qm.areAllObjectivesComplete(quest, data, questId)) {
                        QuestStatus currentStatus = data.getStatus(questId);
                        if (currentStatus == QuestStatus.ACTIVE) {
                            data.setStatus(questId, QuestStatus.READY_TO_COMPLETE);
                            
                            // Quest-Complete Nachricht
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("══════════════════════════════")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN));
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  ★ QUEST READY ★")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD));
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  " + quest.getName())
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  Return to claim your rewards!")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("══════════════════════════════")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN));
                            
                            qm.savePlayerData(player.getUUID());
                        }
                    }
                });
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in checkInventoryForQuests: {}", e.getMessage());
        }
    }

    /**
     * Zählt wie viele Items eines Typs ein Spieler im Inventar hat.
     */
    public static int countItemsInInventory(Player player, String itemIdStr) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) return 0;

        var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
        if (itemOpt.isEmpty()) return 0;

        var targetItem = itemOpt.get();
        int count = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(targetItem)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Entfernt Items aus dem Inventar (für DELIVER_ITEM).
     * @return true wenn alle Items entfernt werden konnten
     */
    public static boolean removeItemsFromInventory(ServerPlayer player, String itemIdStr, int amount) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) return false;

        var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
        if (itemOpt.isEmpty()) return false;

        var targetItem = itemOpt.get();
        int remaining = amount;

        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(targetItem)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;

                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }

        return remaining == 0;
    }
}
