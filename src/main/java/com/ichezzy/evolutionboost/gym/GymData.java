package com.ichezzy.evolutionboost.gym;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.GymConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * World-basierte Daten für das Gym-System.
 * 
 * Pfad: <world>/evolutionboost/gym_data.json
 * 
 * Enthält:
 * - Battle-Historie
 * - Monthly Reward Claims
 * - Player Stats
 * - Leader Battle-Counter
 * - Registrierte Leader-Teams + Rules
 */
public final class GymData {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()  // Wichtig: null-Werte müssen serialisiert werden
            .create();

    private static GymData INSTANCE;
    private static MinecraftServer SERVER;

    // ==================== Daten-Strukturen ====================

    /** Alle Battles (für Statistiken) */
    public List<BattleRecord> battles = new ArrayList<>();

    /** 
     * Monthly Reward Claims: "YYYY-MM" -> "gymType" -> [player UUIDs die geclaimt haben]
     */
    public Map<String, Map<String, Set<String>>> monthlyRewardsClaimed = new HashMap<>();

    /**
     * Leader Battle Counter pro Monat: "YYYY-MM" -> "leaderUUID" -> battleCount
     */
    public Map<String, Map<String, Integer>> leaderBattlesPerMonth = new HashMap<>();

    /**
     * Player Stats (persistent)
     */
    public Map<String, PlayerGymStats> playerStats = new HashMap<>();

    /**
     * Registrierte Leader-Teams + Rules: "gymType" -> LeaderTeamData
     */
    public Map<String, LeaderTeamData> leaderTeams = new HashMap<>();

    // ==================== Record-Klassen ====================

    public static class BattleRecord {
        public String id;
        public String challengerUUID;
        public String challengerName;
        public String leaderUUID;
        public String leaderName;
        public String gymType;
        public String timestamp;
        public BattleResult result;
        public boolean rewardsClaimed;

        public BattleRecord() {}

        public BattleRecord(String challengerUUID, String challengerName,
                           String leaderUUID, String leaderName,
                           String gymType, BattleResult result) {
            this.id = UUID.randomUUID().toString();
            this.challengerUUID = challengerUUID;
            this.challengerName = challengerName;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.gymType = gymType;
            this.timestamp = Instant.now().toString();
            this.result = result;
            this.rewardsClaimed = false;
        }
    }

    public enum BattleResult {
        CHALLENGER_WIN,
        LEADER_WIN,
        DRAW,
        CANCELLED
    }

    public static class PlayerGymStats {
        public int totalBattles = 0;
        public int wins = 0;
        public int losses = 0;
        public Set<String> badgesEarned = new HashSet<>();
        public int battlesAsLeader = 0;
        public int winsAsLeader = 0;

        public double getWinRate() {
            if (totalBattles == 0) return 0;
            return (double) wins / totalBattles * 100;
        }

        public double getLeaderWinRate() {
            if (battlesAsLeader == 0) return 0;
            return (double) winsAsLeader / battlesAsLeader * 100;
        }
    }

    /**
     * Gespeichertes Team und Rules eines Gym Leaders.
     */
    public static class LeaderTeamData {
        /** Leader UUID */
        public String leaderUUID;
        
        /** Leader Name */
        public String leaderName;
        
        /** Battle-Format: "singles" oder "doubles" - null bis explizit gesetzt */
        public String battleFormat;
        
        /** Level Cap: 50 oder 100 (0 = kein Cap) */
        public int levelCap = 50;
        
        /** Wann wurde das Team zuletzt geändert? (ISO-8601) */
        public String lastTeamChange;
        
        /** Wann wurden die Rules zuletzt geändert? (ISO-8601) */
        public String lastRulesChange;
        
        /** Die Pokémon im Team (Species + Form) */
        public List<TeamPokemon> team = new ArrayList<>();

        public LeaderTeamData() {}

        public LeaderTeamData(String leaderUUID, String leaderName) {
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.battleFormat = null;  // Wird erst bei setGymRules() gesetzt
            this.levelCap = 50;
            this.lastTeamChange = Instant.now().toString();
            this.lastRulesChange = null;  // Wird erst bei setGymRules() gesetzt
            this.team = new ArrayList<>();
        }
    }

