package com.ichezzy.evolutionboost.permission;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

/**
 * Zentrale Permission-Hilfe für EvolutionBoost.
 *
 * - Nutzt optional die Fabric Permissions API (LuckPerms-Hook).
 * - Fällt IMMER auf vanilla OP-Level zurück.
 *
 * Verwendung:
 *   EbPermissions.check(src, "evolutionboost.boost", 2, false)
 *
 * Bedeutet:
 *   - OP-Level >= 2 => immer erlaubt
 *   - sonst: falls fabric-permissions-api vorhanden:
 *       -> Permissions.check(node, defaultValue)
 *     falls nicht vorhanden:
 *       -> defaultValue (hier: false)
 */
public final class EvolutionboostPermissions {

    /** Reflektierte Methode me.lucko.fabric.api.permissions.v0.Permissions.check(...) oder null, wenn API fehlt. */
    private static final Method FABRIC_PERM_CHECK;

    static {
        Method m = null;
        try {
            Class<?> c = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            m = c.getMethod("check", CommandSourceStack.class, String.class, boolean.class);
        } catch (Throwable ignored) {
            // Kein fabric-permissions-api installiert -> bleiben einfach bei OP-only
        }
        FABRIC_PERM_CHECK = m;
    }

    private EvolutionboostPermissions() {}

    /**
     * @param src           CommandSource (Spieler, Konsole, etc.)
     * @param node          Permission-Node, z.B. "evolutionboost.boost"
     * @param fallbackLevel Vanilla-OP-Level, ab dem es IMMER erlaubt ist (z.B. 2)
     * @param defaultValue  Default-Wert für Permissions.check(...), falls Node nicht gesetzt ist
     */
    public static boolean check(CommandSourceStack src, String node, int fallbackLevel, boolean defaultValue) {
        // Vanilla-OP / Konsole mit entsprechendem Level darf immer
        if (src.hasPermission(fallbackLevel)) {
            return true;
        }

        // Wenn keine Fabric-Permissions-API vorhanden ist, greifen wir rein auf OP zurück
        if (FABRIC_PERM_CHECK == null) {
            return defaultValue;
        }

        try {
            return (boolean) FABRIC_PERM_CHECK.invoke(null, src, node, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
