package com.ichezzy.evolutionboost.quest.random;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.Map;

/**
 * Scheduler für Random Quest Resets.
 * Prüft effizient auf 00:00 UTC und benachrichtigt Spieler über neue Quests.
 * 
 * Performance-Optimierungen:
 * - Prüft nur alle 20 Ticks (1 Sekunde)
 * - Speichert letzten bekannten Seed pro Periode
 * - Keine Berechnungen wenn keine Spieler online
 */
public class RandomQuestScheduler {
    
    /** Letzte bekannte Seeds pro Periode */
    private static final Map<RandomQuestPeriod, String> lastKnownSeeds = new EnumMap<>(RandomQuestPeriod.class);
    
    /** Tick-Counter für Performance */
    private static int tickCounter = 0;
    
    /** Check-Intervall in Ticks (20 = 1 Sekunde) */
    private static final int CHECK_INTERVAL = 20;
    
    /** Ob der Scheduler initialisiert wurde */
    private static boolean initialized = false;
    
    /**
     * Registriert den Tick-Handler.
     * Sollte einmal beim Server-Start aufgerufen werden.
     */
    public static void register() {
        if (initialized) return;
        initialized = true;
        
        // Initiale Seeds setzen
        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            lastKnownSeeds.put(period, getCurrentSeed(period));
        }
        
        ServerTickEvents.END_SERVER_TICK.register(RandomQuestScheduler::onServerTick);
        EvolutionBoost.LOGGER.info("[random-quests] Scheduler registered");
    }
    
    /**
     * Server-Tick Handler.
     * Prüft effizient auf Perioden-Wechsel.
     */
    private static void onServerTick(MinecraftServer server) {
        // Nur alle CHECK_INTERVAL Ticks prüfen
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;
        
        // Keine Prüfung wenn keine Spieler online
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        
        // Prüfe jede Periode auf Seed-Wechsel
        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            String currentSeed = getCurrentSeed(period);
            String lastSeed = lastKnownSeeds.get(period);
            
            if (!currentSeed.equals(lastSeed)) {
                // Seed hat sich geändert = neue Periode!
                lastKnownSeeds.put(period, currentSeed);
                
                EvolutionBoost.LOGGER.info("[random-quests] {} quest reset detected (old: {}, new: {})",
                        period.getDisplayName(), lastSeed, currentSeed);
                
                // Spieler benachrichtigen
                RandomQuestManager.get().notifyNewQuests(period);
            }
        }
    }
    
    /**
     * Generiert den aktuellen Seed für eine Periode.
     */
    private static String getCurrentSeed(RandomQuestPeriod period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        
        return switch (period) {
            case DAILY -> String.format("%d-%03d", now.getYear(), now.getDayOfYear());
            case WEEKLY -> {
                ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield String.format("%d-W%02d", weekStart.getYear(), 
                        weekStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            }
            case MONTHLY -> String.format("%d-%02d", now.getYear(), now.getMonthValue());
        };
    }
    
    /**
     * Setzt den Seed für eine Periode zurück.
     * Wird vom Admin-Reset verwendet um sofortige Benachrichtigung zu triggern.
     */
    public static void invalidateSeed(RandomQuestPeriod period) {
        lastKnownSeeds.put(period, "INVALID");
    }
    
    /**
     * Setzt alle Seeds zurück.
     */
    public static void invalidateAllSeeds() {
        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            lastKnownSeeds.put(period, "INVALID");
        }
    }
}
