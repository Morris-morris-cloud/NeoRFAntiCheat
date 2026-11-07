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
package com.neorf.anticheat.check.packet;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.Check;
import com.neorf.anticheat.config.AcConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketFloodCheck —— 数据包洪水检测
 * <p>
 * 判定逻辑：单玩家单位时间发包计数。
 * <ul>
 *   <li>滑动窗口统计每个玩家每秒收到的入站包数量；</li>
 *   <li>超过阈值（如 1500/s）连续达到 sustainSeconds 秒 → 限流 + 标记；</li>
 *   <li>用于防压测、防卡服攻击。</li>
 * </ul>
 *
 * <p>接入方式：在玩家连接建立时，向 netty channel 管道加入一个入站计数器
 * （见 {@link #installChannelCounter}），每收到一个包调用 {@link #onPacket}。
 * 具体 netty 管道 API 随 26.1 内部结构可能变化，接入时按需校订。
 */
public class PacketFloodCheck extends Check {

    /** 玩家 → 滑动窗口计数器 */
    private final Map<UUID, SlidingWindow> counters = new ConcurrentHashMap<>();

    public PacketFloodCheck(NeoRFAntiCheat mod) {
        super(mod, "PacketFlood", "packet");
    }

    @Override
    public String id() { return "packetflood"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getPacket().getFlood().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        // 发包洪水属于攻击行为，连续超限即标记，阈值 1
        return 1;
    }

    /**
     * 由连接建立处调用，为玩家连接安装入站计数器。
     * 简化实现：这里仅登记计数器实例，真实 netty 挂钩见类注释。
     */
    public void onJoin(UUID uuid) {
        counters.put(uuid, new SlidingWindow(1000, 100));
    }

    /** 玩家离开清理 */
    public void onQuit(UUID uuid) {
        counters.remove(uuid);
    }

    /** 每收到一个入站包调用一次 */
    public void onPacket(ServerPlayer p) {
        if (!isEnabled()) return;
        AcConfig.Flood cfg = mod.getConfigManager().get().getPacket().getFlood();
        SlidingWindow w = counters.computeIfAbsent(p.getUUID(),
                k -> new SlidingWindow(1000, cfg.getSustainSeconds() * 1000));

        long now = System.currentTimeMillis();
        w.add(now);
        int count = w.count(now);

        if (count > cfg.getMaxPacketsPerSecond()) {
            // 连续超限才标记：窗口内持续处于高位
            if (w.isSustainedOver(now, cfg.getMaxPacketsPerSecond(), cfg.getSustainSeconds())) {
                flag(p, 1, "发包洪水 " + count + " 包/秒");
                w.reset(now);
            }
        }
    }

    /** 玩家离开时统一清理（由连接事件调用） */
    public void clearAll() {
        counters.clear();
    }

    /**
     * 简单滑动窗口计数器：维护最近 N 秒内的时间戳，统计频率。
     */
    private static class SlidingWindow {
        private final long windowMs;
        private final long[] buckets;
        private final long[] bucketTimes;
        private final int bucketCount;

        SlidingWindow(long windowMs, int historyMs) {
            this.windowMs = windowMs;
            // 用多个子窗口近似滑动（1 秒粒度）
            this.bucketCount = Math.max(1, (int) (historyMs / windowMs));
            this.buckets = new long[bucketCount];
            this.bucketTimes = new long[bucketCount];
        }

        synchronized void add(long now) {
            int idx = bucketIndex(now);
            if (bucketTimes[idx] != now / windowMs) {
                bucketTimes[idx] = now / windowMs;
                buckets[idx] = 0;
            }
            buckets[idx]++;
        }

        synchronized int count(long now) {
            long cur = now / windowMs;
            long total = 0;
            for (int i = 0; i < bucketCount; i++) {
                if (bucketTimes[i] == cur) total += buckets[i];
            }
            return (int) total;
        }

        /** 是否在过去 sustainSeconds 秒内持续超过阈值 */
        synchronized boolean isSustainedOver(long now, int maxPerSecond, int sustainSeconds) {
            long cur = now / windowMs;
            int overCount = 0;
            for (int i = 0; i < bucketCount; i++) {
                if (bucketTimes[i] == cur && buckets[i] > maxPerSecond) overCount++;
            }
            return overCount >= sustainSeconds;
        }

        synchronized void reset(long now) {
            for (int i = 0; i < bucketCount; i++) bucketTimes[i] = 0;
        }

        private int bucketIndex(long now) {
            return (int) (now / windowMs % bucketCount);
        }
    }
}
