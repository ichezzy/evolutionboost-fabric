package com.ichezzy.evolutionboost.block;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.particles.ParticleTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Spirit Altar - Rechtsklick mit Spirit Dew spawnt ein legendäres Pokemon.
 * 
 * Cooldown ist PRO ALTAR + PRO SPIELER:
 * - Spieler A benutzt Altar bei (100, 65, 200) -> 24h Cooldown für diesen Altar
 * - Spieler A kann immer noch Altar bei (500, 70, 300) benutzen
 * - Spieler B kann Altar bei (100, 65, 200) benutzen (hat eigenen Cooldown)
 */
public class SpiritAltarBlock extends Block {
    
    // Cooldown in Millisekunden (Standard: 24 Stunden)
    private static final long COOLDOWN_MS = 24 * 60 * 60 * 1000;
    
    // Cooldown-Tracking: Key = AltarKey (Dimension + Position + Spieler-UUID)
    private static final Map<AltarKey, Long> altarCooldowns = new HashMap<>();
    
    // Pokemon-Spawn-Befehl
    private static final String SPAWN_COMMAND = "pokespawn suicune christmas level=100 shiny=yes";
    
    public SpiritAltarBlock(Properties properties) {
        super(properties);
    }
    
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, 
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        
        // Nur Server-seitig
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        
        // Prüfe ob Spirit Dew in der Hand
        if (!stack.is(ModItems.SPIRIT_DEW)) {
            player.displayClientMessage(
                Component.literal("The altar awaits an offering of Spirit Dew...")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), 
                true
            );
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) level;
        
        // Key für diesen Altar + Spieler erstellen
        AltarKey key = new AltarKey(level.dimension(), pos, player.getUUID());
        
        // Cooldown prüfen
        long now = System.currentTimeMillis();
        Long lastUse = altarCooldowns.get(key);
        
        if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
            long remaining = COOLDOWN_MS - (now - lastUse);
            String timeStr = formatTime(remaining);
            player.displayClientMessage(
                Component.literal("This altar's power is still recovering for you... (" + timeStr + ")")
                    .withStyle(ChatFormatting.RED), 
                true
            );
            return ItemInteractionResult.FAIL;
        }
        
        // Spirit Dew konsumieren
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        
        // Cooldown setzen
        altarCooldowns.put(key, now);
        
        // Effekte
        playActivationEffects(serverLevel, pos);
        
        // Nachricht
        serverPlayer.sendSystemMessage(
            Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.AQUA)
        );
        serverPlayer.sendSystemMessage(
            Component.literal("  The Spirit Dew glows brilliantly...")
                .withStyle(ChatFormatting.WHITE)
        );
        serverPlayer.sendSystemMessage(
            Component.literal("  A legendary presence approaches!")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
        );
        serverPlayer.sendSystemMessage(
            Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.AQUA)
        );
        
        // Pokemon spawnen via Command
        try {
            String command = SPAWN_COMMAND;
            serverLevel.getServer().getCommands().performPrefixedCommand(
                serverLevel.getServer().createCommandSourceStack()
                    .withPosition(pos.above(2).getCenter())
                    .withLevel(serverLevel)
                    .withSuppressedOutput(),
                command
            );
            
            EvolutionBoost.LOGGER.info("[SpiritAltar] {} activated altar at {} in {}, spawned Pokemon", 
                player.getName().getString(), pos, level.dimension().location());
                
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[SpiritAltar] Failed to spawn Pokemon: {}", e.getMessage());
            player.displayClientMessage(
                Component.literal("Something went wrong... the altar flickers.")
                    .withStyle(ChatFormatting.RED), 
                false
            );
            // Refund Spirit Dew
            if (!player.isCreative()) {
                player.getInventory().add(new ItemStack(ModItems.SPIRIT_DEW));
            }
            altarCooldowns.remove(key);
            return ItemInteractionResult.FAIL;
        }
        
        return ItemInteractionResult.SUCCESS;
    }
    
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, 
            Player player, BlockHitResult hitResult) {
        
        if (!level.isClientSide()) {
            // Key für diesen Altar + Spieler
            AltarKey key = new AltarKey(level.dimension(), pos, player.getUUID());
            
            // Cooldown-Status anzeigen
            Long lastUse = altarCooldowns.get(key);
            if (lastUse != null) {
                long now = System.currentTimeMillis();
                long remaining = COOLDOWN_MS - (now - lastUse);
                
                if (remaining > 0) {
                    player.displayClientMessage(
                        Component.literal("This altar's cooldown for you: " + formatTime(remaining))
                            .withStyle(ChatFormatting.YELLOW), 
                        true
                    );
                } else {
                    player.displayClientMessage(
                        Component.literal("This altar is ready. Offer Spirit Dew to summon a legendary!")
                            .withStyle(ChatFormatting.GREEN), 
                        true
                    );
                }
            } else {
                player.displayClientMessage(
                    Component.literal("This altar is ready. Offer Spirit Dew to summon a legendary!")
                        .withStyle(ChatFormatting.GREEN), 
                    true
                );
            }
        }
        
        return InteractionResult.SUCCESS;
    }
    
    private void playActivationEffects(ServerLevel level, BlockPos pos) {
        // Sound
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.5f);
        
        // Partikel
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        
        for (int i = 0; i < 50; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;
            
            level.sendParticles(ParticleTypes.END_ROD, 
                x + offsetX, y + offsetY, z + offsetZ, 
                1, 0, 0.1, 0, 0.05);
        }
        
        // Zusätzliche Spirale nach oben
        for (int i = 0; i < 30; i++) {
            double angle = i * 0.3;
            double radius = 0.5 + i * 0.05;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + i * 0.1;
            
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, 
                px, py, pz, 
                1, 0, 0, 0, 0);
        }
    }
    
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    // ==================== Admin-Funktionen ====================
    
    /**
     * Setzt den Cooldown für einen Spieler an einem bestimmten Altar zurück.
     */
    public static void resetCooldown(ResourceKey<Level> dimension, BlockPos pos, UUID playerId) {
        altarCooldowns.remove(new AltarKey(dimension, pos, playerId));
    }
    
    /**
     * Setzt alle Cooldowns für einen Spieler zurück.
     */
    public static void resetPlayerCooldowns(UUID playerId) {
        altarCooldowns.entrySet().removeIf(e -> e.getKey().playerId.equals(playerId));
    }
    
    /**
     * Setzt alle Cooldowns für einen bestimmten Altar zurück.
     */
    public static void resetAltarCooldowns(ResourceKey<Level> dimension, BlockPos pos) {
        altarCooldowns.entrySet().removeIf(e -> 
            e.getKey().dimension.equals(dimension) && e.getKey().pos.equals(pos));
    }
    
    /**
     * Setzt alle Cooldowns zurück.
     */
    public static void resetAllCooldowns() {
        altarCooldowns.clear();
    }
    
    // ==================== Inner Class ====================
    
    /**
     * Key für die Cooldown-Map: Dimension + BlockPos + Spieler-UUID
     */
    private record AltarKey(ResourceKey<Level> dimension, BlockPos pos, UUID playerId) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AltarKey altarKey = (AltarKey) o;
            return Objects.equals(dimension, altarKey.dimension) && 
                   Objects.equals(pos, altarKey.pos) && 
                   Objects.equals(playerId, altarKey.playerId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos, playerId);
        }
    }
}
