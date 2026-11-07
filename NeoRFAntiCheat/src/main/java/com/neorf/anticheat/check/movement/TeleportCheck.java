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
package com.neorf.anticheat.check.movement;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.Check;
import com.neorf.anticheat.config.AcConfig;
import com.neorf.anticheat.util.MathUtil;
import com.neorf.anticheat.util.TimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * TeleportCheck —— 瞬移检测
 * <p>
 * 判定逻辑：两点欧氏距离 ÷ 时间间隔 = 移动速度，超过原版极限速度的倍数判为瞬移。
 * <ul>
 *   <li>过滤 TPS 暴跌、服务端 tick 跳变带来的坐标跳变
 *       （TPS 过低时整体跳过）；</li>
 *   <li>用多帧间隔平滑，避免单帧抖动误判。</li>
 * </ul>
 */
public class TeleportCheck extends Check {

    /** 原版极限移动速度（b/s）近似，含疾跑+跳跃下坡；按版本校订 */
    private static final double VANILLA_LIMIT_SPEED = 11.0;

    public TeleportCheck(NeoRFAntiCheat mod) {
        super(mod, "Teleport", "movement");
    }

    @Override
    public String id() { return "teleport"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getMovement().getTeleport().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getMovement().getTeleport().getViolationsToBan();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        // TPS 过低 / 服务器过卡时跳过，避免 tick 跳变误判为瞬移
        if (mod.getTpsAdaptiveManager().shouldSkipTimingChecks()) return;

        AcConfig.Teleport cfg = mod.getConfigManager().get().getMovement().getTeleport();
        final int FRAMES = 3;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mod.getExemptionManager().isExempt(p, id())) continue;

            var data = mod.getTickDataManager().get(p.getUUID());
            Vec3 now = p.position();
            Vec3 before = data.posNFramesAgo(FRAMES);
            if (before == null) continue;

            long dtMs = TimeUtil.tickMs() - data.lastPosTickMs;
            if (dtMs <= 0) continue;

            // 三维欧氏距离（瞬移往往含 y 轴突变）
            double dist = before.distanceTo(now);
            double speed = dist / (dtMs / 1000.0);

            double limit = VANILLA_LIMIT_SPEED * cfg.getLimitMultiplier()
                    * mod.getTpsAdaptiveManager().multiplier();

            if (speed > limit) {
                flag(p, 1, String.format("位移速度 %.2f b/s（限制 %.2f）", speed, limit));
            }
        }
    }
}
