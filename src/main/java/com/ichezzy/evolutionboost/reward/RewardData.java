package com.ichezzy.evolutionboost.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Speicher je Spieler: letzter Claim-Zeitpunkt pro Typ (epoch seconds). */
public final class RewardData {
    public Map<UUID, Map<String, Long>> lastClaims = new HashMap<>();
}
