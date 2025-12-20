package com.ichezzy.evolutionboost.block;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registrierung aller Mod-Blöcke.
 */
public final class ModBlocks {
    private ModBlocks() {}

    // Spirit Altar - spawnt legendäre Pokemon mit Spirit Dew
    public static final Block SPIRIT_ALTAR = register("spirit_altar",
            new SpiritAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE)
                    .strength(5.0f, 1200.0f)  // Hart wie Obsidian
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)   // Leichtes Leuchten
                    .noOcclusion()            // Transparenz erlauben
            ));

    // ==================== Registration ====================

    private static Block register(String name, Block block) {
        return Registry.register(
                BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, name),
                block
        );
    }

    /**
     * Muss bei Mod-Init aufgerufen werden um die Blöcke zu laden.
     */
    public static void registerAll() {
        EvolutionBoost.LOGGER.info("[{}] Registering blocks...", EvolutionBoost.MOD_ID);
        // Statische Felder werden durch Klassenladen initialisiert
    }
}
