package com.ichezzy.evolutionboost.dex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.DexRewardsConfig.PokemonReward;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility-Klasse zum Geben von Pokémon mit perfekten IVs als Dex-Reward.
 */
public final class DexPokemonGiver {
    private DexPokemonGiver() {}

    // Sets für Legendary, Mythical und Ultra Beasts (lowercase)
    // Diese Listen basieren auf offiziellen Pokémon-Kategorien
    private static final Set<String> LEGENDARY_POKEMON = new HashSet<>();
    private static final Set<String> MYTHICAL_POKEMON = new HashSet<>();
    private static final Set<String> ULTRA_BEASTS = new HashSet<>();

    static {
        // Legendary Pokémon (Gen 1-9)
        // Gen 1
        LEGENDARY_POKEMON.add("articuno"); LEGENDARY_POKEMON.add("zapdos"); LEGENDARY_POKEMON.add("moltres");
        LEGENDARY_POKEMON.add("mewtwo");
        // Gen 2
        LEGENDARY_POKEMON.add("raikou"); LEGENDARY_POKEMON.add("entei"); LEGENDARY_POKEMON.add("suicune");
        LEGENDARY_POKEMON.add("lugia"); LEGENDARY_POKEMON.add("ho-oh");
        // Gen 3
        LEGENDARY_POKEMON.add("regirock"); LEGENDARY_POKEMON.add("regice"); LEGENDARY_POKEMON.add("registeel");
        LEGENDARY_POKEMON.add("latias"); LEGENDARY_POKEMON.add("latios");
        LEGENDARY_POKEMON.add("kyogre"); LEGENDARY_POKEMON.add("groudon"); LEGENDARY_POKEMON.add("rayquaza");
        // Gen 4
        LEGENDARY_POKEMON.add("uxie"); LEGENDARY_POKEMON.add("mesprit"); LEGENDARY_POKEMON.add("azelf");
        LEGENDARY_POKEMON.add("dialga"); LEGENDARY_POKEMON.add("palkia"); LEGENDARY_POKEMON.add("heatran");
        LEGENDARY_POKEMON.add("regigigas"); LEGENDARY_POKEMON.add("giratina"); LEGENDARY_POKEMON.add("cresselia");
        // Gen 5
        LEGENDARY_POKEMON.add("cobalion"); LEGENDARY_POKEMON.add("terrakion"); LEGENDARY_POKEMON.add("virizion");
        LEGENDARY_POKEMON.add("tornadus"); LEGENDARY_POKEMON.add("thundurus"); LEGENDARY_POKEMON.add("landorus");
        LEGENDARY_POKEMON.add("reshiram"); LEGENDARY_POKEMON.add("zekrom"); LEGENDARY_POKEMON.add("kyurem");
        // Gen 6
        LEGENDARY_POKEMON.add("xerneas"); LEGENDARY_POKEMON.add("yveltal"); LEGENDARY_POKEMON.add("zygarde");
        // Gen 7
        LEGENDARY_POKEMON.add("type:null"); LEGENDARY_POKEMON.add("typenull"); LEGENDARY_POKEMON.add("silvally");
        LEGENDARY_POKEMON.add("tapukoko"); LEGENDARY_POKEMON.add("tapu_koko");
        LEGENDARY_POKEMON.add("tapulele"); LEGENDARY_POKEMON.add("tapu_lele");
        LEGENDARY_POKEMON.add("tapubulu"); LEGENDARY_POKEMON.add("tapu_bulu");
        LEGENDARY_POKEMON.add("tapufini"); LEGENDARY_POKEMON.add("tapu_fini");
        LEGENDARY_POKEMON.add("cosmog"); LEGENDARY_POKEMON.add("cosmoem");
        LEGENDARY_POKEMON.add("solgaleo"); LEGENDARY_POKEMON.add("lunala"); LEGENDARY_POKEMON.add("necrozma");
        // Gen 8
        LEGENDARY_POKEMON.add("zacian"); LEGENDARY_POKEMON.add("zamazenta"); LEGENDARY_POKEMON.add("eternatus");
        LEGENDARY_POKEMON.add("kubfu"); LEGENDARY_POKEMON.add("urshifu");
        LEGENDARY_POKEMON.add("regieleki"); LEGENDARY_POKEMON.add("regidrago");
        LEGENDARY_POKEMON.add("glastrier"); LEGENDARY_POKEMON.add("spectrier"); LEGENDARY_POKEMON.add("calyrex");
        LEGENDARY_POKEMON.add("enamorus");
        // Gen 9
        LEGENDARY_POKEMON.add("tinglu"); LEGENDARY_POKEMON.add("ting-lu");
        LEGENDARY_POKEMON.add("chienpao"); LEGENDARY_POKEMON.add("chien-pao");
        LEGENDARY_POKEMON.add("wochien"); LEGENDARY_POKEMON.add("wo-chien");
        LEGENDARY_POKEMON.add("chiyu"); LEGENDARY_POKEMON.add("chi-yu");
        LEGENDARY_POKEMON.add("koraidon"); LEGENDARY_POKEMON.add("miraidon");
        LEGENDARY_POKEMON.add("okidogi"); LEGENDARY_POKEMON.add("munkidori"); LEGENDARY_POKEMON.add("fezandipiti");
        LEGENDARY_POKEMON.add("ogerpon"); LEGENDARY_POKEMON.add("terapagos"); LEGENDARY_POKEMON.add("pecharunt");

        // Mythical Pokémon
        MYTHICAL_POKEMON.add("mew");
        MYTHICAL_POKEMON.add("celebi");
        MYTHICAL_POKEMON.add("jirachi"); MYTHICAL_POKEMON.add("deoxys");
        MYTHICAL_POKEMON.add("phione"); MYTHICAL_POKEMON.add("manaphy"); MYTHICAL_POKEMON.add("darkrai");
        MYTHICAL_POKEMON.add("shaymin"); MYTHICAL_POKEMON.add("arceus");
        MYTHICAL_POKEMON.add("victini"); MYTHICAL_POKEMON.add("keldeo"); MYTHICAL_POKEMON.add("meloetta"); MYTHICAL_POKEMON.add("genesect");
        MYTHICAL_POKEMON.add("diancie"); MYTHICAL_POKEMON.add("hoopa"); MYTHICAL_POKEMON.add("volcanion");
        MYTHICAL_POKEMON.add("magearna"); MYTHICAL_POKEMON.add("marshadow"); MYTHICAL_POKEMON.add("zeraora");
        MYTHICAL_POKEMON.add("meltan"); MYTHICAL_POKEMON.add("melmetal");
        MYTHICAL_POKEMON.add("zarude");

        // Ultra Beasts
        ULTRA_BEASTS.add("nihilego"); ULTRA_BEASTS.add("buzzwole"); ULTRA_BEASTS.add("pheromosa");
        ULTRA_BEASTS.add("xurkitree"); ULTRA_BEASTS.add("celesteela"); ULTRA_BEASTS.add("kartana");
        ULTRA_BEASTS.add("guzzlord"); ULTRA_BEASTS.add("poipole"); ULTRA_BEASTS.add("naganadel");
        ULTRA_BEASTS.add("stakataka"); ULTRA_BEASTS.add("blacephalon");
    }

