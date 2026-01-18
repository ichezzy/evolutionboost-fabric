package com.ichezzy.evolutionboost.item;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
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
 * Shiny Swapper - toggles shiny status of a Pokémon.
 */
public class ShinySwapperItem extends Item {
    
    public ShinySwapperItem(Properties properties) {
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
        
        String pokemonName = pokemon.getDisplayName().getString();
        boolean wasShiny = pokemon.getShiny();
        
        // Toggle shiny
        pokemon.setShiny(!wasShiny);
        
        if (wasShiny) {
            serverPlayer.sendSystemMessage(Component.literal("✓ " + pokemonName + " is no longer shiny")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            serverPlayer.sendSystemMessage(Component.literal("✓ " + pokemonName + " is now ✨ SHINY ✨!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Toggles shiny status of a Pokémon").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click on your Pokémon to use").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
