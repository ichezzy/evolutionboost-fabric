package com.ichezzy.evolutionboost.quest.hooks;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.QuestType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
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
     * Kann periodisch aufgerufen werden als Alternative zum Pickup-Hook.
     */
    public static void checkInventoryForQuests(ServerPlayer player) {
        try {
            QuestManager qm = QuestManager.get();

            // Für jede aktive Quest mit COLLECT_ITEM Objectives
            for (String questId : qm.getPlayerData(player).getActiveQuests()) {
                qm.getQuest(questId).ifPresent(quest -> {
                    for (var obj : quest.getObjectivesByType(QuestType.COLLECT_ITEM)) {
                        String requiredItem = obj.getFilterString("item");
                        if (requiredItem == null) continue;

                        // Zähle Items im Inventar
                        int count = countItemsInInventory(player, requiredItem);
                        int currentProgress = qm.getPlayerData(player).getObjectiveProgress(questId, obj.getId());

                        // Setze Fortschritt auf Inventar-Anzahl (falls höher)
                        if (count > currentProgress) {
                            qm.getPlayerData(player).setObjectiveProgress(questId, obj.getId(), count);

                            // Nachricht wenn Target erreicht
                            if (currentProgress < obj.getTarget() && count >= obj.getTarget()) {
                                player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.literal("[Quest] ")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN)
                                                .append(net.minecraft.network.chat.Component.literal(
                                                                "Objective complete: " + obj.getDescription())
                                                        .withStyle(net.minecraft.ChatFormatting.WHITE))
                                );
                            }
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