    /**
     * Ein Pokémon im gespeicherten Team.
     */
    public static class TeamPokemon {
        /** Species (z.B. "pikachu") */
        public String species;
        
        /** Form (z.B. "alolan", "" für normal) */
        public String form = "";
        
        /** Level beim Registrieren */
        public int level;
        
        /** Pokémon UUID (für Identifikation) */
        public String pokemonUUID;

        public TeamPokemon() {}

        public TeamPokemon(String species, String form, int level, String pokemonUUID) {
            this.species = species;
            this.form = form != null ? form : "";
            this.level = level;
            this.pokemonUUID = pokemonUUID;
        }

        /**
         * Erstellt einen einzigartigen Key für Vergleiche (species-form).
         */
        public String getKey() {
            if (form == null || form.isEmpty()) {
                return species.toLowerCase();
            }
            return species.toLowerCase() + "-" + form.toLowerCase();
        }
    }

    // ==================== Singleton & IO ====================

    private GymData() {}

    public static void init(MinecraftServer server) {
        SERVER = server;
        INSTANCE = load();
        EvolutionBoost.LOGGER.info("[gym] GymData initialized");
    }

    public static GymData get() {
        if (INSTANCE == null) {
            INSTANCE = new GymData();
        }
        return INSTANCE;
    }

    private static Path getDataFile() {
        if (SERVER == null) {
            return Path.of("config", "evolutionboost", "gym", "gym_data.json");
        }
        Path worldDir = SERVER.getWorldPath(LevelResource.ROOT).resolve("evolutionboost");
        try { Files.createDirectories(worldDir); } catch (Exception ignored) {}
        return worldDir.resolve("gym_data.json");
    }

    private static GymData load() {
        Path file = getDataFile();
        if (Files.exists(file)) {
            try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                GymData data = GSON.fromJson(br, GymData.class);
                if (data != null) {
                    data.ensureDefaults();
                    return data;
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[gym] Failed to load GymData: {}", e.getMessage());
            }
        }
        return new GymData();
    }

