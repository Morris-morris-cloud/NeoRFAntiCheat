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
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraCheck —— 击杀光环 / 高频点击检测
 * <p>
 * 判定逻辑：
 * <ul>
 *   <li>遵从 26.1 攻击冷却（基于冷却换算单窗口允许次数）；</li>
 *   <li>统计攻击时间间隔与单位时间攻击次数；</li>
 *   <li>识别固定极小间隔的"机械连点"——真实人手点击存在自然抖动，
 *       机械连点间隔方差极小（几乎恒定）；</li>
 * </ul>
 * 在服务端处理真实攻击后计数，避免客户端事件导致服务端计数失效。
 */
public class KillAuraCheck extends Check {

     private final Map<UUID, AttackRateWindow> attackWindows = new ConcurrentHashMap<>();
     private final Map<UUID, Long> lastCpsViolationAt = new ConcurrentHashMap<>();

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
        int cps = attackWindows.computeIfAbsent(p.getUUID(), id -> new AttackRateWindow())
                .recordAndCount(now, cfg.getWindowMs());

        if (cps > cfg.getMaxCps()) {
            long lastFlag = lastCpsViolationAt.getOrDefault(p.getUUID(), Long.MIN_VALUE);
            if (lastFlag == Long.MIN_VALUE || now - lastFlag >= cfg.getWindowMs()) {
                lastCpsViolationAt.put(p.getUUID(), now);
                flagAtThreshold(p, 1, "服务端检测到 " + cps + " CPS，限制为 " + cfg.getMaxCps(),
                        cfg.getCpsViolationsToBan());
            }
        }
    }

    public void onQuit(UUID uuid) {
        attackWindows.remove(uuid);
        lastCpsViolationAt.remove(uuid);
    }
}