    /**
     * Prüft ob eine Species für den gegebenen PokemonReward erlaubt ist.
     * Nur Basis-Pokémon (erste Entwicklungsstufe) sind erlaubt!
     */
    public static boolean isSpeciesAllowed(String speciesName, PokemonReward reward) {
        String lower = speciesName.toLowerCase().replace(" ", "").replace("-", "");

        // Auch Varianten ohne Bindestriche checken
        boolean isLegendary = LEGENDARY_POKEMON.contains(lower)
                || LEGENDARY_POKEMON.contains(speciesName.toLowerCase());
        boolean isMythical = MYTHICAL_POKEMON.contains(lower)
                || MYTHICAL_POKEMON.contains(speciesName.toLowerCase());
        boolean isUltraBeast = ULTRA_BEASTS.contains(lower)
                || ULTRA_BEASTS.contains(speciesName.toLowerCase());

        if (isLegendary && !reward.allowLegendary) return false;
        if (isMythical && !reward.allowMythical) return false;
        if (isUltraBeast && !reward.allowUltraBeasts) return false;

        // Prüfen ob es eine erste Entwicklungsstufe ist (kein Pre-Evolution)
        try {
            Species species = PokemonSpecies.INSTANCE.getByName(speciesName.toLowerCase());
            if (species != null) {
                // Wenn das Pokémon Pre-Evolutions hat, ist es NICHT die erste Stufe
                var preEvolutions = species.getPreEvolution();
                if (preEvolutions != null) {
                    return false; // Hat Pre-Evolution = nicht erlaubt
                }
            }
        } catch (Exception e) {
            // Fallback: erlauben wenn wir nicht prüfen können
        }

        return true;
    }