    public static void save() {
        if (INSTANCE == null) return;
        try {
            Path file = getDataFile();
            Files.createDirectories(file.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[gym] Failed to save GymData: {}", e.getMessage());
        }
    }

    private void ensureDefaults() {
        if (battles == null) battles = new ArrayList<>();
        if (monthlyRewardsClaimed == null) monthlyRewardsClaimed = new HashMap<>();
        if (leaderBattlesPerMonth == null) leaderBattlesPerMonth = new HashMap<>();
        if (playerStats == null) playerStats = new HashMap<>();
        if (leaderTeams == null) leaderTeams = new HashMap<>();
        
        // Migration: Alte Daten ohne levelCap
        for (LeaderTeamData data : leaderTeams.values()) {
            if (data.levelCap == 0) data.levelCap = 50;
            // lastRulesChange bleibt null bis Rules explizit gesetzt werden
        }
    }

    // ==================== Team Registration ====================

    /**
     * Registriert das Team eines Leaders.
     * Setzt Rules zurück wenn: neuer Eintrag, neuer Leader, oder Leader war nicht registriert.
     */
    public void registerLeaderTeam(String gymType, ServerPlayer leader, List<TeamPokemon> team) {
        String key = gymType.toLowerCase();
        LeaderTeamData teamData = leaderTeams.get(key);
        
        boolean isNewEntry = (teamData == null);
        boolean isNewLeader = teamData != null && !leader.getStringUUID().equals(teamData.leaderUUID);
        
        // Prüfe ob der Leader vorher als "nicht registriert" markiert war
        // Das bedeutet, er muss ALLES neu machen (Team + Rules)
        GymConfig.GymEntry gymEntry = GymConfig.get().getGym(gymType);
        boolean wasNotRegistered = gymEntry != null && !gymEntry.leaderRegistered;
        
        if (isNewEntry) {
            teamData = new LeaderTeamData(leader.getStringUUID(), leader.getName().getString());
            leaderTeams.put(key, teamData);
        }
        
        // Wenn neuer Leader ODER Leader war nicht registriert -> Rules zurücksetzen
        if (isNewLeader || wasNotRegistered) {
            teamData.battleFormat = null;
            teamData.lastRulesChange = null;
            EvolutionBoost.LOGGER.info("[gym] Reset rules for {} Gym (new leader or re-registration)", gymType);
        }
        
        teamData.leaderUUID = leader.getStringUUID();
        teamData.leaderName = leader.getName().getString();
        teamData.team = team;
        teamData.lastTeamChange = Instant.now().toString();
        
        save();
        EvolutionBoost.LOGGER.info("[gym] Registered team for {} Gym Leader {} ({} Pokemon)", 
                gymType, leader.getName().getString(), team.size());
    }

    /**
     * Setzt die Rules für ein Gym (Format + Level Cap).
     */
    public void setGymRules(String gymType, String battleFormat, int levelCap) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData != null) {
            teamData.battleFormat = battleFormat;
            teamData.levelCap = levelCap;
            teamData.lastRulesChange = Instant.now().toString();
            save();
            EvolutionBoost.LOGGER.info("[gym] Updated rules for {} Gym: format={}, levelCap={}", 
                    gymType, battleFormat, levelCap);
        }
    }

    /**
     * Holt das registrierte Team eines Leaders.
     */
    public LeaderTeamData getLeaderTeam(String gymType) {
        return leaderTeams.get(gymType.toLowerCase());
    }

    /**
     * Entfernt das registrierte Team (wenn Leader entfernt wird).
     */
    public void removeLeaderTeam(String gymType) {
        leaderTeams.remove(gymType.toLowerCase());
        save();
    }

    /**
     * Setzt nur das Team zurück (nicht die Rules).
     */
    public void resetLeaderTeam(String gymType) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData != null) {
            teamData.team = new ArrayList<>();
            teamData.lastTeamChange = null;
            save();
            
            // Auch in Config als nicht-registriert markieren
            GymConfig.GymEntry gym = GymConfig.get().getGym(gymType);
            if (gym != null) {
                gym.leaderRegistered = false;
                GymConfig.save();
            }
            
            EvolutionBoost.LOGGER.info("[gym] Reset team for {} Gym", gymType);
        }
    }

    /**
     * Setzt die Rules zurück (erlaubt sofortige Änderung).
     */
    public void resetGymRules(String gymType) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData != null) {
            teamData.lastRulesChange = null;
            save();
            EvolutionBoost.LOGGER.info("[gym] Reset rules timer for {} Gym", gymType);
        }
    }

    /**
     * Prüft ob ein Leader sein Team ändern darf (basierend auf Season-Intervall).
     */
    public boolean canChangeTeam(String gymType) {
        LeaderTeamData team = leaderTeams.get(gymType.toLowerCase());
        if (team == null || team.lastTeamChange == null) return true;

        GymConfig cfg = GymConfig.get();
        return cfg.canChangeTeamOrRules(team.lastTeamChange);
    }

    /**
     * Prüft ob ein Leader die Rules ändern darf (basierend auf Season-Intervall).
     * Erstes Mal Rules setzen ist IMMER erlaubt.
     */
    public boolean canChangeRules(String gymType) {
        LeaderTeamData team = leaderTeams.get(gymType.toLowerCase());
        if (team == null) return true;
        
        // Noch nie Rules gesetzt -> erlaubt
        if (team.battleFormat == null) return true;
        if (team.lastRulesChange == null) return true;
        
        // Wenn lastRulesChange == lastTeamChange, wurden sie im alten Konstruktor 
        // gleichzeitig gesetzt -> erstes Mal Rules setzen erlauben
        if (team.lastRulesChange.equals(team.lastTeamChange)) return true;

        GymConfig cfg = GymConfig.get();
        return cfg.canChangeTeamOrRules(team.lastRulesChange);
    }

    /**
     * Gibt eine Beschreibung wann die nächste Team-Änderung möglich ist.
     */
    public String getNextTeamChangeDescription(String gymType) {
        LeaderTeamData team = leaderTeams.get(gymType.toLowerCase());
        if (team == null || team.lastTeamChange == null) return null;

        return GymConfig.get().getNextChangeAllowedDescription(team.lastTeamChange);
    }

    /**
     * Gibt eine Beschreibung wann die nächste Rules-Änderung möglich ist.
     */
    public String getNextRulesChangeDescription(String gymType) {
        LeaderTeamData team = leaderTeams.get(gymType.toLowerCase());
        if (team == null) return null;
        
        // Noch nie Rules gesetzt -> keine Beschränkung
        if (team.battleFormat == null) return null;
        if (team.lastRulesChange == null) return null;
        
        // Wenn lastRulesChange == lastTeamChange, wurden sie gleichzeitig gesetzt
        // -> erstes Mal Rules setzen, keine Beschränkung
        if (team.lastRulesChange.equals(team.lastTeamChange)) return null;

        return GymConfig.get().getNextChangeAllowedDescription(team.lastRulesChange);
    }

    /**
     * @deprecated Verwendet das neue Season-System. Verwende getNextTeamChangeDescription().
     */
    @Deprecated
    public int getDaysUntilTeamChange(String gymType) {
        return canChangeTeam(gymType) ? 0 : 1;
    }

    /**
     * @deprecated Verwendet das neue Season-System. Verwende getNextRulesChangeDescription().
     */
    @Deprecated
    public int getDaysUntilRulesChange(String gymType) {
        return canChangeRules(gymType) ? 0 : 1;
    }

    // ==================== Team Validation ====================

    /**
     * Validiert ob der Leader das registrierte Team hat.
     * @return null wenn valid, sonst Fehlermeldung
     */
    public String validateLeaderTeam(ServerPlayer leader, String gymType) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData == null || teamData.team == null || teamData.team.isEmpty()) {
            return "No team registered for this gym.";
        }

        // Hole aktuelles Team des Leaders
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(leader);
        
        // Sammle aktuelle Pokémon (Species-Form Keys)
        Set<String> currentTeamKeys = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null) {
                String species = pokemon.getSpecies().getName().toLowerCase();
                String form = pokemon.getForm().getName();
                if (form.equalsIgnoreCase("normal") || form.equalsIgnoreCase("default")) {
                    form = "";
                }
                String key = form.isEmpty() ? species : species + "-" + form.toLowerCase();
                currentTeamKeys.add(key);
            }
        }

        // Sammle registrierte Pokémon Keys
        Set<String> registeredKeys = new HashSet<>();
        for (TeamPokemon tp : teamData.team) {
            registeredKeys.add(tp.getKey());
        }

        // Prüfe ob alle registrierten Pokémon vorhanden sind
        if (!currentTeamKeys.equals(registeredKeys)) {
            // Finde fehlende Pokémon
            Set<String> missing = new HashSet<>(registeredKeys);
            missing.removeAll(currentTeamKeys);
            
            Set<String> extra = new HashSet<>(currentTeamKeys);
            extra.removeAll(registeredKeys);
            
            StringBuilder sb = new StringBuilder("Team mismatch! ");
            if (!missing.isEmpty()) {
                sb.append("Missing: ").append(String.join(", ", missing)).append(". ");
            }
            if (!extra.isEmpty()) {
                sb.append("Not registered: ").append(String.join(", ", extra)).append(".");
            }
            return sb.toString();
        }

        return null; // Valid
    }

    /**
     * Holt das Battle-Format für ein Gym.
     */
    public String getBattleFormat(String gymType) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData != null && teamData.battleFormat != null) {
            return teamData.battleFormat;
        }
        return "singles";
    }

    /**
     * Holt das Level-Cap für ein Gym.
     */
    public int getLevelCap(String gymType) {
        LeaderTeamData teamData = leaderTeams.get(gymType.toLowerCase());
        if (teamData != null && teamData.levelCap > 0) {
            return teamData.levelCap;
        }
        return 50; // Default
    }

    // ==================== Battle Recording ====================

    /**
     * Speichert einen Battle-Record.
     */
    public void recordBattle(BattleRecord record) {
        battles.add(record);
        
        // Leader Battle Counter erhöhen
        String month = getCurrentMonth();
        leaderBattlesPerMonth.computeIfAbsent(month, k -> new HashMap<>());
        Map<String, Integer> monthMap = leaderBattlesPerMonth.get(month);
        monthMap.merge(record.leaderUUID, 1, Integer::sum);

        // Player Stats updaten
        updateStatsAfterBattle(record);

        save();
    }

    private void updateStatsAfterBattle(BattleRecord record) {
        // Challenger Stats
        PlayerGymStats challengerStats = playerStats.computeIfAbsent(
                record.challengerUUID, k -> new PlayerGymStats());
        challengerStats.totalBattles++;
        if (record.result == BattleResult.CHALLENGER_WIN) {
            challengerStats.wins++;
        } else if (record.result == BattleResult.LEADER_WIN) {
            challengerStats.losses++;
        }

        // Leader Stats
        PlayerGymStats leaderStats = playerStats.computeIfAbsent(
                record.leaderUUID, k -> new PlayerGymStats());
        leaderStats.battlesAsLeader++;
        if (record.result == BattleResult.LEADER_WIN) {
            leaderStats.winsAsLeader++;
        }
    }

    // ==================== Monthly Rewards ====================

    public boolean hasClaimedThisMonth(String playerUUID, String gymType) {
        String month = getCurrentMonth();
        Map<String, Set<String>> gymMap = monthlyRewardsClaimed.get(month);
        if (gymMap == null) return false;
        Set<String> claimed = gymMap.get(gymType);
        return claimed != null && claimed.contains(playerUUID);
    }

    public void markRewardClaimed(String playerUUID, String gymType) {
        String month = getCurrentMonth();
        monthlyRewardsClaimed
                .computeIfAbsent(month, k -> new HashMap<>())
                .computeIfAbsent(gymType, k -> new HashSet<>())
                .add(playerUUID);
        save();
    }

    public void addBadge(String playerUUID, String gymType) {
        PlayerGymStats stats = playerStats.computeIfAbsent(playerUUID, k -> new PlayerGymStats());
        stats.badgesEarned.add(gymType);
        save();
    }

    // ==================== Leader Stats ====================

    public int getLeaderBattlesThisMonth(String leaderUUID) {
        String month = getCurrentMonth();
        Map<String, Integer> monthMap = leaderBattlesPerMonth.get(month);
        if (monthMap == null) return 0;
        return monthMap.getOrDefault(leaderUUID, 0);
    }

    public boolean leaderHasEnoughBattles(String leaderUUID) {
        int required = GymConfig.get().leaderMinBattlesForMonthlyReward;
        return getLeaderBattlesThisMonth(leaderUUID) >= required;
    }

    // ==================== Player Stats ====================

    public PlayerGymStats getPlayerStats(String playerUUID) {
        return playerStats.computeIfAbsent(playerUUID, k -> new PlayerGymStats());
    }

    public boolean hasBadge(String playerUUID, String gymType) {
        PlayerGymStats stats = playerStats.get(playerUUID);
        return stats != null && stats.badgesEarned.contains(gymType);
    }

    /**
     * Setzt die Stats eines Spielers zurück.
     */
    public void resetPlayerStats(String playerUUID) {
        playerStats.remove(playerUUID);
        save();
        EvolutionBoost.LOGGER.info("[gym] Reset stats for player {}", playerUUID);
    }

    /**
     * Setzt die Stats aller Spieler zurück.
     */
    public void resetAllPlayerStats() {
        playerStats.clear();
        save();
        EvolutionBoost.LOGGER.info("[gym] Reset all player stats");
    }

    // ==================== Utility ====================

    private static String getCurrentMonth() {
        return YearMonth.now(ZoneId.of("Europe/Berlin")).toString();
    }

    /**
     * Cleanup: Alte Daten entfernen.
     */
    public void cleanup(int keepMonths) {
        YearMonth cutoff = YearMonth.now().minusMonths(keepMonths);
        
        monthlyRewardsClaimed.keySet().removeIf(month -> {
            try {
                return YearMonth.parse(month).isBefore(cutoff);
            } catch (Exception e) {
                return false;
            }
        });

        leaderBattlesPerMonth.keySet().removeIf(month -> {
            try {
                return YearMonth.parse(month).isBefore(cutoff);
            } catch (Exception e) {
                return false;
            }
        });

        Instant cutoffInstant = cutoff.atDay(1).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        battles.removeIf(b -> {
            try {
                return Instant.parse(b.timestamp).isBefore(cutoffInstant);
            } catch (Exception e) {
                return false;
            }
        });

        save();
    }
}
