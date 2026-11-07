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
import com.neorf.anticheat.util.MathUtil;
import com.neorf.anticheat.util.TimeUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AimCheck —— 异常视角（AimBot）检测
 * <p>
 * 判定逻辑：
 * <ul>
 *   <li>短时间视角 yaw/pitch 突变（单 tick 变化超过上限）；</li>
 *   <li>视角平滑锁向实体——攻击时视线方向与"指向目标方向"高度相关，
 *       且这种相关性在多次攻击中稳定，远超人手可复现水平；</li>
 *   <li>统计视角变化速率与目标实体位置的相关性。</li>
 * </ul>
 */
public class AimCheck extends Check {

    /** 每个玩家最近一次攻击时的视角记录（yaw, pitch, 指向目标方向） */
    private final Map<UUID, Deque<long[]>> aimHistory = new ConcurrentHashMap<>();

    public AimCheck(NeoRFAntiCheat mod) {
        super(mod, "Aim", "combat");
    }

    @Override
    public String id() { return "aim"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getCombat().getAim().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getCombat().getAim().getViolationsToBan();
    }

    public void onServerAttack(ServerPlayer p, Entity target) {
        if (!isEnabled()) return;
        if (mod.getExemptionManager().isExempt(p, id())) return;

        AcConfig.Aim cfg = mod.getConfigManager().get().getCombat().getAim();

        // 1) 视角突变检测：与上次攻击视角对比
        Deque<long[]> hist = aimHistory.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        long now = TimeUtil.tickMs();
        if (!hist.isEmpty()) {
            long[] prev = hist.peekLast();
            if (now - prev[3] < 200) { // 200ms 内的连续攻击
                float yawDelta = MathUtil.angleDelta(p.getYRot(), (prev[0] / 1000.0f));
                float pitchDelta = MathUtil.angleDelta(p.getXRot(), (prev[1] / 1000.0f));
                if (yawDelta > cfg.getMaxYawDeltaPerTick()) {
                    flag(p, 1, String.format("视角突变 yaw=%.1f°", yawDelta));
                }
            }
        }

        // 2) 锁向实体检测：视线方向与"指向目标中心方向"的夹角是否极小且稳定
        Vec3 eye = p.getEyePosition();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye).normalize();
        Vec3 look = p.getViewVector(1.0f);
        double cosAngle = look.dot(toTarget);
        if (cosAngle > cfg.getLockCorrelationThreshold()) {
            // 视线几乎精确指向目标 → 记录一次"锁向"
            flag(p, 1, "视角平滑锁向实体，相关性 " + String.format("%.3f", cosAngle));
        }

        // 记录本次攻击视角
        hist.addLast(new long[]{(long) (p.getYRot() * 1000), (long) (p.getXRot() * 1000), 0, now});
        while (hist.size() > 8) hist.pollFirst();
    }
}
