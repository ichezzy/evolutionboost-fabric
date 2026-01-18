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
 * EV Reset Item - Setzt alle EVs eines Pokémons auf 0.
 * 
 * Nützlich um EVs komplett neu zu verteilen.
 */
public class EVResetItem extends Item {

    public EVResetItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("🔄 Reset Medicine")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Resets ALL EVs to 0!")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("• HP, Attack, Defense")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("• Sp. Atk, Sp. Def, Speed")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("⚠ This cannot be undone!")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
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
                serverPlayer.sendSystemMessage(Component.literal("This Pokémon has no EVs to reset!")
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
                Component.literal("Select Pokémon for EV Reset"),
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
        // Kann nur verwendet werden wenn das Pokémon überhaupt EVs hat
        EVs evs = pokemon.getEvs();
        return calculateTotalEVs(evs) > 0;
    }

    /**
     * Wendet das Item auf ein Pokémon an.
     */
    public InteractionResultHolder<ItemStack> applyToPokemon(ServerPlayer player, ItemStack stack, Pokemon pokemon) {
        EVs evs = pokemon.getEvs();
        int totalBefore = calculateTotalEVs(evs);

        // Keine EVs zum Zurücksetzen?
        if (totalBefore == 0) {
            player.sendSystemMessage(Component.literal("✗ ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(pokemon.getDisplayName().getString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" has no EVs to reset!")
                            .withStyle(ChatFormatting.RED)));
            return InteractionResultHolder.fail(stack);
        }

        // Alle EVs auf 0 setzen
        for (Stat stat : Stats.Companion.getPERMANENT()) {
            evs.set(stat, 0);
        }

        // Erfolgs-Nachricht
        player.sendSystemMessage(Component.literal("✓ ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(pokemon.getDisplayName().getString())
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s EVs have been reset!")
                        .withStyle(ChatFormatting.GREEN)));
        player.sendSystemMessage(Component.literal("  (" + totalBefore + " EVs removed)")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        playResetSound(player);
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

    private void playResetSound(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.5f, 0.8f // Tieferer Ton für Reset
        );
    }
}
