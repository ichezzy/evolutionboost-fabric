package com.ichezzy.evolutionboost.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Zentralisierte Dimensionen-Beschränkungen.
 * 
 * Beschränkte Dimensionen (kein Fliegen, kein Fallschaden):
 * - event:* (alle event-Dimensionen)
 * - evolution:* (alle evolution-Dimensionen, AUSSER evolution:quarry)
 */
public final class DimensionRestrictions {

    private DimensionRestrictions() {}

    /**
     * Prüft ob Fliegen (Elytra/Raketen) in dieser Dimension verboten ist.
     * 
     * @param level Die Dimension
     * @return true wenn Fliegen verboten ist
     */
    public static boolean isFlightRestricted(Level level) {
        return isFlightRestricted(level.dimension().location());
    }

    /**
     * Prüft ob Fliegen (Elytra/Raketen) in dieser Dimension verboten ist.
     * 
     * @param dimId Die Dimension-ID
     * @return true wenn Fliegen verboten ist
     */
    public static boolean isFlightRestricted(ResourceLocation dimId) {
        String namespace = dimId.getNamespace();
        String path = dimId.getPath();

        // event:* - alle event-Dimensionen sind beschränkt
        if (namespace.equals("event")) {
            return true;
        }

        // evolution:* - alle evolution-Dimensionen AUSSER quarry
        if (namespace.equals("evolution")) {
            // quarry erlaubt Fliegen
            return !path.equals("quarry");
        }

        return false;
    }

    /**
     * Prüft ob Fallschaden in dieser Dimension deaktiviert ist.
     * (Gleiche Logik wie Flugbeschränkung - wenn kein Fliegen, dann auch kein Fallschaden)
     * 
     * @param level Die Dimension
     * @return true wenn Fallschaden deaktiviert ist
     */
    public static boolean isFallDamageDisabled(Level level) {
        return isFlightRestricted(level);
    }

    /**
     * Prüft ob Fallschaden in dieser Dimension deaktiviert ist.
     * 
     * @param dimId Die Dimension-ID
     * @return true wenn Fallschaden deaktiviert ist
     */
    public static boolean isFallDamageDisabled(ResourceLocation dimId) {
        return isFlightRestricted(dimId);
    }
}
