package com.ichezzy.evolutionboost.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.RewardConfig;
import com.ichezzy.evolutionboost.configs.RewardConfig.RewardItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewards mit serverseitiger Realzeit (CET/CEST) + weltgebundener, menschenlesbarer Persistenz.
 *
 * - Eligibility (Properties): /config/evolutionboost/rewards/eligibility.properties
 *   -> wer ist DONATOR (Copper/Silver/Gold) / GYM / STAFF (RewardCommand nutzt das)
 *
 * - State (JSON):            <world>/evolutionboost/rewards_state.json
 *   -> wann hat welcher Spieler was zuletzt geclaimt
 *
 * - Item-Definitionen:       RewardConfig (config/evolutionboost/rewards/rewards.json)
 *   -> welche Items bei DAILY/WEEKLY/MONTHLY_* droppen
 */
public final class RewardManager {
    private RewardManager() {}

    /* ================== Storage / Pfade ================== */

    private static final ZoneId ZONE_CET = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE_CET);

    private static final Map<UUID, PlayerRewardState> STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_NAMES = new ConcurrentHashMap<>();

    // Donator-Tiers (Case-insensitive Namen)
    private static final Set<String> ALLOWED_DONATOR_COPPER = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> ALLOWED_DONATOR_SILVER = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> ALLOWED_DONATOR_GOLD   = Collections.synchronizedSet(new HashSet<>());

    private static final Set<String> ALLOWED_GYM   = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> ALLOWED_STAFF = Collections.synchronizedSet(new HashSet<>());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static MinecraftServer SERVER; // gesetzt in init(server)

    // ---- CONFIG-Pfade (Eligibility) unter /config/evolutionboost/rewards/ ----
    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(EvolutionBoost.MOD_ID);
    }

    private static Path rewardsConfigDir() {
        Path p = configDir().resolve("rewards");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    private static Path eligibilityFile() {
        return rewardsConfigDir().resolve("eligibility.properties");
    }

    // ---- WORLD-Pfade (STATE) ----
    private static Path worldRootDir() {
        if (SERVER == null) {
            return configDir();
        }
        Path root = SERVER.getWorldPath(LevelResource.ROOT);
        Path sub  = root.resolve("evolutionboost");
        try { Files.createDirectories(sub); } catch (IOException ignored) {}
        return sub;
    }

    private static Path stateFileWorld() {
        return worldRootDir().resolve("rewards_state.json");
    }

    // Alt: alter Speicherort (nur Migration)
    private static Path stateFileLegacyConfig() {
        return configDir().resolve("rewards_state.json");
    }

    public static void init(MinecraftServer server) {
        SERVER = server;
        try { Files.createDirectories(configDir()); } catch (IOException ignored) {}

        // Eligibility-Listen laden/erzeugen
        loadEligibility();

        // NEU: Rewards-Config laden/erzeugen → sorgt dafür, dass rewards.json beim Start angelegt wird
        RewardConfig.get();

        // State migrieren + laden
        migrateLegacyStateIfNeeded();
        loadState();
    }

    public static void saveAll() {
        saveState();
        saveEligibility();
        // RewardConfig wird nur über RewardConfig.save() geschrieben, falls du später etwas änderst.
    }

    /** Explizites Reload z.B. über /eb rewards reload. */
    public static void reloadEligibilityFromDisk() {
        loadEligibility();
    }

    /* ================== Public API ================== */

    /** Login-Hinweise. */
    public static void onPlayerJoin(ServerPlayer p) {
        UUID id = p.getUUID();
        STATE.computeIfAbsent(id, k -> new PlayerRewardState());
        LAST_NAMES.put(id, safeName(p));

        boolean readyDaily   = isReady(p, RewardType.DAILY);
        boolean readyWeekly  = isReady(p, RewardType.WEEKLY);
        boolean readyDonator = isEligibleMonthly(p, RewardType.MONTHLY_DONATOR) && isReady(p, RewardType.MONTHLY_DONATOR);
        boolean readyGym     = isEligibleMonthly(p, RewardType.MONTHLY_GYM)     && isReady(p, RewardType.MONTHLY_GYM);
        boolean readyStaff   = isEligibleMonthly(p, RewardType.MONTHLY_STAFF)   && isReady(p, RewardType.MONTHLY_STAFF);

        if (readyDaily || readyWeekly || readyDonator || readyGym || readyStaff) {
            p.sendSystemMessage(
                    Component.literal("[REWARDS] You have rewards to claim!")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
            );
            p.sendSystemMessage(
                    Component.literal("Tip: Use /eb rewards info  or  /eb rewards claim <type>")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
            );
        } else {
            p.sendSystemMessage(
                    Component.literal("[REWARDS] You currently have no rewards to claim.")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
    }

    /** Schöne Info-Ausgabe je Zeile/Farbe. */
    public static void sendInfo(CommandSourceStack src, ServerPlayer p) {
        sendOneInfo(src, p, RewardType.DAILY,  ChatFormatting.YELLOW, "Daily");
        sendOneInfo(src, p, RewardType.WEEKLY, ChatFormatting.AQUA,   "Weekly");

        if (isEligibleMonthly(p, RewardType.MONTHLY_DONATOR)) {
            DonatorTier tier = getDonatorTier(p);
            String label = switch (tier) {
                case COPPER -> "Monthly (Donator Copper)";
                case SILVER -> "Monthly (Donator Silver)";
                case GOLD   -> "Monthly (Donator Gold)";
                default     -> "Monthly (Donator)";
            };
            sendOneInfo(src, p, RewardType.MONTHLY_DONATOR, ChatFormatting.LIGHT_PURPLE, label);
        } else {
            src.sendSuccess(() -> Component.literal("Monthly (Donator): not eligible").withStyle(ChatFormatting.RED), false);
        }

        if (isEligibleMonthly(p, RewardType.MONTHLY_GYM)) {
            sendOneInfo(src, p, RewardType.MONTHLY_GYM, ChatFormatting.LIGHT_PURPLE, "Monthly (Gym)");
        } else {
            src.sendSuccess(() -> Component.literal("Monthly (Gym): not eligible").withStyle(ChatFormatting.RED), false);
        }

        if (isEligibleMonthly(p, RewardType.MONTHLY_STAFF)) {
            sendOneInfo(src, p, RewardType.MONTHLY_STAFF, ChatFormatting.GOLD, "Monthly (Staff)");
        } else {
            src.sendSuccess(() -> Component.literal("Monthly (Staff): not eligible").withStyle(ChatFormatting.RED), false);
        }
    }

    private static void sendOneInfo(CommandSourceStack src, ServerPlayer p, RewardType t, ChatFormatting color, String label) {
        long sec = secondsUntilNext(p, t);
        if (sec == 0) {
            src.sendSuccess(() -> Component.literal(label + ": ready now").withStyle(color, ChatFormatting.BOLD), false);
        } else {
            src.sendSuccess(() -> Component.literal(label + ": " + human(sec) + " until reset").withStyle(color), false);
        }
    }

    /** Claim-Logik + rote Meldungen bei fehlender Berechtigung. */
    public static boolean claim(ServerPlayer p, RewardType type) {
        UUID id = p.getUUID();
        LAST_NAMES.put(id, safeName(p));

        // Eligibility nur für Monthly (Donator / Gym / Staff)
        if ((type == RewardType.MONTHLY_DONATOR || type == RewardType.MONTHLY_GYM || type == RewardType.MONTHLY_STAFF)
                && !isEligibleMonthly(p, type)) {
            p.sendSystemMessage(Component.literal("[Rewards] You are not eligible for this monthly reward.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        long s = secondsUntilNext(p, type);
        if (s > 0) {
            p.sendSystemMessage(Component.literal("[Rewards] Not ready. ")
                    .append(Component.literal(human(s) + " remaining.").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // === Items aus RewardConfig geben ===
        grantConfiguredRewards(p, type);

        // Timestamp & History
        stampClaim(id, type);
        p.sendSystemMessage(Component.literal("[Rewards] Claimed ")
                .append(Component.literal(typeReadable(type)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(TS_FMT.format(Instant.now())).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /**
     * Holt für den gegebenen RewardType die Items aus der RewardConfig
     * und gibt sie dem Spieler.
     *
     * Mapping:
     *   DAILY                    -> key "DAILY"
     *   WEEKLY                   -> key "WEEKLY"
     *   MONTHLY_DONATOR (Copper) -> key "MONTHLY_DONATOR_COPPER"
     *   MONTHLY_DONATOR (Silver) -> key "MONTHLY_DONATOR_SILVER"
     *   MONTHLY_DONATOR (Gold)   -> key "MONTHLY_DONATOR_GOLD"
     *   MONTHLY_GYM              -> key "MONTHLY_GYM"
     *   MONTHLY_STAFF            -> key "MONTHLY_STAFF"
     */
    private static void grantConfiguredRewards(ServerPlayer p, RewardType type) {
        RewardConfig cfg = RewardConfig.get();

        String key;
        if (type == RewardType.MONTHLY_DONATOR) {
            DonatorTier tier = getDonatorTier(p);
            key = switch (tier) {
                case COPPER -> "MONTHLY_DONATOR_COPPER";
                case SILVER -> "MONTHLY_DONATOR_SILVER";
                case GOLD   -> "MONTHLY_DONATOR_GOLD";
                default     -> "MONTHLY_DONATOR"; // Fallback für alte Configs
            };
        } else {
            key = type.name(); // z.B. DAILY, WEEKLY, MONTHLY_GYM, MONTHLY_STAFF
        }

        List<RewardItem> items = cfg.rewards.get(key);
        if (items == null || items.isEmpty()) {
            EvolutionBoost.LOGGER.warn("[rewards] No rewards configured for type '{}', nothing granted.", key);
            return;
        }

        for (RewardItem ri : items) {
            if (ri == null || ri.id == null || ri.id.isBlank()) continue;
            int count = ri.count <= 0 ? 1 : ri.count;
            ItemStack stack = stackFromId(p, ri.id, count);
            if (!stack.isEmpty()) {
                giveOrDrop(p, stack);
            } else {
                EvolutionBoost.LOGGER.warn("[rewards] Missing or invalid item '{}' for type '{}', skipping.", ri.id, key);
            }
        }
    }

    /* ================== Cooldowns & Eligibility ================== */

    private static boolean isReady(ServerPlayer p, RewardType t) {
        return secondsUntilNext(p, t) == 0;
    }

    public static long secondsUntilNext(ServerPlayer p, RewardType t) {
        return secondsUntilNext(p.getUUID(), t);
    }

    /** Ready, wenn lastClaim < lastResetBoundary; sonst Cooldown bis nextResetBoundary. */
    public static long secondsUntilNext(UUID uuid, RewardType t) {
        PlayerRewardState st = STATE.computeIfAbsent(uuid, id -> new PlayerRewardState());
        Instant now = Instant.now();
        Instant last = st.getLast(t);

        Instant lastReset = lastResetTime(t, now);
        if (last == null || last.isBefore(lastReset)) {
            return 0L;
        }

        Instant nextReset = nextResetTime(t, now);
        long sec = Duration.between(now, nextReset).getSeconds();
        return Math.max(sec, 0L);
    }

    public static void resetCooldown(UUID uuid, RewardType t) {
        PlayerRewardState st = STATE.computeIfAbsent(uuid, id -> new PlayerRewardState());
        st.clear(t);
        saveState();
    }

    /** Alte Methode (Kompatibilität). */
    public static void setMonthlyEligibility(String playerName, boolean donator, boolean gym) {
        setDonatorEligibility(playerName, donator);
        setGymEligibility(playerName, gym);
    }

    /** Backwards-Compat: true = COPPER, false = NONE. */
    public static void setDonatorEligibility(String playerName, boolean donator) {
        setDonatorTier(playerName, donator ? DonatorTier.COPPER : DonatorTier.NONE);
    }

    public static void setGymEligibility(String playerName, boolean gym) {
        String key = normalizeName(playerName);
        synchronized (ALLOWED_GYM) {
            if (gym) ALLOWED_GYM.add(key);
            else ALLOWED_GYM.remove(key);
        }
        saveEligibility();
    }

    /** NEU: Staff-Eligibility. */
    public static void setStaffEligibility(String playerName, boolean staff) {
        String key = normalizeName(playerName);
        synchronized (ALLOWED_STAFF) {
            if (staff) ALLOWED_STAFF.add(key);
            else ALLOWED_STAFF.remove(key);
        }
        saveEligibility();
    }

    private static boolean isEligibleMonthly(ServerPlayer p, RewardType t) {
        String key = normalizeName(p.getGameProfile().getName());
        if (t == RewardType.MONTHLY_DONATOR) return getDonatorTier(p) != DonatorTier.NONE;
        if (t == RewardType.MONTHLY_GYM)     return ALLOWED_GYM.contains(key);
        if (t == RewardType.MONTHLY_STAFF)   return ALLOWED_STAFF.contains(key);
        return true;
    }

    /* ================== Reset-Berechnungen ================== */

    private static Instant lastResetTime(RewardType t, Instant ref) {
        ZonedDateTime zdt = ref.atZone(ZONE_CET);
        switch (t) {
            case DAILY -> {
                ZonedDateTime todayMidnight = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (zdt.isBefore(todayMidnight)) return todayMidnight.minusDays(1).toInstant();
                return todayMidnight.toInstant();
            }
            case WEEKLY -> {
                ZonedDateTime weekMidnight = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .with(java.time.DayOfWeek.MONDAY);
                while (zdt.isBefore(weekMidnight)) weekMidnight = weekMidnight.minusWeeks(1);
                return weekMidnight.toInstant();
            }
            case MONTHLY_DONATOR, MONTHLY_GYM, MONTHLY_STAFF -> {
                ZonedDateTime firstOfMonth = zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (zdt.isBefore(firstOfMonth)) firstOfMonth = firstOfMonth.minusMonths(1);
                return firstOfMonth.toInstant();
            }
            default -> {
                return ref;
            }
        }
    }

    private static Instant nextResetTime(RewardType t, Instant ref) {
        ZonedDateTime zdt = ref.atZone(ZONE_CET);
        switch (t) {
            case DAILY -> {
                ZonedDateTime next = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (!next.isAfter(zdt)) next = next.plusDays(1);
                return next.toInstant();
            }
            case WEEKLY -> {
                ZonedDateTime next = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .with(java.time.DayOfWeek.MONDAY);
                while (!next.isAfter(zdt)) next = next.plusWeeks(1);
                return next.toInstant();
            }
            case MONTHLY_DONATOR, MONTHLY_GYM, MONTHLY_STAFF -> {
                ZonedDateTime next = zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (!next.isAfter(zdt)) next = next.plusMonths(1);
                return next.toInstant();
            }
            default -> {
                return ref;
            }
        }
    }

    private static String human(long seconds) {
        long s = Math.max(0, seconds);
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String typeReadable(RewardType t) {
        return switch (t) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY_DONATOR -> "Monthly (Donator)";
            case MONTHLY_GYM -> "Monthly (Gym)";
            case MONTHLY_STAFF -> "Monthly (Staff)";
        };
    }

    private static String safeName(ServerPlayer p) {
        return (p == null || p.getGameProfile() == null) ? "-" : p.getGameProfile().getName();
    }

    /** Timestamp + History + Save. */
    private static void stampClaim(UUID uuid, RewardType t) {
        PlayerRewardState st = STATE.computeIfAbsent(uuid, id -> new PlayerRewardState());
        Instant now = Instant.now();
        st.setLast(t, now);
        st.addHistory(t, now);
        saveState();
    }

    /* ================== Item Helpers (extern & robust) ================== */

    private static void giveOrDrop(ServerPlayer p, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        p.getInventory().placeItemBackInInventory(stack);
    }

    private static void giveExternal(ServerPlayer p, String id, int count) {
        ItemStack s = stackFromId(p, id, count);
        if (!s.isEmpty()) giveOrDrop(p, s);
        else EvolutionBoost.LOGGER.warn("[rewards] Missing item '{}', skipping.", id);
    }

    private static void giveCrdKeysRaidPass(ServerPlayer p, int uses) {
        ItemStack s = stackFromId(p, "crdkeys:raid_pass_basic", 1);
        if (s.isEmpty()) {
            EvolutionBoost.LOGGER.warn("[rewards] Missing item 'crdkeys:raid_pass_basic', skipping.");
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("crdkeys_uses", uses);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        giveOrDrop(p, s);
    }

    private static ItemStack stackFromId(ServerPlayer p, String id, int count) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            Registry<Item> reg = p.serverLevel().registryAccess().registryOrThrow(Registries.ITEM);
            Item item = reg.getOptional(rl).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.warn("[rewards] Invalid item id '{}': {}", id, t.toString());
            return ItemStack.EMPTY;
        }
    }

    /* ================== Persistenz: Eligibility (Properties) ================== */

    private static void loadEligibility() {
        ALLOWED_DONATOR_COPPER.clear();
        ALLOWED_DONATOR_SILVER.clear();
        ALLOWED_DONATOR_GOLD.clear();
        ALLOWED_GYM.clear();
        ALLOWED_STAFF.clear();

        Path p = eligibilityFile();
        if (!Files.exists(p)) { saveEligibility(); return; }

        try (var r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(r);

            // Neue Tiers
            for (String n : props.getProperty("donator_copper", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_DONATOR_COPPER.add(k);
            }
            for (String n : props.getProperty("donator_silver", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_DONATOR_SILVER.add(k);
            }
            for (String n : props.getProperty("donator_gold", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_DONATOR_GOLD.add(k);
            }

            // Backwards-Compat: altes "donator" → COPPER, wenn keine Tiers gesetzt sind
            if (ALLOWED_DONATOR_COPPER.isEmpty() && ALLOWED_DONATOR_SILVER.isEmpty() && ALLOWED_DONATOR_GOLD.isEmpty()) {
                for (String n : props.getProperty("donator", "").split(",")) {
                    String k = normalizeName(n);
                    if (!k.isEmpty()) ALLOWED_DONATOR_COPPER.add(k);
                }
            }

            for (String n : props.getProperty("gym", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_GYM.add(k);
            }
            for (String n : props.getProperty("staff", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_STAFF.add(k);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to load eligibility: {}", e.getMessage());
        }
    }

    private static void saveEligibility() {
        try {
            Files.createDirectories(rewardsConfigDir());
            Properties props = new Properties();

            props.setProperty("donator_copper", String.join(",", ALLOWED_DONATOR_COPPER));
            props.setProperty("donator_silver", String.join(",", ALLOWED_DONATOR_SILVER));
            props.setProperty("donator_gold",   String.join(",", ALLOWED_DONATOR_GOLD));
            props.setProperty("gym",   String.join(",", ALLOWED_GYM));
            props.setProperty("staff", String.join(",", ALLOWED_STAFF));

            try (var w = Files.newBufferedWriter(eligibilityFile(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(w,
                        "Rewards eligibility (names, case-insensitive)\n" +
                                "donator_copper=a,b,c\n" +
                                "donator_silver=x,y\n" +
                                "donator_gold=z\n" +
                                "gym=g1,g2\n" +
                                "staff=s1,s2");
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to save eligibility: {}", e.getMessage());
        }
    }

    /* ================== Persistenz: STATE (weltgebunden, JSON) ================== */

    private static void migrateLegacyStateIfNeeded() {
        Path legacy = stateFileLegacyConfig();
        Path world  = stateFileWorld();
        if (Files.exists(world)) return;
        if (!Files.exists(legacy)) return;

        try {
            List<String> lines = Files.readAllLines(legacy, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split(";");
                if (parts.length < 1) continue;
                UUID id;
                try { id = UUID.fromString(parts[0]); } catch (IllegalArgumentException ex) { continue; }
                PlayerRewardState st = new PlayerRewardState();
                if (parts.length > 1 && !parts[1].isEmpty()) st.daily          = Instant.ofEpochSecond(Long.parseLong(parts[1]));
                if (parts.length > 2 && !parts[2].isEmpty()) st.weekly         = Instant.ofEpochSecond(Long.parseLong(parts[2]));
                if (parts.length > 3 && !parts[3].isEmpty()) st.monthlyDonator = Instant.ofEpochSecond(Long.parseLong(parts[3]));
                if (parts.length > 4 && !parts[4].isEmpty()) st.monthlyGym     = Instant.ofEpochSecond(Long.parseLong(parts[4]));
                // legacy hatte keinen staff
                STATE.put(id, st);
                LAST_NAMES.putIfAbsent(id, "-");
            }
            saveState();
            EvolutionBoost.LOGGER.info("[rewards] Migrated legacy rewards_state.json from /config to <world>/evolutionboost/.");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] legacy migration failed: {}", e.getMessage());
        }
    }

    private static void loadState() {
        STATE.clear();
        Path p = stateFileWorld();
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            RewardData data = GSON.fromJson(br, RewardData.class);
            if (data == null || data.players == null) return;

            for (RewardData.PlayerEntry pe : data.players) {
                if (pe == null || pe.uuid == null || pe.uuid.isEmpty()) continue;
                UUID id;
                try { id = UUID.fromString(pe.uuid); } catch (IllegalArgumentException ex) { continue; }
                PlayerRewardState st = new PlayerRewardState();

                if (pe.last != null) {
                    st.daily          = parseInstant(pe.last.get("DAILY"));
                    st.weekly         = parseInstant(pe.last.get("WEEKLY"));
                    st.monthlyDonator = parseInstant(pe.last.get("MONTHLY_DONATOR"));
                    st.monthlyGym     = parseInstant(pe.last.get("MONTHLY_GYM"));
                    st.monthlyStaff   = parseInstant(pe.last.get("MONTHLY_STAFF"));
                }
                if (pe.claims != null) {
                    for (RewardData.Claim c : pe.claims) {
                        if (c == null || c.type == null || c.at == null) continue;
                        try {
                            RewardType t = RewardType.valueOf(c.type);
                            Instant at = Instant.parse(c.at);
                            st.history.add(new HistoryEntry(t, at));
                        } catch (Exception ignored) {}
                    }
                }

                STATE.put(id, st);
                LAST_NAMES.put(id, pe.name == null ? "-" : pe.name);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to load state: {}", e.getMessage());
        }
    }

    private static void saveState() {
        try {
            Files.createDirectories(worldRootDir());

            RewardData out = new RewardData();
            for (Map.Entry<UUID, PlayerRewardState> e : STATE.entrySet()) {
                UUID id = e.getKey();
                PlayerRewardState st = e.getValue();

                RewardData.PlayerEntry pe = new RewardData.PlayerEntry();
                pe.uuid = id.toString();
                pe.name = LAST_NAMES.getOrDefault(id, "-");
                pe.last = new LinkedHashMap<>();

                if (st.daily          != null) pe.last.put("DAILY",            st.daily.toString());
                if (st.weekly         != null) pe.last.put("WEEKLY",           st.weekly.toString());
                if (st.monthlyDonator != null) pe.last.put("MONTHLY_DONATOR",  st.monthlyDonator.toString());
                if (st.monthlyGym     != null) pe.last.put("MONTHLY_GYM",      st.monthlyGym.toString());
                if (st.monthlyStaff   != null) pe.last.put("MONTHLY_STAFF",    st.monthlyStaff.toString());

                if (!st.history.isEmpty()) {
                    pe.claims = new ArrayList<>(st.history.size());
                    for (HistoryEntry h : st.history) {
                        pe.claims.add(new RewardData.Claim(h.type.name(), h.at.toString()));
                    }
                }

                out.players.add(pe);
            }

            Path file = stateFileWorld();
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(out, bw);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to save state: {}", e.getMessage());
        }
    }

    private static Instant parseInstant(String s) {
        try { return (s == null || s.isEmpty()) ? null : Instant.parse(s); }
        catch (Exception ex) { return null; }
    }

    // Liste der Rollen im Chat ausgeben: /rewards list <donator|donator_copper|donator_silver|donator_gold|gym|staff>
    public static void sendRoleList(CommandSourceStack src, String roleKey) {
        final Set<String> names;

        if ("donator".equalsIgnoreCase(roleKey)) {
            // Alle Donator-Spieler (alle Tiers zusammen)
            Set<String> all = new LinkedHashSet<>();
            all.addAll(ALLOWED_DONATOR_COPPER);
            all.addAll(ALLOWED_DONATOR_SILVER);
            all.addAll(ALLOWED_DONATOR_GOLD);
            names = all;
        } else if ("donator_copper".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_DONATOR_COPPER;
        } else if ("donator_silver".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_DONATOR_SILVER;
        } else if ("donator_gold".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_DONATOR_GOLD;
        } else if ("gym".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_GYM;
        } else if ("staff".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_STAFF;
        } else {
            src.sendSuccess(() -> Component.literal("[Rewards] Unknown role: " + roleKey)
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        if (names.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[Rewards] No entries for '" + roleKey + "'.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            return;
        }

        String joined = String.join(", ", names);
        src.sendSuccess(() -> Component.literal("[Rewards] " + roleKey + ": " + joined)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
    }

    /* ================== Interner State & Donator-Tier ================== */

    public enum DonatorTier {
        NONE,
        COPPER,
        SILVER,
        GOLD
    }

    public static DonatorTier getDonatorTier(ServerPlayer p) {
        String key = normalizeName(p.getGameProfile().getName());
        synchronized (ALLOWED_DONATOR_GOLD) {
            if (ALLOWED_DONATOR_GOLD.contains(key)) return DonatorTier.GOLD;
        }
        synchronized (ALLOWED_DONATOR_SILVER) {
            if (ALLOWED_DONATOR_SILVER.contains(key)) return DonatorTier.SILVER;
        }
        synchronized (ALLOWED_DONATOR_COPPER) {
            if (ALLOWED_DONATOR_COPPER.contains(key)) return DonatorTier.COPPER;
        }
        return DonatorTier.NONE;
    }

    /**
     * Setzt die Donator-Stufe und sorgt dafür, dass ein Spieler
     * nie gleichzeitig in mehreren Stufen ist.
     */
    public static void setDonatorTier(String playerName, DonatorTier tier) {
        String key = normalizeName(playerName);

        synchronized (ALLOWED_DONATOR_COPPER) {
            ALLOWED_DONATOR_COPPER.remove(key);
        }
        synchronized (ALLOWED_DONATOR_SILVER) {
            ALLOWED_DONATOR_SILVER.remove(key);
        }
        synchronized (ALLOWED_DONATOR_GOLD) {
            ALLOWED_DONATOR_GOLD.remove(key);
        }

        switch (tier) {
            case COPPER -> ALLOWED_DONATOR_COPPER.add(key);
            case SILVER -> ALLOWED_DONATOR_SILVER.add(key);
            case GOLD   -> ALLOWED_DONATOR_GOLD.add(key);
            case NONE   -> { /* nichts */ }
        }

        saveEligibility();
    }

    private static final class PlayerRewardState {
        Instant daily;
        Instant weekly;
        Instant monthlyDonator;
        Instant monthlyGym;
        Instant monthlyStaff; // NEU

        final List<HistoryEntry> history = new ArrayList<>();

        Instant getLast(RewardType t) {
            return switch (t) {
                case DAILY            -> daily;
                case WEEKLY           -> weekly;
                case MONTHLY_DONATOR  -> monthlyDonator;
                case MONTHLY_GYM      -> monthlyGym;
                case MONTHLY_STAFF    -> monthlyStaff;
            };
        }

        void setLast(RewardType t, Instant when) {
            switch (t) {
                case DAILY -> daily = when;
                case WEEKLY -> weekly = when;
                case MONTHLY_DONATOR -> monthlyDonator = when;
                case MONTHLY_GYM -> monthlyGym = when;
                case MONTHLY_STAFF -> monthlyStaff = when;
            }
        }

        void clear(RewardType t) {
            switch (t) {
                case DAILY -> daily = null;
                case WEEKLY -> weekly = null;
                case MONTHLY_DONATOR -> monthlyDonator = null;
                case MONTHLY_GYM -> monthlyGym = null;
                case MONTHLY_STAFF -> monthlyStaff = null;
            }
        }

        void addHistory(RewardType t, Instant at) {
            history.add(new HistoryEntry(t, at));
        }
    }

    private static final class HistoryEntry {
        final RewardType type;
        final Instant at;
        HistoryEntry(RewardType type, Instant at) {
            this.type = type; this.at = at;
        }
    }
}
