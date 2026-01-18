package com.ichezzy.evolutionboost.item;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bottle Cap - sets Pokemon IVs to 31.
 * 
 * Variants:
 * - null stat = random single IV (Silver Bottle Cap)
 * - specific stat = that IV only
 * - ALL flag = all IVs (Gold Bottle Cap)
 * - Copper = random value (not max)
 */
public class BottleCapItem extends Item {
    
    private static final Random RANDOM = new Random();
    
    public enum CapType {
        SILVER,     // Random single IV to 31
        GOLD,       // All IVs to 31
        COPPER,     // Random single IV to random value
        VOID,       // All IVs to 0
        HP,         // Specific stat to 31
        ATK,
        DEF,
        SPATK,
        SPDEF,
        SPEED
    }
    
    private final CapType capType;
    private final String tooltipKey;
    
    public BottleCapItem(Properties properties, CapType capType, String tooltipKey) {
        super(properties);
        this.capType = capType;
        this.tooltipKey = tooltipKey;
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
        
        boolean success = applyBottleCap(pokemon, serverPlayer);
        
        if (success) {
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        
        return InteractionResult.FAIL;
    }
    
    private boolean applyBottleCap(Pokemon pokemon, ServerPlayer player) {
        var ivs = pokemon.getIvs();
        String pokemonName = pokemon.getDisplayName().getString();
        
        switch (capType) {
            case GOLD -> {
                // Check if already maxed
                boolean allMax = true;
                for (Stat stat : getIVStats()) {
                    Integer current = ivs.get(stat);
                    if (current == null || current < 31) {
                        allMax = false;
                        break;
                    }
                }
                if (allMax) {
                    player.sendSystemMessage(Component.literal("✗ " + pokemonName + " already has perfect IVs!")
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                
                // Set all to 31
                for (Stat stat : getIVStats()) {
                    ivs.set(stat, 31);
                }
                player.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s IVs are now perfect!")
                        .withStyle(ChatFormatting.GOLD));
                return true;
            }
            
            case VOID -> {
                // Check if already all zero
                boolean allZero = true;
                for (Stat stat : getIVStats()) {
                    Integer current = ivs.get(stat);
                    if (current == null || current > 0) {
                        allZero = false;
                        break;
                    }
                }
                if (allZero) {
                    player.sendSystemMessage(Component.literal("✗ " + pokemonName + " already has zero IVs!")
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                
                // Set all to 0
                for (Stat stat : getIVStats()) {
                    ivs.set(stat, 0);
                }
                player.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s IVs are now all zero!")
                        .withStyle(ChatFormatting.DARK_PURPLE));
                return true;
            }
            
            case SILVER -> {
                // Find stats that aren't 31
                List<Stat> improvable = new ArrayList<>();
                for (Stat stat : getIVStats()) {
                    Integer current = ivs.get(stat);
                    if (current == null || current < 31) {
                        improvable.add(stat);
                    }
                }
                if (improvable.isEmpty()) {
                    player.sendSystemMessage(Component.literal("✗ " + pokemonName + " already has perfect IVs!")
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                
                Stat chosen = improvable.get(RANDOM.nextInt(improvable.size()));
                ivs.set(chosen, 31);
                player.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s " + getStatName(chosen) + " IV is now 31!")
                        .withStyle(ChatFormatting.GREEN));
                return true;
            }
            
            case COPPER -> {
                // Random stat to random value
                Stat chosen = getIVStats()[RANDOM.nextInt(6)];
                int newValue = RANDOM.nextInt(32); // 0-31
                Integer oldValue = ivs.get(chosen);
                ivs.set(chosen, newValue);
                player.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s " + getStatName(chosen) + 
                        " IV changed from " + (oldValue != null ? oldValue : 0) + " to " + newValue)
                        .withStyle(ChatFormatting.YELLOW));
                return true;
            }
            
            default -> {
                // Specific stat
                Stat stat = getStatForCapType();
                if (stat == null) return false;
                
                Integer current = ivs.get(stat);
                if (current != null && current >= 31) {
                    player.sendSystemMessage(Component.literal("✗ " + pokemonName + "'s " + getStatName(stat) + " IV is already 31!")
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                
                ivs.set(stat, 31);
                player.sendSystemMessage(Component.literal("✓ " + pokemonName + "'s " + getStatName(stat) + " IV is now 31!")
                        .withStyle(ChatFormatting.GREEN));
                return true;
            }
        }
    }
    
    private Stat getStatForCapType() {
        return switch (capType) {
            case HP -> Stats.HP;
            case ATK -> Stats.ATTACK;
            case DEF -> Stats.DEFENCE;
            case SPATK -> Stats.SPECIAL_ATTACK;
            case SPDEF -> Stats.SPECIAL_DEFENCE;
            case SPEED -> Stats.SPEED;
            default -> null;
        };
    }
    
    private static Stat[] getIVStats() {
        return new Stat[] {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
        };
    }
    
    private static String getStatName(Stat stat) {
        if (stat == Stats.HP) return "HP";
        if (stat == Stats.ATTACK) return "Attack";
        if (stat == Stats.DEFENCE) return "Defense";
        if (stat == Stats.SPECIAL_ATTACK) return "Sp. Atk";
        if (stat == Stats.SPECIAL_DEFENCE) return "Sp. Def";
        if (stat == Stats.SPEED) return "Speed";
        return stat.toString();
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click on your Pokémon to use").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
    
    // Factory methods
    public static BottleCapItem silver(Properties props) {
        return new BottleCapItem(props, CapType.SILVER, "tooltip.evolutionboost.bottle_cap_silver");
    }
    
    public static BottleCapItem gold(Properties props) {
        return new BottleCapItem(props, CapType.GOLD, "tooltip.evolutionboost.bottle_cap_gold");
    }
    
    public static BottleCapItem copper(Properties props) {
        return new BottleCapItem(props, CapType.COPPER, "tooltip.evolutionboost.bottle_cap_copper");
    }
    
    public static BottleCapItem voidCap(Properties props) {
        return new BottleCapItem(props, CapType.VOID, "tooltip.evolutionboost.bottle_cap_void");
    }
    
    public static BottleCapItem forStat(Properties props, CapType type, String tooltipKey) {
        return new BottleCapItem(props, type, tooltipKey);
    }
}
