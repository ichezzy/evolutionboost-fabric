package com.ichezzy.evolutionboost.reward;

import java.util.*;

/**
 * Persistentes, menschenlesbares Format:
 *
 * {
 *   "players": [
 *     {
 *       "uuid": "5a554069-01aa-4f8c-8a71-2565b46d31f1",
 *       "name": "ichezzy",
 *       "last": {
 *         "DAILY":   "2025-11-13T17:45:36Z",
 *         "WEEKLY":  "2025-11-13T17:46:40Z",
 *         "MONTHLY_DONATOR": "2025-11-01T00:00:03Z",
 *         "MONTHLY_GYM":     "2025-11-01T00:00:03Z"
 *       },
 *       "claims": [
 *         {"type":"DAILY","at":"2025-11-13T17:45:36Z"},
 *         {"type":"WEEKLY","at":"2025-11-13T17:46:40Z"}
 *       ]
 *     }
 *   ]
 * }
 */
public final class RewardData {

    /** Einzelner Claim-Eintrag (Historie). */
    public static final class Claim {
        public String type;
        public String at;   // ISO-8601 (Instant.toString)
        public Claim() {}
        public Claim(String type, String at) { this.type = type; this.at = at; }
    }

    /** Persistenter Zustand pro Spieler. */
    public static final class PlayerEntry {
        public String uuid;
        public String name;
        /** Letzte Claims je Typ in ISO-8601 (z. B. "2025-11-13T17:45:36Z"). */
        public Map<String, String> last = new HashMap<>();
        /** Historie (optional, kann leer bleiben). */
        public List<Claim> claims = new ArrayList<>();
    }

    /** Root: Liste aller Spielerzustände. */
    public List<PlayerEntry> players = new ArrayList<>();
}
