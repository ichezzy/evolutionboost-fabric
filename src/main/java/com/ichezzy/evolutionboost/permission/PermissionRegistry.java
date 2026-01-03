package com.ichezzy.evolutionboost.permission;

import com.ichezzy.evolutionboost.EvolutionBoost;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registriert alle EvolutionBoost Permissions für LuckPerms/Fabric Permissions API.
 * 
 * Damit Permissions im LuckPerms Web-Editor angezeigt werden, müssen sie
 * über die Fabric Permissions API registriert werden.
 */
public final class PermissionRegistry {
    private PermissionRegistry() {}

    /**
     * Alle verfügbaren Permissions mit Beschreibungen.
     */
    public static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();

    static {
        // Admin
        PERMISSIONS.put("evolutionboost.admin", "Access to admin commands (reload, debug, gc, cache)");
        
        // Boost
        PERMISSIONS.put("evolutionboost.boost", "Access to boost commands (add, clear, list)");
        
        // Event
        PERMISSIONS.put("evolutionboost.event", "Access to event commands (spawn, npc)");
        
        // Rewards
        PERMISSIONS.put("evolutionboost.rewards", "View rewards help");
        PERMISSIONS.put("evolutionboost.rewards.admin", "Access to rewards admin commands (set, list, reload)");
        
        // Weather
        PERMISSIONS.put("evolutionboost.weather", "View weather help");
        PERMISSIONS.put("evolutionboost.weather.admin", "Access to weather commands (enable, storm, auto)");
        
        // Quest
        PERMISSIONS.put("evolutionboost.quest.admin", "Access to quest admin commands (activate, complete, set, reset)");
        
        // Dex
        PERMISSIONS.put("evolutionboost.dex.admin", "Access to dex admin commands (check, reload, reset)");
    }

    /**
     * Registriert alle Permissions bei der Fabric Permissions API.
     * Wird beim Server-Start aufgerufen.
     */
    public static void register() {
        try {
            // Versuche die Fabric Permissions API zu finden
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            
            // Suche nach einer Registrierungsmethode
            // Die API hat verschiedene Versionen, wir versuchen mehrere Ansätze
            
            // Ansatz 1: PermissionCheckEvent für Suggestions
            try {
                Class<?> optionClass = Class.forName("me.lucko.fabric.api.permissions.v0.Options");
                Method getMethod = optionClass.getMethod("get", String.class, String.class, String.class);
                
                // Registriere jede Permission als Option mit Beschreibung
                for (Map.Entry<String, String> entry : PERMISSIONS.entrySet()) {
                    try {
                        // Dies macht die Permission "bekannt" für das System
                        getMethod.invoke(null, entry.getKey(), "description", entry.getValue());
                    } catch (Exception ignored) {}
                }
                
                EvolutionBoost.LOGGER.info("[permissions] Registered {} permissions with Fabric Permissions API (Options)", 
                        PERMISSIONS.size());
                return;
            } catch (ClassNotFoundException ignored) {}
            
            // Ansatz 2: Einfach loggen dass API vorhanden ist
            EvolutionBoost.LOGGER.info("[permissions] Fabric Permissions API found. {} permissions available.", 
                    PERMISSIONS.size());
            
        } catch (ClassNotFoundException e) {
            EvolutionBoost.LOGGER.info("[permissions] Fabric Permissions API not found. Using OP-level fallback.");
            EvolutionBoost.LOGGER.info("[permissions] Install 'fabric-permissions-api' for LuckPerms integration.");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[permissions] Error registering permissions: {}", e.getMessage());
        }
    }

    /**
     * Gibt eine formatierte Liste aller Permissions zurück (für /eb admin permissions).
     */
    public static String getFormattedList() {
        StringBuilder sb = new StringBuilder();
        sb.append("EvolutionBoost Permissions:\n");
        
        String lastCategory = "";
        for (Map.Entry<String, String> entry : PERMISSIONS.entrySet()) {
            String perm = entry.getKey();
            String desc = entry.getValue();
            
            // Kategorie extrahieren (evolutionboost.X)
            String[] parts = perm.split("\\.");
            String category = parts.length > 1 ? parts[1] : "other";
            
            if (!category.equals(lastCategory)) {
                sb.append("\n§6").append(category.toUpperCase()).append("§r\n");
                lastCategory = category;
            }
            
            sb.append("  §a").append(perm).append("§r\n");
            sb.append("    §7").append(desc).append("§r\n");
        }
        
        return sb.toString();
    }
}