    /**
     * Prüft ob eine Species in Cobblemon existiert.
     */
    public static boolean speciesExists(String speciesName) {
        try {
            Species species = PokemonSpecies.INSTANCE.getByName(speciesName.toLowerCase());
            return species != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gibt einem Spieler ein Pokémon mit perfekten IVs.
     * Nur Basis-Pokémon (erste Entwicklungsstufe) sind erlaubt, Level 1.
     *
     * @param player Der Spieler
     * @param speciesName Die gewünschte Species
     * @param shiny Ob das Pokémon shiny sein soll
     * @param reward Die Reward-Konfiguration (für Erlaubnis-Checks)
     * @return true wenn erfolgreich
     */
    public static boolean givePokemon(ServerPlayer player, String speciesName, boolean shiny, PokemonReward reward) {
        try {
            // Species finden
            Species species = PokemonSpecies.INSTANCE.getByName(speciesName.toLowerCase());
            if (species == null) {
                player.sendSystemMessage(
                        Component.literal("Unknown Pokémon species: " + speciesName)
                                .withStyle(ChatFormatting.RED)
                );
                return false;
            }

            // Prüfen ob es eine erste Entwicklungsstufe ist
            var preEvolution = species.getPreEvolution();
            if (preEvolution != null) {
                // Finde das Basis-Pokémon und empfehle es
                Species baseSpecies = species;
                int attempts = 0;
                while (baseSpecies.getPreEvolution() != null && attempts < 5) {
                    baseSpecies = baseSpecies.getPreEvolution().getSpecies();
                    attempts++;
                }
                
                player.sendSystemMessage(
                        Component.literal("❌ " + species.getName() + " is an evolved Pokémon!")
                                .withStyle(ChatFormatting.RED)
                );
                player.sendSystemMessage(
                        Component.literal("   → You can only choose base Pokémon. Try: " + baseSpecies.getName())
                                .withStyle(ChatFormatting.GRAY)
                );
                return false;
            }

            // Erlaubnis prüfen (Legendary/Mythical/UltraBeast)
            if (!isSpeciesAllowed(speciesName, reward)) {
                String reason = "";
                String lower = speciesName.toLowerCase();
                if (LEGENDARY_POKEMON.contains(lower)) reason = "Legendary";
                else if (MYTHICAL_POKEMON.contains(lower)) reason = "Mythical";
                else if (ULTRA_BEASTS.contains(lower)) reason = "Ultra Beast";

                player.sendSystemMessage(
                        Component.literal(speciesName + " is a " + reason + " Pokémon and not allowed for this reward!")
                                .withStyle(ChatFormatting.RED)
                );
                return false;
            }

            // Shiny-Check
            if (shiny && !reward.allowShinyChoice) {
                player.sendSystemMessage(
                        Component.literal("Shiny is not available for this reward!")
                                .withStyle(ChatFormatting.RED)
                );
                return false;
            }

            // Pokémon erstellen auf Level 1
            Pokemon pokemon = species.create(1);
            pokemon.setLevel(1); // Level 1!

            // Perfekte IVs setzen (31 für alle Stats)
            pokemon.setIV(Stats.HP, 31);
            pokemon.setIV(Stats.ATTACK, 31);
            pokemon.setIV(Stats.DEFENCE, 31);
            pokemon.setIV(Stats.SPECIAL_ATTACK, 31);
            pokemon.setIV(Stats.SPECIAL_DEFENCE, 31);
            pokemon.setIV(Stats.SPEED, 31);

            // Shiny setzen wenn gewünscht
            if (shiny) {
                pokemon.setShiny(true);
            }

            // Pokémon zum Spieler hinzufügen
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);

            if (party.size() >= 6) {
                // Party voll - in PC legen
                var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
                pc.add(pokemon);

                player.sendSystemMessage(
                        Component.literal("Your party is full! " + species.getName() + " was sent to your PC.")
                                .withStyle(ChatFormatting.YELLOW)
                );
            } else {
                party.add(pokemon);
            }

            // Erfolgsmeldung
            player.sendSystemMessage(
                    Component.literal("✨ You received ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal((shiny ? "★ Shiny " : "") + species.getName() + " (Lv.1)")
                                    .withStyle(shiny ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" with perfect IVs!")
                                    .withStyle(ChatFormatting.GOLD))
            );

            EvolutionBoost.LOGGER.info("[dex] {} received {} (shiny={}, Lv.1) with perfect IVs as dex reward",
                    player.getGameProfile().getName(), speciesName, shiny);

            return true;

        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[dex] Error giving Pokémon to {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
            player.sendSystemMessage(
                    Component.literal("An error occurred while giving the Pokémon. Please contact an admin.")
                            .withStyle(ChatFormatting.RED)
            );
            return false;
        }
    }

    /**
     * Gibt eine Beschreibung zurück, welche Pokémon für einen Reward erlaubt sind.
     */
    public static String getAllowedDescription(PokemonReward reward) {
        StringBuilder sb = new StringBuilder("Base Pokémon only (Lv.1)");
        
        if (reward.allowLegendary && reward.allowMythical && reward.allowUltraBeasts) {
            sb.append(" - Any category");
        } else {
            if (!reward.allowLegendary) sb.append(" (no Legendaries)");
            if (!reward.allowMythical) sb.append(" (no Mythicals)");
            if (!reward.allowUltraBeasts) sb.append(" (no Ultra Beasts)");
        }

        return sb.toString();
    }
}
