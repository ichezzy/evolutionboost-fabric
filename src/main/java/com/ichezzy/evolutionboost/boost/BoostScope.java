package com.ichezzy.evolutionboost.boost;

public enum BoostScope {
    GLOBAL;

    /**
     * Helper für persistente Daten aus älteren Versionen.
     * Unbekannte Namen oder das alte "PLAYER" werden auf GLOBAL gemappt.
     */
    public static BoostScope fromPersistent(String name) {
        if (name == null) {
            return GLOBAL;
        }
        try {
            if ("PLAYER".equalsIgnoreCase(name)) {
                return GLOBAL;
            }
            return BoostScope.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return GLOBAL;
        }
    }
}
