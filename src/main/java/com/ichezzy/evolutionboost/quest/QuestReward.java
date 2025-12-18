package com.ichezzy.evolutionboost.quest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Eine Belohnung für das Abschließen einer Quest.
 */
public class QuestReward {

    public enum RewardType {
        ITEM,       // Minecraft/Mod Item
        POKEMON,    // Cobblemon Pokemon
        XP,         // Minecraft XP
        COMMAND     // Server-Command ausführen
    }

    private final RewardType type;
    private final String value;     // Item-ID, Pokemon-Species, XP-Menge, oder Command
    private final int count;        // Anzahl (für Items) oder Level (für Pokemon)
    private final String extra;     // Extra-Daten (z.B. Pokemon-Aspects, NBT)

    public QuestReward(RewardType type, String value, int count, String extra) {
        this.type = type;
        this.value = value;
        this.count = count;
        this.extra = extra;
    }

    public QuestReward(RewardType type, String value, int count) {
        this(type, value, count, null);
    }

    public RewardType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getCount() {
        return count;
    }

    public String getExtra() {
        return extra;
    }

    /**
     * Gibt die Belohnung an einen Spieler.
     * @return true wenn erfolgreich
     */
    public boolean grantTo(ServerPlayer player) {
        return switch (type) {
            case ITEM -> grantItem(player);
            case POKEMON -> grantPokemon(player);
            case XP -> grantXp(player);
            case COMMAND -> executeCommand(player);
        };
    }

    private boolean grantItem(ServerPlayer player) {
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(value));
        if (itemOpt.isEmpty()) {
            return false;
        }

        ItemStack stack = new ItemStack(itemOpt.get(), count);

        // Extra als NBT parsen wenn vorhanden
        // TODO: NBT-Parsing wenn nötig

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return true;
    }

    private boolean grantPokemon(ServerPlayer player) {
        try {
            // Pokemon mit Properties erstellen
            String properties = value;
            if (extra != null && !extra.isEmpty()) {
                properties += " " + extra;
            }
            if (count > 1) {
                properties += " level=" + count;
            }

            PokemonProperties props = PokemonProperties.Companion.parse(properties);
            Pokemon pokemon = props.create();

            // Zum Spieler-Team hinzufügen
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party.size() < 6) {
                party.add(pokemon);
            } else {
                // PC wenn Team voll
                var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
                pc.add(pokemon);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean grantXp(ServerPlayer player) {
        player.giveExperiencePoints(count);
        return true;
    }

    private boolean executeCommand(ServerPlayer player) {
        try {
            String command = value.replace("%player%", player.getName().getString());
            player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack(),
                    command
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Beschreibung der Belohnung für Anzeige.
     */
    public String getDisplayText() {
        return switch (type) {
            case ITEM -> count + "x " + getItemDisplayName();
            case POKEMON -> "Pokemon: " + value + (count > 1 ? " (Lv." + count + ")" : "");
            case XP -> count + " XP";
            case COMMAND -> "Special Reward";
        };
    }

    private String getItemDisplayName() {
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(value));
        if (itemOpt.isPresent()) {
            return itemOpt.get().getDescription().getString();
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("QuestReward{type=%s, value='%s', count=%d}", type, value, count);
    }
}
