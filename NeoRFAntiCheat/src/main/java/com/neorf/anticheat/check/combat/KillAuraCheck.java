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
package com.neorf.anticheat.check.combat;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.Check;
import com.neorf.anticheat.config.AcConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraCheck —— 击杀光环 / 高频点击检测
 * <p>
 * 判定逻辑：
 * <ul>
 *   <li>统计服务端实际收到的攻击次数，不依赖客户端回调；</li>
 *   <li>滚动窗口持续超过 CPS 阈值时累计违规并触发封禁；</li>
 * </ul>
 * 在服务端处理真实攻击后计数，避免客户端事件导致服务端计数失效。
 */
public class KillAuraCheck extends Check {

    private final Map<UUID, AttackRateWindow> attackWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCpsSampleAt = new ConcurrentHashMap<>();

    public KillAuraCheck(NeoRFAntiCheat mod) {
        super(mod, "KillAura", "combat");
    }

    @Override
    public String id() { return "killaura"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getCombat().getKillaura().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getCombat().getKillaura().getCpsViolationsToBan();
    }

    /** Called from the server attack packet handler after thread handoff. */
    public void onServerAttack(ServerPlayer p) {
        if (!isEnabled()) return;
        if (!mod.getConfigManager().get().getGeneral().isEnabled()) return;
        if (mod.getExemptionManager().isExempt(p, id())) return;

        AcConfig.KillAura cfg = mod.getConfigManager().get().getCombat().getKillaura();
        long now = System.nanoTime() / 1_000_000L;
        UUID uuid = p.getUUID();
        long windowMs = cfg.getAverageWindowMs();
        int attackCount = attackWindows.computeIfAbsent(uuid, id -> new AttackRateWindow())
            .recordAndCount(now, windowMs);
        double averageCps = AttackRateWindow.averageCps(attackCount, windowMs);

        if (averageCps <= cfg.getMaxCps()) {
            lastCpsSampleAt.remove(uuid);
            clearViolations(p);
            return;
        }

        long lastSample = lastCpsSampleAt.getOrDefault(uuid, Long.MIN_VALUE);
        if (lastSample == Long.MIN_VALUE || now - lastSample >= windowMs) {
            lastCpsSampleAt.put(uuid, now);
            NeoRFAntiCheat.LOGGER.warn("CPS 超限: {}，2 秒平均 {:.1f} CPS（限制 {}）",
                    p.getGameProfile().name(), averageCps, cfg.getMaxCps());
            flagAtThreshold(p, 1, String.format("2 秒平均 %.1f CPS，限制为 %d CPS",
                averageCps, cfg.getMaxCps()), cfg.getCpsViolationsToBan());
        }
    }

    public void onQuit(UUID uuid) {
        attackWindows.remove(uuid);
        lastCpsSampleAt.remove(uuid);
    }
}
