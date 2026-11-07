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
 *   <li>超过阈值（如 1500/s）连续达到 sustainSeconds 秒 → 触发封禁；</li>
 *   <li>用于防压测、防卡服攻击。</li>
 * </ul>
 *
 * <p>每个入站 packet 由 Connection mixin 转发到 {@link #onPacket(ServerPlayer)}。
 */
public class PacketFloodCheck extends Check {

    /** 玩家 → 入站包滚动窗口 */
    private final Map<UUID, PacketFloodWindow> counters = new ConcurrentHashMap<>();

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

    /** 玩家离开清理 */
    public void onQuit(UUID uuid) {
        counters.remove(uuid);
    }

    /** 每收到一个入站包调用一次 */
    public void onPacket(ServerPlayer p) {
        if (!isEnabled()) return;
        if (!mod.getConfigManager().get().getGeneral().isEnabled()) return;
        AcConfig.Flood cfg = mod.getConfigManager().get().getPacket().getFlood();
        long now = System.nanoTime() / 1_000_000L;
        PacketFloodWindow.Observation observation = counters.computeIfAbsent(p.getUUID(),
                ignored -> new PacketFloodWindow())
            .record(now, cfg.getMaxPacketsPerSecond(), cfg.getSustainSeconds());
        if (observation.shouldFlag()) {
            p.level().getServer().execute(() ->
                flag(p, 1, "发包洪水 " + observation.packetCount() + " 包/秒，持续超限"));
        }
    }

}
