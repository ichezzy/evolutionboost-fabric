package com.ichezzy.evolutionboost.reward;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.item.ModItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewards mit serverseitiger Realzeit (CET/CEST):
 * DAILY:   Reset täglich 00:00 CET
 * WEEKLY:  Reset montags 00:00 CET
 * MONTHLY: Reset am 1. 00:00 CET
 * Monthly-Eligibility über Namenslisten (case-insensitive).
 */
public final class RewardManager {
    private RewardManager() {}

    /* ================== Storage ================== */

    private static final ZoneId ZONE_CET = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZONE_CET);

    private static final Map<UUID, PlayerRewardState> STATE = new ConcurrentHashMap<>();
    private static final Set<String> ALLOWED_DONATOR = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> ALLOWED_GYM     = Collections.synchronizedSet(new HashSet<>());

    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(EvolutionBoost.MOD_ID);
    }
    private static Path stateFile()       { return configDir().resolve("rewards_state.json"); }
    private static Path eligibilityFile() { return configDir().resolve("rewards_eligibility.json"); }

    public static void init(net.minecraft.server.MinecraftServer server) {
        try { Files.createDirectories(configDir()); } catch (IOException ignored) {}
        loadEligibility();
        loadState();
    }

    public static void saveAll() {
        saveState();
        saveEligibility();
    }

    /* ================== Public API ================== */

    /** Login-Hinweise. */
    public static void onPlayerJoin(ServerPlayer p) {
        STATE.computeIfAbsent(p.getUUID(), id -> new PlayerRewardState());

        boolean readyDaily   = isReady(p, RewardType.DAILY);
        boolean readyWeekly  = isReady(p, RewardType.WEEKLY);
        boolean readyDonator = isEligibleMonthly(p, RewardType.MONTHLY_DONATOR) && isReady(p, RewardType.MONTHLY_DONATOR);
        boolean readyGym     = isEligibleMonthly(p, RewardType.MONTHLY_GYM)     && isReady(p, RewardType.MONTHLY_GYM);

        if (readyDaily || readyWeekly || readyDonator || readyGym) {
            p.sendSystemMessage(
                    Component.literal("[REWARDS] You have rewards to claim!")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
            );
            p.sendSystemMessage(
                    Component.literal("Tip: /rewards info  or  /rewards claim <type>")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
            );
        } else {
            p.sendSystemMessage(
                    Component.literal("[REWARDS] You currently have no rewards to claim.")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
    }

    /** Schöne Info-Ausgabe je Zeile und Farbe. */
    public static void sendInfo(CommandSourceStack src, ServerPlayer p) {
        sendOneInfo(src, p, RewardType.DAILY,  ChatFormatting.YELLOW,      "Daily");
        sendOneInfo(src, p, RewardType.WEEKLY, ChatFormatting.AQUA,        "Weekly");

        if (isEligibleMonthly(p, RewardType.MONTHLY_DONATOR)) {
            sendOneInfo(src, p, RewardType.MONTHLY_DONATOR, ChatFormatting.LIGHT_PURPLE, "Monthly (Donator)");
        } else {
            src.sendSuccess(() -> Component.literal("Monthly (Donator): not eligible").withStyle(ChatFormatting.RED), false);
        }

        if (isEligibleMonthly(p, RewardType.MONTHLY_GYM)) {
            sendOneInfo(src, p, RewardType.MONTHLY_GYM, ChatFormatting.LIGHT_PURPLE, "Monthly (Gym)");
        } else {
            src.sendSuccess(() -> Component.literal("Monthly (Gym): not eligible").withStyle(ChatFormatting.RED), false);
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
        if ((type == RewardType.MONTHLY_DONATOR || type == RewardType.MONTHLY_GYM) && !isEligibleMonthly(p, type)) {
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

        // === Grant items ===
        switch (type) {
            case DAILY:
                giveOrDrop(p, new ItemStack(ModItems.EVOLUTION_COIN_BRONZE));
                break;
            case WEEKLY:
                giveOrDrop(p, new ItemStack(ModItems.EVOLUTION_COIN_SILVER));
                break;
            case MONTHLY_DONATOR:
                giveOrDrop(p, new ItemStack(ModItems.EVOLUTION_COIN_GOLD));
                giveOrDrop(p, new ItemStack(ModItems.EVENT_VOUCHER_BLANK));
                // externe Items
                giveCrdKeysRaidPass(p, 10); // crdkeys:raid_pass_basic mit uses=10
                giveExternal(p, "wanteditems:cobblemon_lucky_box", 5);
                giveExternal(p, "wanteditems:gold_candy_lucky_box", 5);
                giveExternal(p, "wanteditems:ancient_poke_ball_lucky_box", 5);
                break;
            case MONTHLY_GYM:
                giveOrDrop(p, new ItemStack(ModItems.EVOLUTION_COIN_GOLD));
                giveCrdKeysRaidPass(p, 10);
                break;
        }

        stampClaim(p.getUUID(), type);
        p.sendSystemMessage(Component.literal("[Rewards] Claimed ")
                .append(Component.literal(typeReadable(type)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(TS_FMT.format(Instant.now())).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /* ================== Cooldowns & Eligibility ================== */

    private static boolean isReady(ServerPlayer p, RewardType t) {
        return secondsUntilNext(p, t) == 0;
    }

    public static long secondsUntilNext(ServerPlayer p, RewardType t) {
        return secondsUntilNext(p.getUUID(), t);
    }

    /**
     * NEU: Ready, wenn lastClaim < lastResetBoundary; sonst Cooldown bis nextResetBoundary.
     */
    public static long secondsUntilNext(UUID uuid, RewardType t) {
        PlayerRewardState st = STATE.computeIfAbsent(uuid, id -> new PlayerRewardState());
        Instant now = Instant.now();
        Instant last = st.getLast(t);

        Instant lastReset = lastResetTime(t, now);
        if (last == null || last.isBefore(lastReset)) {
            return 0L; // noch nicht für dieses Fenster geclaimed
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

    public static void setMonthlyEligibility(String playerName, boolean donator, boolean gym) {
        String key = normalizeName(playerName);
        synchronized (ALLOWED_DONATOR) { if (donator) ALLOWED_DONATOR.add(key); else ALLOWED_DONATOR.remove(key); }
        synchronized (ALLOWED_GYM)     { if (gym)     ALLOWED_GYM.add(key);     else ALLOWED_GYM.remove(key); }
        saveEligibility();
    }

    private static boolean isEligibleMonthly(ServerPlayer p, RewardType t) {
        String key = normalizeName(p.getGameProfile().getName());
        if (t == RewardType.MONTHLY_DONATOR) return ALLOWED_DONATOR.contains(key);
        if (t == RewardType.MONTHLY_GYM)     return ALLOWED_GYM.contains(key);
        return true;
    }

    /* ================== Reset-Berechnungen ================== */

    private static Instant lastResetTime(RewardType t, Instant ref) {
        ZonedDateTime zdt = ref.atZone(ZONE_CET);
        switch (t) {
            case DAILY: {
                ZonedDateTime todayMidnight = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (zdt.isBefore(todayMidnight)) {
                    // vor Mitternacht -> gestern 00:00
                    return todayMidnight.minusDays(1).toInstant();
                }
                return todayMidnight.toInstant();
            }
            case WEEKLY: {
                ZonedDateTime weekMidnight = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .with(java.time.DayOfWeek.MONDAY);
                // falls heute vor dem heutigen Montag-00:00 liegt -> eine Woche zurück
                while (zdt.isBefore(weekMidnight)) weekMidnight = weekMidnight.minusWeeks(1);
                return weekMidnight.toInstant();
            }
            case MONTHLY_DONATOR:
            case MONTHLY_GYM: {
                ZonedDateTime firstOfMonth = zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (zdt.isBefore(firstOfMonth)) firstOfMonth = firstOfMonth.minusMonths(1);
                return firstOfMonth.toInstant();
            }
            default:
                return ref;
        }
    }

    private static Instant nextResetTime(RewardType t, Instant ref) {
        ZonedDateTime zdt = ref.atZone(ZONE_CET);
        switch (t) {
            case DAILY: {
                ZonedDateTime next = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (!next.isAfter(zdt)) next = next.plusDays(1);
                return next.toInstant();
            }
            case WEEKLY: {
                ZonedDateTime next = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .with(java.time.DayOfWeek.MONDAY);
                while (!next.isAfter(zdt)) next = next.plusWeeks(1);
                return next.toInstant();
            }
            case MONTHLY_DONATOR:
            case MONTHLY_GYM: {
                ZonedDateTime next = zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                if (!next.isAfter(zdt)) next = next.plusMonths(1);
                return next.toInstant();
            }
            default:
                return ref;
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
        if (t == RewardType.DAILY) return "Daily";
        if (t == RewardType.WEEKLY) return "Weekly";
        if (t == RewardType.MONTHLY_DONATOR) return "Monthly (Donator)";
        return "Monthly (Gym)";
    }

    private static void stampClaim(UUID uuid, RewardType t) {
        PlayerRewardState st = STATE.computeIfAbsent(uuid, id -> new PlayerRewardState());
        st.setLast(t, Instant.now());
        saveState();
    }

    /* ================== Item Helpers (extern & robust) ================== */

    private static void giveOrDrop(ServerPlayer p, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        p.getInventory().placeItemBackInInventory(stack); // legt ins Inventar oder droppt davor, wenn voll
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
        // 1.21.x: CustomData-Komponente statt getOrCreateTag
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

    /* ================== Persistenz (simpel) ================== */

    private static void loadEligibility() {
        ALLOWED_DONATOR.clear();
        ALLOWED_GYM.clear();
        Path p = eligibilityFile();
        if (!Files.exists(p)) { saveEligibility(); return; }
        try {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(p));
            for (String n : props.getProperty("donator", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_DONATOR.add(k);
            }
            for (String n : props.getProperty("gym", "").split(",")) {
                String k = normalizeName(n);
                if (!k.isEmpty()) ALLOWED_GYM.add(k);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to load eligibility: {}", e.getMessage());
        }
    }

    private static void saveEligibility() {
        try {
            Files.createDirectories(configDir());
            Properties props = new Properties();
            props.setProperty("donator", String.join(",", ALLOWED_DONATOR));
            props.setProperty("gym", String.join(",", ALLOWED_GYM));
            props.store(Files.newBufferedWriter(eligibilityFile()),
                    "Rewards eligibility (names, case-insensitive)");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to save eligibility: {}", e.getMessage());
        }
    }

    private static void loadState() {
        STATE.clear();
        Path p = stateFile();
        if (!Files.exists(p)) return;
        try {
            List<String> lines = Files.readAllLines(p);
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
                STATE.put(id, st);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to load state: {}", e.getMessage());
        }
    }

    private static void saveState() {
        try {
            Files.createDirectories(configDir());
            List<String> out = new ArrayList<>();
            for (Map.Entry<UUID, PlayerRewardState> e : STATE.entrySet()) {
                PlayerRewardState st = e.getValue();
                out.add(String.join(";",
                        e.getKey().toString(),
                        st.daily == null ? "" : String.valueOf(st.daily.getEpochSecond()),
                        st.weekly == null ? "" : String.valueOf(st.weekly.getEpochSecond()),
                        st.monthlyDonator == null ? "" : String.valueOf(st.monthlyDonator.getEpochSecond()),
                        st.monthlyGym == null ? "" : String.valueOf(st.monthlyGym.getEpochSecond())
                ));
            }
            Files.write(stateFile(), out);
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[rewards] failed to save state: {}", e.getMessage());
        }
    }

    // Liste der Rollen im Chat ausgeben: /rewards list <donator|gym>
    public static void sendRoleList(net.minecraft.commands.CommandSourceStack src, String roleKey) {
        final java.util.Set<String> names;
        if ("donator".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_DONATOR;
        } else if ("gym".equalsIgnoreCase(roleKey)) {
            names = ALLOWED_GYM;
        } else {
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Rewards] Unknown role: " + roleKey)
                    .withStyle(net.minecraft.ChatFormatting.RED), false);
            return;
        }

        if (names.isEmpty()) {
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Rewards] No entries for '" + roleKey + "'.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), false);
            return;
        }

        String joined = String.join(", ", names);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Rewards] " + roleKey + ": " + joined)
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), false);
    }


    /* ================== State ================== */

    private static final class PlayerRewardState {
        Instant daily;
        Instant weekly;
        Instant monthlyDonator;
        Instant monthlyGym;

        Instant getLast(RewardType t) {
            switch (t) {
                case DAILY: return daily;
                case WEEKLY: return weekly;
                case MONTHLY_DONATOR: return monthlyDonator;
                case MONTHLY_GYM: return monthlyGym;
                default: return null;
            }
        }
        void setLast(RewardType t, Instant when) {
            switch (t) {
                case DAILY: daily = when; break;
                case WEEKLY: weekly = when; break;
                case MONTHLY_DONATOR: monthlyDonator = when; break;
                case MONTHLY_GYM: monthlyGym = when; break;
            }
        }
        void clear(RewardType t) {
            switch (t) {
                case DAILY: daily = null; break;
                case WEEKLY: weekly = null; break;
                case MONTHLY_DONATOR: monthlyDonator = null; break;
                case MONTHLY_GYM: monthlyGym = null; break;
            }
        }
    }
}
