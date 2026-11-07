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
package com.neorf.anticheat.core.tick;

import com.neorf.anticheat.util.TimeUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerTickDataManager —— 玩家 tick 数据管理器
 * <p>
 * 为每个在线玩家维护检测所需的历史帧缓存：
 * <ul>
 *   <li>上一 tick 位置（用于速度/瞬移计算）；</li>
 *   <li>进入"可疑状态"的时间戳（用于飞行/穿墙计时）；</li>
 *   <li>最近 N 帧位置环形缓存（用于速度平滑、轨迹分析）；</li>
 *   <li>各检测模块的违规点数累计（统一登记，避免重复造表）。</li>
 * </ul>
 *
 * <p>仅在服务端主线程访问，使用 ConcurrentHashMap 兜底并发安全。
 * 玩家退出时通过 {@link #onQuit(UUID)} 清理，避免内存泄漏。
 */
public class PlayerTickDataManager {

    /** 位置历史帧数 */
    private static final int HISTORY_FRAMES = 40;

    /** 每位玩家的运行时数据 */
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    /** 玩家加入：初始化数据 */
    public void onJoin(ServerPlayer player) {
        dataMap.put(player.getUUID(), new PlayerData());
    }

    /** 玩家离开：清理数据 */
    public void onQuit(UUID uuid) {
        dataMap.remove(uuid);
    }

    /** 服务端 tick：更新所有在线玩家帧数据 */
    public void onServerTick(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData d = dataMap.computeIfAbsent(player.getUUID(), k -> new PlayerData());
            d.pushFrame(player.position(), TimeUtil.tickMs());
        }
    }

    /** 获取玩家运行时数据（不存在则惰性创建） */
    public PlayerData get(UUID uuid) {
        return dataMap.computeIfAbsent(uuid, k -> new PlayerData());
    }

    /**
     * 单玩家运行时数据。
     * 所有字段仅服务端主线程读写。
     */
    public static class PlayerData {
        /** 上一 tick 位置 */
        public Vec3 lastPos;
        /** 上一 tick 的 tick 时间戳(ms) */
        public long lastPosTickMs;
        /** 进入可疑空中状态的起始 tick 时间(ms)，-1 表示不在可疑状态 */
        public long suspiciousAirSince = -1;
        /** 碰撞箱与实心方块相交的起始时间(ms) */
        public long clipSince = -1;
        /** 最近位置历史帧（环形，旧在前） */
        private final Vec3[] history = new Vec3[HISTORY_FRAMES];
        private int historyIndex = 0;
        private int historyCount = 0;

        /** 违规点登记：checkName -> 累计点数 */
        private final Map<String, Integer> violations = new ConcurrentHashMap<>();

        /** 推入一帧位置 */
        public void pushFrame(Vec3 pos, long nowMs) {
            history[historyIndex] = pos;
            historyIndex = (historyIndex + 1) % HISTORY_FRAMES;
            if (historyCount < HISTORY_FRAMES) historyCount++;

            lastPos = pos;
            lastPosTickMs = nowMs;
        }

        /** 最近一帧位置（最新） */
        public Vec3 latestPos() {
            int idx = (historyIndex - 1 + HISTORY_FRAMES) % HISTORY_FRAMES;
            return history[idx];
        }

        /** 获取 N 帧前的位置（帧内位移用） */
        public Vec3 posNFramesAgo(int n) {
            if (n >= historyCount) return history[0];
            int idx = (historyIndex - 1 - n + HISTORY_FRAMES) % HISTORY_FRAMES;
            return history[idx];
        }

        /** 累计违规点，返回累计后的值 */
        public int addViolation(String checkName, int amount) {
            return violations.merge(checkName, amount, Integer::sum);
        }

        /** 重置指定检测的违规点（如管理员解封、TpsAdaptive 放宽后） */
        public void resetViolations(String checkName) {
            violations.remove(checkName);
        }

        /** 读取指定检测违规点 */
        public int getViolations(String checkName) {
            return violations.getOrDefault(checkName, 0);
        }
    }
}
