/*
 * Copyright (C) 2026 NeoRF Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neorf.anticheat.punishment;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.config.ConfigManager;
import com.neorf.anticheat.core.snapshot.SnapshotBuilder;
import com.neorf.anticheat.storage.StorageProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BanManager —— 封禁、计数、处罚逻辑核心
 * <p>
 * 以 <b>UUID 为主键</b>维护每位玩家的 {@link PlayerBanState}：
 * <ul>
 *   <li>{@code validBanCount}：有效封禁次数（只用于阶梯判定）；</li>
 *   <li>{@code records}：该玩家的全部封禁记录（历史）。</li>
 * </ul>
 *
 * <p>处罚分支（自动封禁触发时）：
 * <pre>
 * validBanCount == 0 → 15 天限时封禁，validBanCount += 1
 * validBanCount >= 1 → 永久封禁
 * </pre>
 *
 * <p>管理员指令：
 * <ul>
 *   <li>unban [falsepositive]：标记 FALSE_POSITIVE（不改计数）或按配置计数；</li>
 *   <li>clearcount：手动清零 validBanCount（兜底运维）。</li>
 * </ul>
 */
public class BanManager {

    private final StorageProvider storage;
    private final ConfigManager configManager;

    /** UUID → 玩家封禁状态（内存缓存，启动时从存储加载，变更时写回） */
    private final Map<UUID, PlayerBanState> states = new ConcurrentHashMap<>();
    /** Normalized player name -> active name ban. */
    private final Map<String, NameBanRecord> nameBans = new ConcurrentHashMap<>();

    public BanManager(StorageProvider storage, ConfigManager configManager) {
        this.storage = storage;
        this.configManager = configManager;
    }

    /** 服务器启动：加载持久化数据 */
    public void onServerStart(MinecraftServer server) {
        states.clear();
        nameBans.clear();
        Map<UUID, PlayerBanState> loaded = storage.loadAllStates();
        if (loaded != null) {
            states.putAll(loaded);
        }
        Map<String, NameBanRecord> loadedNameBans = storage.loadNameBans();
        if (loadedNameBans != null) {
            nameBans.putAll(loadedNameBans);
        }
    }

    /** 服务器关闭：持久化 */
    public void onServerStop() {
        storage.saveAllStates(states);
        storage.saveNameBans(nameBans);
    }

    /**
     * 触发自动封禁（检测/查端命中时调用）。
     *
     * @param player   违规玩家
     * @param checkName 触发检测
     * @param evidence  证据快照
     * @return true 表示成功封禁
     */
    public boolean applyAutomaticBan(ServerPlayer player, String checkName,
                                     SnapshotBuilder.Snapshot evidence) {
        UUID uuid = player.getUUID();
        PlayerBanState state = states.computeIfAbsent(uuid, k -> new PlayerBanState());

        // 读取有效封禁次数，决定阶梯
        long now = System.currentTimeMillis();
        int count = state.validBanCount;

        BanRecord record = new BanRecord();
        record.setUuid(uuid);
        record.setName(player.getGameProfile().name());
        record.setCheckName(checkName);
        record.setAutomatic(true);
        record.setCreatedAtMs(now);
        record.setBanStartMs(now);
        record.setEvidence(evidence);

        if (count == 0) {
            // 首次：15 天限时封禁
            long firstMs = configManager.get().getPunishment().getFirstBanMs();
            record.setDurationMs(firstMs);
            record.setBanEndMs(now + firstMs);
        } else {
            // 二次及以后：永久封禁
            record.setDurationMs(-1);
            record.setBanEndMs(-1);
        }

        record.setStatus(BanStatus.ACTIVE);
        state.records.add(record);
        // 只有"有效封禁"计入阶梯次数（EXPIRED 计入，FALSE_POSITIVE 不计入）
        state.validBanCount += 1;

        // 持久化
        storage.saveAllStates(states);

        // 踢出服务器
        player.connection.disconnect(banMessage(record));

        NeoRFAntiCheat.LOGGER.info("自动封禁 {} ({}) 检测={} 阶梯={} 时长={}",
                player.getGameProfile().name(), uuid, checkName,
                record.isPermanent() ? "永久" : record.getDurationMs() + "ms",
                evidence != null ? evidence.detail : "无证据");

        return true;
    }

