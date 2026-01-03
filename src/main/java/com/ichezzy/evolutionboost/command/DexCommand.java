package com.ichezzy.evolutionboost.command;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig.Milestone;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig.PokemonReward;
import com.ichezzy.evolutionboost.dex.DexDataManager;
import com.ichezzy.evolutionboost.dex.DexPokemonGiver;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pokédex Reward Commands - Minimalistisch
 */
public final class DexCommand {
    private DexCommand() {}

    private static final SuggestionProvider<CommandSourceStack> MILESTONE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    DexRewardsConfig.get().getMilestoneIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> CLAIMABLE_MILESTONE_SUGGEST =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    List<Milestone> claimable = DexDataManager.getClaimableMilestones(player);
                    return SharedSuggestionProvider.suggest(
                            claimable.stream().map(m -> m.id).toList(), builder);
                } catch (Exception e) {
                    return SharedSuggestionProvider.suggest(
                            DexRewardsConfig.get().getMilestoneIds(), builder);
                }
            };

    private static final SuggestionProvider<CommandSourceStack> CLAIMABLE_POKEMON_MILESTONE_SUGGEST =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    List<Milestone> claimable = DexDataManager.getClaimablePokemonRewards(player);
                    return SharedSuggestionProvider.suggest(
                            claimable.stream().map(m -> m.id).toList(), builder);
                } catch (Exception e) {
                    return SharedSuggestionProvider.suggest(
                            DexRewardsConfig.get().milestones.stream()
                                    .filter(m -> m.pokemonReward != null)
                                    .map(m -> m.id).toList(), builder);
                }
            };

    // Species autocomplete - zeigt alle Pokémon-Namen
    private static final SuggestionProvider<CommandSourceStack> SPECIES_SUGGEST =
            (ctx, builder) -> {
                try {
                    // Hole den Milestone um zu prüfen welche Species erlaubt sind
                    String milestoneId = StringArgumentType.getString(ctx, "milestone");
                    Optional<Milestone> opt = DexRewardsConfig.get().getMilestone(milestoneId);
                    
                    List<String> speciesNames = new ArrayList<>();
                    
                    // Alle Species aus Cobblemon holen
                    for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
                        String name = species.getName().toLowerCase();
                        
                        // Filtern basierend auf Milestone-Restrictions
                        if (opt.isPresent() && opt.get().pokemonReward != null) {
                            PokemonReward pr = opt.get().pokemonReward;
                            if (DexPokemonGiver.isSpeciesAllowed(name, pr)) {
                                speciesNames.add(name);
                            }
                        } else {
                            speciesNames.add(name);
                        }
                    }
                    
                    return SharedSuggestionProvider.suggest(speciesNames, builder);
                } catch (Exception e) {
                    // Fallback: Alle Species
                    List<String> allSpecies = new ArrayList<>();
                    for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
                        allSpecies.add(species.getName().toLowerCase());
                    }
                    return SharedSuggestionProvider.suggest(allSpecies, builder);
                }
            };

    private static final SuggestionProvider<CommandSourceStack> RESET_TYPE_SUGGEST =
            (ctx, builder) -> {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                suggestions.add("pokemon");
                suggestions.addAll(DexRewardsConfig.get().getMilestoneIds());
                return SharedSuggestionProvider.suggest(suggestions, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var dexTree = Commands.literal("dex")
                .then(Commands.literal("info")
                        .executes(ctx -> showInfo(ctx.getSource())))

                .then(Commands.literal("claim")
                        .then(Commands.argument("milestone", StringArgumentType.word())
                                .suggests(CLAIMABLE_MILESTONE_SUGGEST)
                                .executes(ctx -> claimMilestone(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "milestone")))))

                .then(Commands.literal("list")
                        .executes(ctx -> listMilestones(ctx.getSource())))

                .then(Commands.literal("check")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.dex.admin", 2, false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> checkPlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("reload")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.dex.admin", 2, false))
                        .executes(ctx -> reloadConfig(ctx.getSource())))

                .then(Commands.literal("pokemon")
                        .then(Commands.argument("milestone", StringArgumentType.word())
                                .suggests(CLAIMABLE_POKEMON_MILESTONE_SUGGEST)
                                .then(Commands.argument("species", StringArgumentType.word())
                                        .suggests(SPECIES_SUGGEST)
                                        .executes(ctx -> claimPokemonReward(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "milestone"),
                                                StringArgumentType.getString(ctx, "species"),
                                                false))
                                        .then(Commands.argument("shiny", BoolArgumentType.bool())
                                                .executes(ctx -> claimPokemonReward(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "milestone"),
                                                        StringArgumentType.getString(ctx, "species"),
                                                        BoolArgumentType.getBool(ctx, "shiny")))))))

                .then(Commands.literal("reset")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.dex.admin", 2, false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(RESET_TYPE_SUGGEST)
                                        .executes(ctx -> resetPlayer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "type"))))))

                .executes(ctx -> showInfo(ctx.getSource()));

        dispatcher.register(Commands.literal("evolutionboost").then(dexTree));
        dispatcher.register(Commands.literal("eb").then(dexTree.build()));
    }

    // ==================== Player Commands ====================

    private static int showInfo(CommandSourceStack src) {
        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) {
            src.sendFailure(Component.literal("❌ Dex rewards are currently disabled."));
            return 0;
        }
        
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        return showInfoForPlayer(src, player, true);
    }

    private static int showInfoForPlayer(CommandSourceStack src, ServerPlayer player, boolean showClaimHint) {
        UUID uuid = player.getUUID();
        DexRewardsConfig config = DexRewardsConfig.get();

        int caught = DexDataManager.getCaughtCount(player, null);
        int total = config.totalPokemonCount;
        float percent = DexDataManager.getCaughtPercent(player, null);

        // Header
        src.sendSuccess(() -> Component.literal("📖 Pokédex Progress")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Progress
        src.sendSuccess(() -> Component.literal("Caught: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(caught + "/" + total)
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (" + String.format("%.1f", percent) + "%)")
                        .withStyle(ChatFormatting.AQUA)), false);

        // Progress Bar
        src.sendSuccess(() -> createProgressBar(percent), false);

        // Milestones
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("▸ Milestones")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);

        Set<String> claimed = DexDataManager.getClaimedMilestones(uuid);
        List<Milestone> claimable = DexDataManager.getClaimableMilestones(player);

        for (Milestone m : config.milestones) {
            boolean isClaimed = claimed.contains(m.id.toLowerCase());
            boolean isClaimable = claimable.stream().anyMatch(c -> c.id.equals(m.id));

            MutableComponent line;
            String dexPrefix = m.dexId != null ? "[" + extractDexName(m.dexId) + "] " : "";

            if (isClaimed) {
                line = Component.literal("  ✓ " + dexPrefix + m.name)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
            } else if (isClaimable) {
                line = Component.literal("  ★ " + dexPrefix + m.name + " ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("[CLAIM]")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                .withStyle(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                "/eb dex claim " + m.id))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Click to claim!")))));
            } else {
                ResourceLocation dexId = m.dexId != null ? ResourceLocation.tryParse(m.dexId) : null;
                float milestonePercent = DexDataManager.getCaughtPercent(player, dexId);
                line = Component.literal("  ○ " + dexPrefix + m.name)
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(" (" + String.format("%.1f", milestonePercent) + "/" + m.percent + "%)")
                                .withStyle(ChatFormatting.RED));
            }

            final MutableComponent finalLine = line;
            src.sendSuccess(() -> finalLine, false);

            // Pokemon-Reward Status
            if (m.pokemonReward != null && isClaimed) {
                boolean pokemonClaimed = DexDataManager.hasClaimedPokemonReward(uuid, m.id);
                boolean canClaimPokemon = DexDataManager.canClaimPokemonReward(player, m.id);

                if (!pokemonClaimed && canClaimPokemon) {
                    src.sendSuccess(() -> Component.literal("     🎁 ")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal("[CLAIM POKÉMON]")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                                    "/eb dex pokemon " + m.id + " "))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal(m.pokemonReward.description))))), false);
                }
            }
        }

        return 1;
    }

    private static String extractDexName(String dexId) {
        if (dexId == null) return "";
        String path = dexId.contains(":") ? dexId.split(":")[1] : dexId;
        return path.substring(0, 1).toUpperCase() + path.substring(1);
    }

    private static MutableComponent createProgressBar(float percent) {
        int filled = (int) Math.round(percent / 5); // 20 Segmente = 100%
        filled = Math.max(0, Math.min(20, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? "█" : "░");
        }

        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(bar.substring(0, filled))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(bar.substring(filled))
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("]")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int claimMilestone(CommandSourceStack src, String milestoneId) {
        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) {
            src.sendFailure(Component.literal("❌ Dex rewards are currently disabled."));
            return 0;
        }
        
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        boolean success = DexDataManager.claimMilestone(player, milestoneId);
        return success ? 1 : 0;
    }

    private static int claimPokemonReward(CommandSourceStack src, String milestoneId, String species, boolean shiny) {
        // System deaktiviert?
        if (!DexRewardsConfig.isEnabled()) {
            src.sendFailure(Component.literal("❌ Dex rewards are currently disabled."));
            return 0;
        }
        
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        UUID uuid = player.getUUID();
        Optional<Milestone> opt = DexRewardsConfig.get().getMilestone(milestoneId);

        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("❌ Unknown milestone: " + milestoneId));
            return 0;
        }

        Milestone milestone = opt.get();

        if (milestone.pokemonReward == null) {
            src.sendFailure(Component.literal("❌ This milestone has no Pokémon reward!"));
            return 0;
        }

        if (!DexDataManager.hasClaimedMilestone(uuid, milestoneId)) {
            src.sendFailure(Component.literal("❌ Claim item rewards first! ")
                    .append(Component.literal("/eb dex claim " + milestoneId)
                            .withStyle(ChatFormatting.GREEN)));
            return 0;
        }

        if (DexDataManager.hasClaimedPokemonReward(uuid, milestoneId)) {
            src.sendFailure(Component.literal("⚠ Pokémon reward already claimed!"));
            return 0;
        }

        if (!DexPokemonGiver.speciesExists(species)) {
            src.sendFailure(Component.literal("❌ Unknown Pokémon: " + species));
            return 0;
        }

        boolean success = DexPokemonGiver.givePokemon(player, species, shiny, milestone.pokemonReward);

        if (success) {
            DexDataManager.markPokemonRewardClaimed(uuid, milestoneId);
            return 1;
        }

        return 0;
    }

    private static int listMilestones(CommandSourceStack src) {
        DexRewardsConfig config = DexRewardsConfig.get();

        src.sendSuccess(() -> Component.literal("📜 All Pokédex Milestones")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        src.sendSuccess(() -> Component.literal("Total Pokémon: " + config.totalPokemonCount)
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal(""), false);

        for (Milestone m : config.milestones) {
            String dexPrefix = m.dexId != null ? "[" + extractDexName(m.dexId) + "] " : "";

            src.sendSuccess(() -> Component.literal("▸ " + dexPrefix + m.name)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" (" + m.percent + "%)")
                            .withStyle(ChatFormatting.GRAY)), false);

            if (m.description != null && !m.description.isEmpty()) {
                src.sendSuccess(() -> Component.literal("  " + m.description)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            }

            if (m.pokemonReward != null) {
                final PokemonReward pr = m.pokemonReward;
                src.sendSuccess(() -> Component.literal("  🎁 " + pr.description)
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(pr.allowShinyChoice
                                ? Component.literal(" ★").withStyle(ChatFormatting.GOLD)
                                : Component.empty()), false);
            }
        }

        return 1;
    }

    // ==================== Admin Commands ====================

    private static int checkPlayer(CommandSourceStack src, ServerPlayer target) {
        src.sendSuccess(() -> Component.literal("Checking: " + target.getGameProfile().getName())
                .withStyle(ChatFormatting.AQUA), false);
        return showInfoForPlayer(src, target, false);
    }

    private static int reloadConfig(CommandSourceStack src) {
        DexRewardsConfig.reload();
        src.sendSuccess(() -> Component.literal("✓ Dex config reloaded!")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetPlayer(CommandSourceStack src, ServerPlayer target, String type) {
        DexDataManager.resetRewards(target.getUUID(), type);

        String typeDesc = switch (type.toLowerCase()) {
            case "all" -> "all rewards";
            case "pokemon" -> "Pokémon rewards";
            default -> "'" + type + "'";
        };

        src.sendSuccess(() -> Component.literal("✓ Reset " + typeDesc + " for " + target.getGameProfile().getName())
                .withStyle(ChatFormatting.YELLOW), false);

        target.sendSystemMessage(Component.literal("⚠ Your " + typeDesc + " have been reset by an admin.")
                .withStyle(ChatFormatting.YELLOW));

        return 1;
    }
}
