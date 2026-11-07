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
import com.neorf.anticheat.util.TimeUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * FlyCheck —— 飞行检测
 * <p>
 * 判定逻辑：玩家持续"空中且无合法支持"：
 * <ul>
 *   <li>排除合法支持：漂浮(levitation)、鞘翅、水流、粘液块、创造/飞行权限；</li>
 *   <li>排除合法瞬态：台阶、跳跃上升段、气泡柱；</li>
 *   <li>计算垂直速度与悬空持续时间，持续违规累计违规点，达阈值触发封禁。</li>
 * </ul>
 */
public class FlyCheck extends Check {

    public FlyCheck(NeoRFAntiCheat mod) {
        super(mod, "Fly", "movement");
    }

    @Override
    public String id() { return "fly"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getMovement().getFly().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getMovement().getFly().getViolationsToBan();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        AcConfig.Fly cfg = mod.getConfigManager().get().getMovement().getFly();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mod.getExemptionManager().isExempt(p, id())) continue;
            if (hasLegalSupport(p)) {
                // 回到合法状态，重置悬空计时
                mod.getTickDataManager().get(p.getUUID()).suspiciousAirSince = -1;
                continue;
            }
            // 无合法支持：开始或延续计时
            var data = mod.getTickDataManager().get(p.getUUID());
            long now = TimeUtil.tickMs();
            if (data.suspiciousAirSince < 0) {
                data.suspiciousAirSince = now;
            } else if (now - data.suspiciousAirSince >= cfg.getMinSuspiciousAirMs()) {
                // 持续悬空达到阈值 → 记一次违规（按毫秒折算违规点，避免一帧刷满）
                int amount = (int) Math.min(5, (now - data.suspiciousAirSince) / 1000L);
                flag(p, amount, "持续无支持空中 " + (now - data.suspiciousAirSince) + "ms");
            }
        }
    }

    /** 判断玩家当前是否有合法"飞行/漂浮"支持 */
    private boolean hasLegalSupport(ServerPlayer p) {
        // 创造或允许飞行
        if (p.isCreative() || p.getAbilities().mayfly) return true;
        // 地面
        if (p.onGround()) return true;
        // 水中 / 气泡柱
        if (p.isInWater() || p.isUnderWater()) return true;
        // 垂直速度：上升段（跳跃/活塞）合法；持续无支撑下落才可疑
        if (p.getDeltaMovement().y > 0) return true;
        // 鞘翅滑翔
        if (p.isFallFlying()) return true;
        // 漂浮药水效果
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)) return true;
        // 脚下粘液块 / 台阶等半格支持
        BlockPos below = p.blockPosition().below();
        var state = p.level().getBlockState(below);
        if (state.is(Blocks.SLIME_BLOCK) || !state.isSolidRender()) return true;

        return false;
    }
}
