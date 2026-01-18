package com.ichezzy.evolutionboost.boost;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Verwaltet globale Boosts die durch Event Voucher aktiviert werden können.
 * 
 * Boost-Typen:
 * - IV: x2 IV-Werte für gefangene Pokémon
 * - XP: x2 XP für Pokémon
 * - SHINY: x2 Shiny-Chance
 */
public class GlobalBoostManager {
    private static GlobalBoostManager INSTANCE;
    
    private final Map<BoostType, BoostData> activeBoosts = new EnumMap<>(BoostType.class);
    private MinecraftServer server;

    public enum BoostType {
        IV("IV", ChatFormatting.AQUA, "🧬"),
        XP("XP", ChatFormatting.GREEN, "⭐"),
        SHINY("Shiny", ChatFormatting.LIGHT_PURPLE, "✨");

        private final String displayName;
        private final ChatFormatting color;
        private final String icon;

        BoostType(String displayName, ChatFormatting color, String icon) {
            this.displayName = displayName;
            this.color = color;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public ChatFormatting getColor() { return color; }
        public String getIcon() { return icon; }
    }

    public static class BoostData {
        public final double multiplier;
        public final Instant expiresAt;
        public final String activatedBy;

        public BoostData(double multiplier, Instant expiresAt, String activatedBy) {
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
            this.activatedBy = activatedBy;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public long getRemainingSeconds() {
            return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        }

        public String getRemainingTimeFormatted() {
            long seconds = getRemainingSeconds();
            long minutes = seconds / 60;
            long hours = minutes / 60;
            minutes = minutes % 60;
            seconds = seconds % 60;

            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }

    // ==================== Singleton ====================

    public static GlobalBoostManager get() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalBoostManager();
        }
        return INSTANCE;
    }

    // ==================== Initialization ====================

    public void init(MinecraftServer server) {
        this.server = server;
        EvolutionBoost.LOGGER.info("[boost] GlobalBoostManager initialized");
    }

    // ==================== Boost Management ====================

    /**
     * Aktiviert einen globalen Boost.
     * 
     * @param type Der Boost-Typ
     * @param multiplier Der Multiplikator (z.B. 2.0 für x2)
     * @param durationSeconds Dauer in Sekunden
     * @param activatedBy Name des Spielers der den Boost aktiviert hat
     */
    public void activateBoost(BoostType type, double multiplier, long durationSeconds, String activatedBy) {
        Instant expiresAt = Instant.now().plusSeconds(durationSeconds);
        BoostData data = new BoostData(multiplier, expiresAt, activatedBy);
        activeBoosts.put(type, data);

        // Broadcast an alle Spieler
        if (server != null) {
            Component message = Component.literal("")
                    .append(Component.literal(type.getIcon() + " ")
                            .withStyle(type.getColor()))
                    .append(Component.literal("GLOBAL BOOST ACTIVATED!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n   "))
                    .append(Component.literal("x" + (int) multiplier + " " + type.getDisplayName())
                            .withStyle(type.getColor(), ChatFormatting.BOLD))
                    .append(Component.literal(" for " + formatDuration(durationSeconds))
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n   Activated by: " + activatedBy)
                            .withStyle(ChatFormatting.GRAY));

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        }

        EvolutionBoost.LOGGER.info("[boost] {} boost activated by {} (x{} for {}s)",
                type.name(), activatedBy, multiplier, durationSeconds);
    }

    /**
     * Prüft ob ein Boost aktiv ist und gibt den Multiplikator zurück.
     * 
     * @param type Der Boost-Typ
     * @return Der Multiplikator (1.0 wenn kein Boost aktiv)
     */
    public double getMultiplier(BoostType type) {
        BoostData data = activeBoosts.get(type);
        if (data == null || data.isExpired()) {
            activeBoosts.remove(type);
            return 1.0;
        }
        return data.multiplier;
    }

    /**
     * Prüft ob ein bestimmter Boost aktiv ist.
     */
    public boolean isBoostActive(BoostType type) {
        BoostData data = activeBoosts.get(type);
        if (data == null || data.isExpired()) {
            activeBoosts.remove(type);
            return false;
        }
        return true;
    }

    /**
     * Holt die Boost-Daten für einen Typ.
     */
    public BoostData getBoostData(BoostType type) {
        BoostData data = activeBoosts.get(type);
        if (data != null && data.isExpired()) {
            activeBoosts.remove(type);
            return null;
        }
        return data;
    }

    /**
     * Deaktiviert einen Boost manuell.
     */
    public void deactivateBoost(BoostType type) {
        BoostData removed = activeBoosts.remove(type);
        if (removed != null && server != null) {
            Component message = Component.literal(type.getIcon() + " ")
                    .withStyle(type.getColor())
                    .append(Component.literal(type.getDisplayName() + " boost has ended!")
                            .withStyle(ChatFormatting.GRAY));

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * Gibt den aktuellen Status aller Boosts zurück (für Info-Command).
     */
    public void sendBoostStatus(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== Global Boosts ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        boolean anyActive = false;
        for (BoostType type : BoostType.values()) {
            BoostData data = getBoostData(type);
            if (data != null) {
                anyActive = true;
                player.sendSystemMessage(Component.literal("  " + type.getIcon() + " ")
                        .withStyle(type.getColor())
                        .append(Component.literal(type.getDisplayName() + ": ")
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("x" + (int) data.multiplier)
                                .withStyle(type.getColor(), ChatFormatting.BOLD))
                        .append(Component.literal(" (" + data.getRemainingTimeFormatted() + " remaining)")
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        if (!anyActive) {
            player.sendSystemMessage(Component.literal("  No active boosts")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Formatiert eine Dauer in Sekunden zu einem lesbaren String.
     */
    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    /**
     * Tick-Methode um abgelaufene Boosts zu entfernen und zu notifizieren.
     * Sollte einmal pro Sekunde aufgerufen werden.
     */
    public void tick() {
        for (BoostType type : BoostType.values()) {
            BoostData data = activeBoosts.get(type);
            if (data != null && data.isExpired()) {
                deactivateBoost(type);
            }
        }
    }
}
