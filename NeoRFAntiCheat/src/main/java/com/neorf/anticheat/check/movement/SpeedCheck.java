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
 * SpeedCheck —— 加速检测
 * <p>
 * 判定逻辑：
 * <ul>
 *   <li>根据玩家状态（行走/奔跑/潜行/游泳）取原版理论最大水平速度；</li>
 *   <li>用"间隔 N 帧的水平位移 / 时间"计算实测速度，过滤服务端 tick 抖动；</li>
 *   <li>自动扣除敏捷药水、信标速度加成等合法 buff 带来的加成；</li>
 *   <li>实测速度超过理论上限 × (1 + overSpeedRatio) 记为违规。</li>
 * </ul>
 */
public class SpeedCheck extends Check {

    public SpeedCheck(NeoRFAntiCheat mod) {
        super(mod, "Speed", "movement");
    }

    @Override
    public String id() { return "speed"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getMovement().getSpeed().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getMovement().getSpeed().getViolationsToBan();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        // 服务器过卡时跳过，避免误判
        if (mod.getTpsAdaptiveManager().shouldSkipTimingChecks()) return;

        AcConfig.Speed cfg = mod.getConfigManager().get().getMovement().getSpeed();
        // 用 5 帧（约 0.25s）的位移算平均速度，平滑 tick 抖动
        final int FRAMES = 5;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mod.getExemptionManager().isExempt(p, id())) continue;

            var data = mod.getTickDataManager().get(p.getUUID());
            Vec3 now = p.position();
            Vec3 before = data.posNFramesAgo(FRAMES);
            if (before == null || before.distanceToSqr(now) == 0) continue;

            long dtMs = TimeUtil.tickMs() - data.lastPosTickMs;
            if (dtMs <= 0) continue;

            // 水平位移
            double horiz = MathUtil.horizontalDistance(before, now);
            double speed = horiz / (dtMs / 1000.0); // 方块/秒

            double maxSpeed = theoreticalMaxSpeed(p);
            double allowed = maxSpeed * (1.0 + cfg.getOverSpeedRatio()) * mod.getTpsAdaptiveManager().multiplier();

            if (speed > allowed && speed > maxSpeed) {
                flag(p, 1, String.format("速度 %.2f b/s，上限 %.2f b/s", speed, allowed));
            }
        }
    }

    /**
     * 计算玩家在当前状态下的原版理论最大水平速度（b/s），
     * 并叠加敏捷/信标等合法 buff 加成。
     */
    private double theoreticalMaxSpeed(ServerPlayer p) {
        // 原版基准（近似，单位 b/tick），随版本校订
        double base;
        if (p.isCrouching()) {
            base = 0.13; // 潜行
        } else if (p.isInWater() || p.isSwimming()) {
            base = 0.15; // 游泳
        } else if (p.isSprinting()) {
            base = 0.28; // 奔跑
        } else {
            base = 0.22; // 行走
        }
        double speed = base * 20.0; // 转成 b/s

        // 敏捷药水：每级 +20% 速度
        var speedEffect = p.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
        if (speedEffect != null) {
            int lvl = speedEffect.getAmplifier() + 1;
            speed *= (1.0 + 0.2 * lvl);
        }
        // 信标/潮涌等也可在此追加（配置化的放行比例已在 overSpeedRatio 中体现）
        return speed;
    }
}
