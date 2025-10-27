package com.ichezzy.evolutionboost.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.component.DataComponents;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reward-Manager mit Perioden-IDs (UTC):
 * - DAILY:   Periode = UTC-Tag (epochDay)
 * - WEEKLY:  Periode = WEEK_BASED_YEAR * 100 + WEEK_OF_WEEK_BASED_YEAR (ISO)
 * - MONTHLY_*: Periode = year * 100 + month  (pro Kategorie separat)
 *
 * Verpasst = verfällt (kein Stacking). Genau 1 Claim pro aktueller Periode.
 */
public final class RewardManager {
    private RewardManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class PlayerRewardData {
        public Map<RewardType, Long> lastClaimed = new EnumMap<>(RewardType.class);
    }

    /* ===================== Public API ===================== */

    public static boolean hasAnyReady(ServerPlayer player) {
        Instant now = Instant.now();
        PlayerRewardData data = load(player);
        for (RewardType t : RewardType.values()) {
            long cur = currentPeriodId(t, now);
            Long last = data.lastClaimed.get(t);
            if (last == null || !last.equals(cur)) return true;
        }
        return false;
    }

    public static long secondsUntilNext(ServerPlayer player, RewardType type) {
        Instant now = Instant.now();
        PlayerRewardData data = load(player);
        long cur = currentPeriodId(type, now);
        Long last = data.lastClaimed.get(type);
        if (last == null || !last.equals(cur)) return 0L;
        Instant end = periodEndExclusive(type, now);
        long secs = end.getEpochSecond() - now.getEpochSecond();
        return Math.max(0L, secs);
    }

    public static boolean claim(ServerPlayer player, RewardType type) {
        Instant now = Instant.now();
        PlayerRewardData data = load(player);
        long cur = currentPeriodId(type, now);
        Long last = data.lastClaimed.get(type);

        if (last != null && last.equals(cur)) {
            long secs = secondsUntilNext(player, type);
            player.sendSystemMessage(Component.literal("§cAlready claimed for this " + type.name().toLowerCase() + " period. Try again in " + secs + "s."));
            return false;
        }

        // Eligibility für Monthly-Kategorien
        String name = player.getGameProfile().getName();
        switch (type) {
            case MONTHLY_DONATOR -> {
                if (!RewardRoles.isDonator(name)) {
                    player.sendSystemMessage(Component.literal("§cYou are not in the Donator list."));
                    return false;
                }
            }
            case MONTHLY_GYM -> {
                if (!RewardRoles.isGymLeader(name)) {
                    player.sendSystemMessage(Component.literal("§cYou are not in the Gym Leader list."));
                    return false;
                }
            }
            default -> {}
        }

        boolean ok = grantReward(player, type);
        if (!ok) {
            player.sendSystemMessage(Component.literal("§cNo reward configured for " + type.name().toLowerCase() + "."));
            return false;
        }

        data.lastClaimed.put(type, cur);
        save(player, data);

        player.sendSystemMessage(Component.literal("§aClaimed " + type.name().toLowerCase() + " reward!"));
        return true;
    }

    /* ===================== Rewards konfigurieren ===================== */

    private static boolean grantReward(ServerPlayer player, RewardType type) {
        try {
            switch (type) {
                case DAILY -> {
                    // 1× Bronze
                    give(player, stack("evolutionboost:evolution_coin_bronze", 1));
                }
                case WEEKLY -> {
                    // 1× Silver
                    give(player, stack("evolutionboost:evolution_coin_silver", 1));
                }
                case MONTHLY_DONATOR -> {
                    // 1× Gold Coin
                    give(player, stack("evolutionboost:evolution_coin_gold", 1));
                    // 1× Blank Voucher
                    give(player, stack("evolutionboost:event_voucher_blank", 1));
                    // 1× Raid Pass mit Custom-Data {crdkeys_uses: 10}
                    give(player, stackWithCustomData("crdkeys:raid_pass_basic", 1, tag -> tag.putInt("crdkeys_uses", 10)));
                    // WantedItems Lucky Boxes
                    give(player, stack("wanteditems:cobblemon_lucky_box", 5));
                    give(player, stack("wanteditems:gold_candy_lucky_box", 5));
                    give(player, stack("wanteditems:ancient_poke_ball_lucky_box", 5));
                }
                case MONTHLY_GYM -> {
                    // 1× Gold Coin
                    give(player, stack("evolutionboost:evolution_coin_gold", 1));
                    // 1× Raid Pass mit Custom-Data {crdkeys_uses: 10}
                    give(player, stackWithCustomData("crdkeys:raid_pass_basic", 1, tag -> tag.putInt("crdkeys_uses", 10)));
                }
                default -> { return false; }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* ===================== Helpers ===================== */

    private static ItemStack stack(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }

    @FunctionalInterface
    private interface NbtEditor { void edit(CompoundTag tag); }

    /** Erzeugt einen Stack und setzt minecraft:custom_data via DataComponents.CUSTOM_DATA. */
    private static ItemStack stackWithCustomData(String id, int count, NbtEditor editor) {
        ItemStack s = stack(id, count);
        if (s.isEmpty()) return s;
        CompoundTag tag = new CompoundTag();
        if (editor != null) editor.edit(tag);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    /** Inventar → wenn kein Platz, vor dem Spieler droppen. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        boolean ok = player.getInventory().add(stack);
        if (!ok || !stack.isEmpty()) {
            Level level = player.level();
            ItemEntity ent = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack.copy());
            ent.setNoPickUpDelay();
            level.addFreshEntity(ent);
        }
    }

    /* ===================== Perioden-Logik ===================== */

    private static long currentPeriodId(RewardType type, Instant now) {
        LocalDate d = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return switch (type) {
            case DAILY -> d.toEpochDay();
            case WEEKLY -> (long) d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHLY_DONATOR, MONTHLY_GYM -> (long) d.getYear() * 100L + d.getMonthValue();
        };
    }

    private static Instant periodEndExclusive(RewardType type, Instant now) {
        LocalDate d = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return switch (type) {
            case DAILY -> d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case WEEKLY -> {
                LocalDate nextMonday = d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                yield nextMonday.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            case MONTHLY_DONATOR, MONTHLY_GYM -> d.withDayOfMonth(1).plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }

    /* ===================== Persistenz ===================== */

    private static PlayerRewardData load(ServerPlayer player) {
        try {
            Path file = playerFile(player);
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    PlayerRewardData data = GSON.fromJson(r, PlayerRewardData.class);
                    if (data != null && data.lastClaimed != null) return data;
                }
            }
        } catch (IOException ignored) {}
        return new PlayerRewardData();
    }

    private static void save(ServerPlayer player, PlayerRewardData data) {
        try {
            Path file = playerFile(player);
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {}
    }

    private static Path baseDir(net.minecraft.server.MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("evolutionboost").resolve("rewards").resolve("players");
    }

    private static Path playerFile(ServerPlayer player) {
        UUID id = player.getUUID();
        return baseDir(player.server).resolve(id.toString() + ".json");
    }
}