    /** 玩家是否处于生效封禁 */
    public boolean isActiveBanned(UUID uuid) {
        PlayerBanState state = states.get(uuid);
        if (state == null) return false;
        long now = System.currentTimeMillis();
        // 顺带处理到期自动解除（EXPIRED）
        boolean hasActive = false;
        List<BanRecord> expired = null;
        for (BanRecord r : state.records) {
            if (r.getStatus() == BanStatus.ACTIVE) {
                if (r.getBanEndMs() >= 0 && now >= r.getBanEndMs()) {
                    // 到期自动解除，计入 validBanCount（EXPIRED 状态已满足）
                    r.setStatus(BanStatus.EXPIRED);
                    if (expired == null) expired = new ArrayList<>();
                    expired.add(r);
                } else {
                    hasActive = true;
                }
            }
        }
        if (expired != null && !expired.isEmpty()) {
            storage.saveAllStates(states);
        }
        return hasActive;
    }

    /** Add a manual ban by player name, independent of the player's UUID. */
    public String banName(String name, String reason, long durationMs, boolean permanent) {
        if (!isValidPlayerName(name)) {
            return "玩家名无效；请提供 3-16 位字母、数字或下划线组成的名字，或 UUID";
        }

        long now = System.currentTimeMillis();
        long end = permanent ? -1 : now + Math.max(1L, durationMs);
        String safeReason = reason == null || reason.isBlank() ? "manual-ban" : reason.trim();
        String key = normalizeName(name);
        nameBans.put(key, new NameBanRecord(name, safeReason, now, end));
        storage.saveNameBans(nameBans);

        MinecraftServer server = NeoRFAntiCheat.getInstance().getServer();
        if (server != null) {
            Component message = nameBanMessage(name);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                    player.connection.disconnect(message);
                }
            }
        }

        return "已按玩家名封禁 " + name + "，原因=" + safeReason + "，"
                + (permanent ? "永久" : "时长=" + Math.max(1L, durationMs) + "ms");
    }

    /** True when a non-expired name ban matches the player's current name. */
    public boolean isNameBanned(String name) {
        NameBanRecord record = nameBans.get(normalizeName(name));
        if (record == null) return false;
        if (record.isActiveNow(System.currentTimeMillis())) return true;
        nameBans.remove(normalizeName(name), record);
        storage.saveNameBans(nameBans);
        return false;
    }

    /** Build the disconnect message for a name-based ban. */
    public Component nameBanMessage(String name) {
        NameBanRecord record = nameBans.get(normalizeName(name));
        if (record == null) return Component.literal("你已被封禁");
        String end = record.getBanEndMs() < 0
                ? "永久封禁"
                : "封禁至 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                        .format(new java.util.Date(record.getBanEndMs()));
        return Component.literal("你已因玩家名 " + record.getName() + " 被 NeoRFAntiCheat 封禁。\n原因: "
                + record.getReason() + "\n" + end + "\n如认为误封，请联系管理员。");
    }

    /** Ban by UUID when the target parses as one; otherwise ban the player name. */
    public String ban(String target, String reason, long durationMs, boolean permanent) {
        try {
            return ban(UUID.fromString(target), reason, durationMs, permanent);
        } catch (IllegalArgumentException e) {
            return banName(target, reason, durationMs, permanent);
        }
    }

    /** 生效封禁的提示消息 */
    public Component activeBanMessage(UUID uuid) {
        PlayerBanState state = states.get(uuid);
        if (state == null) return Component.literal("你已被封禁");
        for (BanRecord r : state.records) {
            if (r.getStatus() == BanStatus.ACTIVE) {
                return banMessage(r);
            }
        }
        return Component.literal("你已被封禁");
    }

    /** 构造封禁踢出消息 */
    private Component banMessage(BanRecord r) {
        String reason = r.isPermanent()
                ? "永久封禁"
                : "封禁至 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(r.getBanEndMs()));
        return Component.literal("你已被 NeoRFAntiCheat 封禁。\n原因: " + r.getCheckName() + "\n" + reason
                + "\n如认为误封，请联系管理员。");
    }

    /* ---------------- 管理员操作 ---------------- */

    /**
     * 解除封禁。
     *
     * @param uuid         玩家
     * @param falsePositive 是否标记为误封（falsepositive）
     * @return 成功信息
     */
    public String unban(UUID uuid, boolean falsePositive) {
        PlayerBanState state = states.get(uuid);
        if (state == null) {
            return "未找到该玩家的封禁记录";
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (BanRecord r : state.records) {
            if (r.getStatus() == BanStatus.ACTIVE) {
                if (falsePositive) {
                    // 误封：标记 FALSE_POSITIVE，不计入 validBanCount，并回退计数
                    r.setStatus(BanStatus.FALSE_POSITIVE);
                    state.validBanCount = Math.max(0, state.validBanCount - 1);
                } else {
                    // 普通解除：是否计入计数由配置决定
                    r.setStatus(BanStatus.MANUAL_UNBAN);
                    if (!configManager.get().getPunishment().isManualUnbanCounts()) {
                        state.validBanCount = Math.max(0, state.validBanCount - 1);
                    }
                }
                changed = true;
            }
        }
        if (!changed) {
            return "该玩家没有生效中的封禁";
        }
        storage.saveAllStates(states);
        return falsePositive
                ? "已标记为误封解除，有效计数未叠加"
                : "已解除封禁" + (configManager.get().getPunishment().isManualUnbanCounts()
                    ? "（普通解除计入计数）" : "（普通解除不计入计数）");
    }

    /** Unban a UUID or a name; name bans do not contribute to UUID ban counts. */
    public String unban(String target, boolean falsePositive) {
        try {
            return unban(UUID.fromString(target), falsePositive);
        } catch (IllegalArgumentException e) {
            if (!isValidPlayerName(target)) {
                return "目标无效；请提供玩家名或 UUID";
            }
            NameBanRecord removed = nameBans.remove(normalizeName(target));
            if (removed != null) {
                storage.saveNameBans(nameBans);
            }

            UUID uuid = findBannedUuidByName(target);
            String uuidResult = uuid == null ? null : unban(uuid, falsePositive);
            if (removed != null && uuidResult != null) {
                return uuidResult + "；同时解除玩家名封禁: " + removed.getName();
            }
            if (uuidResult != null) return uuidResult;
            if (removed != null) {
                return falsePositive ? "已按玩家名标记误封并解除封禁: " + removed.getName()
                        : "已解除玩家名封禁: " + removed.getName();
            }
            return "未找到该玩家名的生效封禁";
        }
    }

    /**
     * 手工封禁：适合作为 /NeoRF ban 入口，代替 ipban 的更灵活调用。
     * 支持按原因与时长进行手工封禁，并在在线时立即踢出玩家。
     */
    public String ban(UUID uuid, String reason, long durationMs, boolean permanent) {
        PlayerBanState state = states.computeIfAbsent(uuid, k -> new PlayerBanState());
        long now = System.currentTimeMillis();
        String safeReason = reason == null || reason.isBlank() ? "manual-ban" : reason.trim();
        MinecraftServer server = NeoRFAntiCheat.getInstance().getServer();
        ServerPlayer onlinePlayer = server == null ? null : server.getPlayerList().getPlayer(uuid);

        BanRecord record = new BanRecord();
        record.setUuid(uuid);
        record.setName(onlinePlayer == null ? "manual:" + uuid : onlinePlayer.getGameProfile().name());
        record.setCheckName(safeReason);
        record.setAutomatic(false);
        record.setCreatedAtMs(now);
        record.setBanStartMs(now);
        record.setStatus(BanStatus.ACTIVE);

        if (permanent) {
            record.setDurationMs(-1);
            record.setBanEndMs(-1);
        } else {
            long effective = Math.max(1L, durationMs);
            record.setDurationMs(effective);
            record.setBanEndMs(now + effective);
        }

        state.records.add(record);
        state.validBanCount += 1;
        storage.saveAllStates(states);

        // 在线玩家直接踢出并展示更明确的手工封禁原因
        if (onlinePlayer != null) {
            onlinePlayer.connection.disconnect(Component.literal(
                        "你已被管理员手工封禁。\n原因: " + safeReason + "\n" +
                                (permanent ? "永久封禁" : "封禁时长: " + durationMs + "ms")));
        }

        return "已手工封禁 " + uuid + "，原因=" + safeReason + "，" + (permanent ? "永久" : "时长=" + durationMs + "ms");
    }

    /** 清零有效封禁计数（兜底运维） */
    public String clearCount(UUID uuid) {
        PlayerBanState state = states.get(uuid);
        if (state == null) {
            return "未找到该玩家记录";
        }
        state.validBanCount = 0;
        storage.saveAllStates(states);
        return "已清零 " + uuid + " 的有效封禁计数";
    }

    /** 查询玩家封禁状态（baninfo） */
    public PlayerBanState getState(UUID uuid) {
        return states.get(uuid);
    }

    /** 所有玩家状态（reportlist/全局） */
    public Map<UUID, PlayerBanState> allStates() {
        return states;
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private UUID findBannedUuidByName(String name) {
        MinecraftServer server = NeoRFAntiCheat.getInstance().getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                    return player.getUUID();
                }
            }
        }

        for (Map.Entry<UUID, PlayerBanState> entry : states.entrySet()) {
            for (BanRecord record : entry.getValue().records) {
                if (record.getStatus() == BanStatus.ACTIVE
                        && record.name() != null && record.name().equalsIgnoreCase(name)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static boolean isValidPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    /** 玩家封禁状态（含有效计数与记录列表） */
    public static class PlayerBanState {
        /** 有效封禁次数（仅用于阶梯判定） */
        public int validBanCount = 0;
        /** 全部封禁记录 */
        public List<BanRecord> records = new ArrayList<>();
    }
}
