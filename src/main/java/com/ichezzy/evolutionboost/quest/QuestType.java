package com.ichezzy.evolutionboost.quest;

/**
 * Typen von Quest-Objectives.
 * Jeder Typ hat unterschiedliche Filter-Möglichkeiten.
 */
public enum QuestType {
    /**
     * Besiege Pokemon (KO in Battle).
     * Filter: species, types, aspects, minLevel, maxLevel
     */
    DEFEAT,

    /**
     * Kämpfe gegen Pokemon (Battle starten, Sieg nicht nötig).
     * Filter: species, types, aspects, minLevel, maxLevel
     */
    BATTLE,

    /**
     * Fange Pokemon.
     * Filter: species, types, aspects, minLevel, maxLevel, shiny
     */
    CATCH,

    /**
     * Entwickle Pokemon.
     * Filter: species (Ziel-Pokemon), fromSpecies (Ausgangs-Pokemon)
     */
    EVOLVE,

    /**
     * Sammle Items (im Inventar haben/aufheben).
     * Filter: item (Item-ID)
     */
    COLLECT_ITEM,

    /**
     * Liefere Items ab (Items werden entfernt).
     * Filter: item (Item-ID)
     */
    DELIVER_ITEM,

    /**
     * Leve Pokemon hoch.
     * Filter: species, types, minLevel (Ziel-Level)
     */
    LEVEL_UP,

    /**
     * Besuche einen Ort.
     * Filter: dimension, x, y, z, radius
     */
    VISIT_LOCATION,

    /**
     * Begegne Pokemon (Spawn in Nähe).
     * Filter: species, types, aspects, shiny
     */
    ENCOUNTER,

    /**
     * Besiege einen Trainer/NPC.
     * Filter: trainerId, trainerName
     */
    DEFEAT_TRAINER,

    /**
     * Interagiere mit einem NPC.
     * Filter: npcId, npcName
     */
    TALK_TO_NPC,

    /**
     * Custom - wird per Command manuell inkrementiert.
     * Für spezielle Quests die wir nicht automatisch tracken können.
     */
    CUSTOM;

    /**
     * Prüft ob dieser Typ automatisch getrackt werden kann.
     */
    public boolean isAutoTracked() {
        return switch (this) {
            case DEFEAT, BATTLE, CATCH, EVOLVE, COLLECT_ITEM, LEVEL_UP, ENCOUNTER -> true;
            case DELIVER_ITEM, VISIT_LOCATION, DEFEAT_TRAINER, TALK_TO_NPC, CUSTOM -> false;
        };
    }
}
