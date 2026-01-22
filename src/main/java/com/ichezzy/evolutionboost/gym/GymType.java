package com.ichezzy.evolutionboost.gym;

import net.minecraft.ChatFormatting;

/**
 * Alle verfügbaren Gym-Typen (basierend auf Pokemon-Typen).
 */
public enum GymType {
    BUG("Bug", "Käfer", ChatFormatting.GREEN),
    DARK("Dark", "Unlicht", ChatFormatting.DARK_GRAY),
    DRAGON("Dragon", "Drache", ChatFormatting.DARK_PURPLE),
    ELECTRIC("Electric", "Elektro", ChatFormatting.YELLOW),
    FAIRY("Fairy", "Fee", ChatFormatting.LIGHT_PURPLE),
    FIGHTING("Fighting", "Kampf", ChatFormatting.DARK_RED),
    FIRE("Fire", "Feuer", ChatFormatting.RED),
    FLYING("Flying", "Flug", ChatFormatting.AQUA),
    GHOST("Ghost", "Geist", ChatFormatting.DARK_PURPLE),
    GRASS("Grass", "Pflanze", ChatFormatting.GREEN),
    GROUND("Ground", "Boden", ChatFormatting.GOLD),
    ICE("Ice", "Eis", ChatFormatting.AQUA),
    NORMAL("Normal", "Normal", ChatFormatting.WHITE),
    POISON("Poison", "Gift", ChatFormatting.DARK_PURPLE),
    PSYCHIC("Psychic", "Psycho", ChatFormatting.LIGHT_PURPLE),
    ROCK("Rock", "Gestein", ChatFormatting.GOLD),
    STEEL("Steel", "Stahl", ChatFormatting.GRAY),
    WATER("Water", "Wasser", ChatFormatting.BLUE);

    private final String displayName;
    private final String germanName;
    private final ChatFormatting color;

    GymType(String displayName, String germanName, ChatFormatting color) {
        this.displayName = displayName;
        this.germanName = germanName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGermanName() {
        return germanName;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getId() {
        return name().toLowerCase();
    }

    /**
     * Findet GymType by ID (case-insensitive).
     */
    public static GymType fromId(String id) {
        if (id == null) return null;
        for (GymType type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
