package com.ichezzy.evolutionboost.item;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.api.callback.PartySelectPokemonDTO;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Super EV Item - Setzt einen Stat auf 252 EVs.
 * 
 * Regeln:
 * - Prüft ob aktuelle Gesamt-EVs + benötigte EVs <= 510
 * - Wenn der Stat bereits EVs hat, werden nur die fehlenden bis 252 hinzugefügt
 * - Wenn die 510 Grenze überschritten würde, fügt nur so viele hinzu wie möglich
 */
public class SuperEVItem extends Item {

    public static final int MAX_STAT_VALUE = 252;
    public static final int MAX_TOTAL_VALUE = 510;

    private final Stat targetStat;
    private final String statDisplayName;
    private final ChatFormatting statColor;

    public SuperEVItem(Stat targetStat, String statDisplayName, ChatFormatting statColor, Properties properties) {
        super(properties);
        this.targetStat = targetStat;
        this.statDisplayName = statDisplayName;
        this.statColor = statColor;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("⚡ Super " + statDisplayName + " Medicine")
                .withStyle(statColor, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Sets " + statDisplayName + " EVs to 252!")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("• Respects 510 total EV limit")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("• Will not exceed maximum EVs")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Right-click on a Pokémon to use!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ItemStack stack = serverPlayer.getItemInHand(hand);

        // Versuche ein Pokémon in der Nähe zu finden, auf das der Spieler schaut
        PokemonEntity targetPokemon = findLookedAtPokemon(serverPlayer);

        if (targetPokemon != null && targetPokemon.getOwnerUUID() != null 
                && targetPokemon.getOwnerUUID().equals(serverPlayer.getUUID())) {
            // Direktes Anwenden auf das angezeigte Pokémon
            Pokemon pokemon = targetPokemon.getPokemon();
            if (canUseOnPokemon(pokemon)) {
                return applyToPokemon(serverPlayer, stack, pokemon);
            } else {
                serverPlayer.sendSystemMessage(Component.literal("Cannot use on this Pokémon!")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
        }

        // Kein Pokémon angezielt - öffne Party-Auswahl
        if (!serverPlayer.isShiftKeyDown()) {
            return openPartySelection(serverPlayer, stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Findet das Pokémon auf das der Spieler schaut.
     */
    private PokemonEntity findLookedAtPokemon(ServerPlayer player) {
        double range = 6.0; // Interaktionsreichweite
        AABB searchBox = AABB.ofSize(player.position(), range, range, range);

        List<Entity> entities = player.level().getEntities(player, searchBox);

        return entities.stream()
                .filter(e -> e instanceof PokemonEntity)
                .map(e -> (PokemonEntity) e)
                .filter(e -> isPlayerLookingAt(player, e))
                .min(Comparator.comparingDouble(e -> e.distanceTo(player)))
                .orElse(null);
    }

    /**
     * Prüft ob der Spieler auf eine Entity schaut.
     */
    private boolean isPlayerLookingAt(ServerPlayer player, Entity entity) {
        var lookVec = player.getLookAngle().normalize();
        var toEntity = entity.position().subtract(player.getEyePosition()).normalize();
        double dot = lookVec.dot(toEntity);
        return dot > 0.95; // ~18° Toleranz
    }

    /**
     * Öffnet die Party-Auswahl GUI.
     */
    private InteractionResultHolder<ItemStack> openPartySelection(ServerPlayer player, ItemStack stack) {
        var partyStore = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<Pokemon> party = new ArrayList<>();
        for (Pokemon pokemon : partyStore) {
            party.add(pokemon);
        }

        if (party.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        List<PartySelectPokemonDTO> dtoList = new ArrayList<>();
        for (Pokemon pk : party) {
            PartySelectPokemonDTO dto = new PartySelectPokemonDTO(pk);
            dto.setEnabled(canUseOnPokemon(pk));
            dtoList.add(dto);
        }

        PartySelectCallbacks.INSTANCE.create(
                player,
                Component.literal("Select Pokémon for Super " + statDisplayName),
                dtoList,
                (p) -> Unit.INSTANCE, // Cancel handler
                (p, index) -> {
                    ItemStack currentStack = p.getMainHandItem();
                    if (currentStack.getItem() == this || p.getOffhandItem().getItem() == this) {
                        ItemStack heldStack = currentStack.getItem() == this ? currentStack : p.getOffhandItem();
                        applyToPokemon(p, heldStack, party.get(index));
                    }
                    return Unit.INSTANCE;
                }
        );

        return InteractionResultHolder.success(stack);
    }

    /**
     * Prüft ob das Item auf ein Pokémon angewendet werden kann.
     */
    public boolean canUseOnPokemon(Pokemon pokemon) {
        EVs evs = pokemon.getEvs();
        int currentStatEV = evs.getOrDefault(targetStat);

        // Kann nicht verwenden wenn Stat bereits bei 252 ist
        if (currentStatEV >= MAX_STAT_VALUE) {
            return false;
        }

        // Kann nicht verwenden wenn keine EVs mehr hinzugefügt werden können
        int currentTotal = calculateTotalEVs(evs);
        return currentTotal < MAX_TOTAL_VALUE;
    }

    /**
     * Wendet das Item auf ein Pokémon an.
     */
    public InteractionResultHolder<ItemStack> applyToPokemon(ServerPlayer player, ItemStack stack, Pokemon pokemon) {
        EVs evs = pokemon.getEvs();

        // Aktuelle Werte berechnen
        int currentStatEV = evs.getOrDefault(targetStat);
        int currentTotalEV = calculateTotalEVs(evs);

        // Wie viele EVs brauchen wir um auf 252 zu kommen?
        int evsNeeded = MAX_STAT_VALUE - currentStatEV;

        // Schon bei 252?
        if (evsNeeded <= 0) {
            player.sendSystemMessage(Component.literal("✗ ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(pokemon.getDisplayName().getString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" already has maximum " + statDisplayName + " EVs!")
                            .withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.fail(stack);
        }

        // Wie viele EVs können wir noch hinzufügen ohne 510 zu überschreiten?
        int availableSpace = MAX_TOTAL_VALUE - currentTotalEV;

        if (availableSpace <= 0) {
            player.sendSystemMessage(Component.literal("✗ ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(pokemon.getDisplayName().getString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" has reached the maximum total EVs (510)!")
                            .withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.fail(stack);
        }

        // Berechne wie viele EVs wir tatsächlich hinzufügen können
        int evsToAdd = Math.min(evsNeeded, availableSpace);
        int newStatValue = currentStatEV + evsToAdd;

        // EVs setzen
        evs.set(targetStat, newStatValue);

        // Erfolgs-Nachricht
        if (newStatValue == MAX_STAT_VALUE) {
            player.sendSystemMessage(Component.literal("✓ ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(pokemon.getDisplayName().getString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("'s " + statDisplayName + " EVs set to 252!")
                            .withStyle(ChatFormatting.GREEN)));
        } else {
            player.sendSystemMessage(Component.literal("✓ ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(pokemon.getDisplayName().getString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("'s " + statDisplayName + " EVs increased to " + newStatValue + "!")
                            .withStyle(ChatFormatting.GREEN)));
            player.sendSystemMessage(Component.literal("  (Limited by total EV cap)")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        player.sendSystemMessage(Component.literal("  (+" + evsToAdd + " EVs)")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        playSuccessSound(player);
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    /**
     * Berechnet die Gesamt-EVs eines Pokémons.
     */
    private int calculateTotalEVs(EVs evs) {
        int total = 0;
        for (Stat stat : Stats.Companion.getPERMANENT()) {
            total += evs.getOrDefault(stat);
        }
        return total;
    }

    private void playSuccessSound(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.5f, 1.2f
        );
    }

    // ==================== Factory Methods ====================

    public static SuperEVItem forHP(Properties properties) {
        return new SuperEVItem(Stats.HP, "HP", ChatFormatting.GREEN, properties);
    }

    public static SuperEVItem forAttack(Properties properties) {
        return new SuperEVItem(Stats.ATTACK, "Attack", ChatFormatting.RED, properties);
    }

    public static SuperEVItem forDefense(Properties properties) {
        return new SuperEVItem(Stats.DEFENCE, "Defense", ChatFormatting.GOLD, properties);
    }

    public static SuperEVItem forSpAttack(Properties properties) {
        return new SuperEVItem(Stats.SPECIAL_ATTACK, "Sp. Atk", ChatFormatting.DARK_PURPLE, properties);
    }

    public static SuperEVItem forSpDefense(Properties properties) {
        return new SuperEVItem(Stats.SPECIAL_DEFENCE, "Sp. Def", ChatFormatting.AQUA, properties);
    }

    public static SuperEVItem forSpeed(Properties properties) {
        return new SuperEVItem(Stats.SPEED, "Speed", ChatFormatting.LIGHT_PURPLE, properties);
    }
}
