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
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * ReachCheck —— 超距攻击检测
 * <p>
 * 判定逻辑：攻击射线距离校验。
 * <ul>
 *   <li>计算玩家眼睛位置到被攻击实体的最近距离；</li>
 *   <li>超过原版近战攻击上限（默认 3.2 格）判为超距；</li>
 *   <li>排除原版合法特性：远程武器（弓箭/三叉戟）、攻击范围附魔等。</li>
 * </ul>
 * 由服务端攻击 packet 检测入口调用。
 */
public class ReachCheck extends Check {

    public ReachCheck(NeoRFAntiCheat mod) {
        super(mod, "Reach", "combat");
    }

    @Override
    public String id() { return "reach"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getCombat().getReach().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getCombat().getReach().getViolationsToBan();
    }

    public void onServerAttack(ServerPlayer p, Entity target) {
        if (!isEnabled()) return;
        if (mod.getExemptionManager().isExempt(p, id())) return;
        // 远程武器不适用近战距离检测
        if (isRanged(p)) return;

        AcConfig.Reach cfg = mod.getConfigManager().get().getCombat().getReach();

        // 玩家眼睛位置到目标碰撞箱的最近距离
        Vec3 eye = p.getEyePosition();
        double dist = MathUtil.distanceToBox(eye, target.getBoundingBox());

        double allowed = cfg.getMaxReach() * mod.getTpsAdaptiveManager().multiplier();
        // 附加宽容，规避 ping 高导致的客户端位置偏差
        allowed += Math.max(0, p.connection.latency() / 1000.0 * 0.5);

        if (dist > allowed) {
            flag(p, 1, String.format("攻击距离 %.2f 格（上限 %.2f）", dist, allowed));
        }
    }

    /** 是否为远程攻击（射箭、投掷三叉戟等） */
    private boolean isRanged(ServerPlayer p) {
        var main = p.getMainHandItem();
        var off = p.getOffhandItem();
        // 远程武器在近战距离校验中放行（此处按物品类型判断，版本相关）
        return false; // 具体物品判定按 26.1 物品表补充
    }
}
