package com.ichezzy.evolutionboost.item;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Caught Ball Swapper - changes the Pokéball a Pokémon was caught in.
 * Uses the Pokéball held in the off-hand to determine the new ball type.
 */
public class CaughtBallSwapperItem extends Item {
    
    public CaughtBallSwapperItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return InteractionResult.PASS;
        }
        
        Pokemon pokemon = pokemonEntity.getPokemon();
        
        // Check ownership
        if (pokemon.getOwnerUUID() == null || !pokemon.getOwnerUUID().equals(player.getUUID())) {
            serverPlayer.sendSystemMessage(Component.literal("✗ This is not your Pokémon!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        
        // Get the ball from off-hand
        ItemStack offHandStack = player.getOffhandItem();
        if (offHandStack.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("✗ Hold a Poké Ball in your off-hand!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        
        // Try to find the PokeBall from the item
        PokeBall newBall = findPokeBallFromItem(offHandStack);
        if (newBall == null) {
            serverPlayer.sendSystemMessage(Component.literal("✗ That's not a valid Poké Ball!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        
        String pokemonName = pokemon.getDisplayName().getString();
        PokeBall oldBall = pokemon.getCaughtBall();
        
        // Check if same ball
        if (oldBall.getName().equals(newBall.getName())) {
            serverPlayer.sendSystemMessage(Component.literal("✗ " + pokemonName + " is already in a " + formatBallName(newBall) + "!")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        
        // Change the ball
        pokemon.setCaughtBall(newBall);
        
        serverPlayer.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s Poké Ball changed from ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(formatBallName(oldBall)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(formatBallName(newBall)).withStyle(ChatFormatting.AQUA)));
        
        // Consume the swapper and the ball
        stack.shrink(1);
        offHandStack.shrink(1);
        
        return InteractionResult.SUCCESS;
    }
    
    /**
     * Tries to find a PokeBall from an ItemStack.
     * Checks if the item is registered as a Cobblemon PokeBall.
     */
    private PokeBall findPokeBallFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        
        // Get the item's registry name
        var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return null;
        
        String namespace = itemKey.getNamespace();
        String path = itemKey.getPath();
        
        // Cobblemon balls are in the cobblemon namespace
        if (!"cobblemon".equals(namespace)) return null;
        
        // Try to find the ball by name
        // Cobblemon ball items are named like "poke_ball", "great_ball", etc.
        for (PokeBall ball : PokeBalls.INSTANCE.all()) {
            String ballName = ball.getName().getPath();
            if (ballName.equals(path)) {
                return ball;
            }
        }
        
        return null;
    }
    
    private String formatBallName(PokeBall ball) {
        String name = ball.getName().getPath();
        // Convert snake_case to Title Case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Changes a Pokémon's Poké Ball").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Hold desired ball in off-hand").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Right-click on your Pokémon to use").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
