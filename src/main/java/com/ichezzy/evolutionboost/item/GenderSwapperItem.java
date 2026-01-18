package com.ichezzy.evolutionboost.item;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Gender;
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
 * Gender Swapper - switches Pokémon gender between Male and Female.
 * Does not work on genderless Pokémon.
 */
public class GenderSwapperItem extends Item {
    
    public GenderSwapperItem(Properties properties) {
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
        Gender currentGender = pokemon.getGender();
        
        // Check if genderless
        if (currentGender == Gender.GENDERLESS) {
            serverPlayer.sendSystemMessage(Component.literal("✗ " + pokemonName + " is genderless and cannot change gender!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        
        // Check species gender ratio - some can only be one gender
        float maleRatio = pokemon.getSpecies().getMaleRatio();
        if (maleRatio <= 0f) {
            // Female only species
            serverPlayer.sendSystemMessage(Component.literal("✗ " + pokemonName + " can only be female!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (maleRatio >= 1f) {
            // Male only species
            serverPlayer.sendSystemMessage(Component.literal("✗ " + pokemonName + " can only be male!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        
        // Toggle gender
        Gender newGender = (currentGender == Gender.MALE) ? Gender.FEMALE : Gender.MALE;
        pokemon.setGender(newGender);
        
        String genderSymbol = (newGender == Gender.MALE) ? "♂" : "♀";
        ChatFormatting color = (newGender == Gender.MALE) ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE;
        
        serverPlayer.sendSystemMessage(Component.literal("✓ " + pokemonName + " is now " + genderSymbol + " " + newGender.name().toLowerCase())
                .withStyle(color));
        
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Swaps Pokémon gender between ♂ and ♀").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Does not work on genderless species").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Right-click on your Pokémon to use").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
