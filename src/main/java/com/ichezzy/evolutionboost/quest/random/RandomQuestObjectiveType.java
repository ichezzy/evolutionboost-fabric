package com.ichezzy.evolutionboost.quest.random;

/**
 * Alle möglichen Objective-Typen für Random Quests.
 * Jeder Typ hat bestimmte Parameter und Tracking-Logik.
 */
public enum RandomQuestObjectiveType {
    // ==================== Catch Objectives ====================
    
    /** Fange X beliebige Pokémon */
    CATCH_ANY("Catch %d Pokémon", "catch_any"),
    
    /** Fange X Pokémon eines bestimmten Typs */
    CATCH_TYPE("Catch %d %s-type Pokémon", "catch_type"),
    
    /** Fange X Shiny Pokémon */
    CATCH_SHINY("Catch %d Shiny Pokémon", "catch_shiny"),
    
    /** Fange X Legendäre Pokémon */
    CATCH_LEGENDARY("Catch %d Legendary Pokémon", "catch_legendary"),
    
    /** Fange X Pokémon mit Hidden Ability */
    CATCH_HA("Catch %d Pokémon with Hidden Ability", "catch_ha"),
    
    /** Fange X Pokémon mit bestimmter Natur */
    CATCH_NATURE("Catch %d Pokémon with %s nature", "catch_nature"),
    
    // ==================== Battle Objectives ====================
    
    /** Besiege X wilde Pokémon */
    DEFEAT_WILD("Defeat %d wild Pokémon", "defeat_wild"),
    
    /** Besiege X Pokémon eines bestimmten Typs */
    DEFEAT_TYPE("Defeat %d %s-type Pokémon", "defeat_type"),
    
    /** Gewinne X Kämpfe (wild oder trainer) */
    WIN_BATTLE("Win %d battles", "win_battle"),
    
    // ==================== Training Objectives ====================
    
    /** Verdiene X Pokémon-XP */
    GAIN_POKEMON_XP("Gain %d Pokémon XP", "gain_xp"),
    
    /** Level X Pokémon hoch */
    LEVEL_UP("Level up Pokémon %d times", "level_up"),
    
    /** Entwickle X Pokémon */
    EVOLVE("Evolve %d Pokémon", "evolve"),
    
    // ==================== Breeding Objectives ====================
    
    /** Brüte X Eier aus */
    HATCH_EGG("Hatch %d eggs", "hatch_egg");

    // ==================== Fields ====================
    
    private final String displayFormat;
    private final String id;

    RandomQuestObjectiveType(String displayFormat, String id) {
        this.displayFormat = displayFormat;
        this.id = id;
    }

    /**
     * @return Format-String für die Anzeige (mit %d für amount, %s für type/nature)
     */
    public String getDisplayFormat() {
        return displayFormat;
    }

    /**
     * @return Eindeutige ID für Serialisierung
     */
    public String getId() {
        return id;
    }

    /**
     * Findet einen Typ anhand seiner ID.
     */
    public static RandomQuestObjectiveType fromId(String id) {
        for (RandomQuestObjectiveType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * @return true wenn dieser Typ einen Pokémon-Typ als Parameter braucht
     */
    public boolean requiresPokemonType() {
        return this == CATCH_TYPE || this == DEFEAT_TYPE;
    }

    /**
     * @return true wenn dieser Typ eine Natur als Parameter braucht
     */
    public boolean requiresNature() {
        return this == CATCH_NATURE;
    }

    /**
     * Gibt die Schwierigkeit dieses Objective-Typs zurück.
     * Wird verwendet um zu bestimmen, in welchen Quest-Typen dieser Objective-Typ vorkommen kann.
     */
    public Difficulty getDifficulty() {
        return switch (this) {
            case CATCH_ANY, DEFEAT_WILD, GAIN_POKEMON_XP, WIN_BATTLE -> Difficulty.EASY;
            case CATCH_TYPE, DEFEAT_TYPE, LEVEL_UP, HATCH_EGG -> Difficulty.MEDIUM;
            case EVOLVE, CATCH_HA, CATCH_NATURE -> Difficulty.HARD;
            case CATCH_SHINY, CATCH_LEGENDARY -> Difficulty.VERY_HARD;
        };
    }

    public enum Difficulty {
        EASY,       // Daily geeignet
        MEDIUM,     // Daily und Weekly geeignet
        HARD,       // Weekly und Monthly geeignet
        VERY_HARD   // Nur Weekly (mit 1) und Monthly geeignet
    }
}
