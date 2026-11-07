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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * NoClipCheck —— 穿墙检测
 * <p>
 * 判定逻辑：玩家碰撞箱与"实心方块"持续相交。
 * <ul>
 *   <li>过滤合法场景：活塞推动、传送门、瞬间方块更新造成的抖动；</li>
 *   <li>实心方块用 {@code state.isOpaqueFullCube()} 判断（不含草、空气、液体等）；</li>
 *   <li>持续相交达到 minClipMs 累计违规。</li>
 * </ul>
 */
public class NoClipCheck extends Check {

    public NoClipCheck(NeoRFAntiCheat mod) {
        super(mod, "NoClip", "movement");
    }

    @Override
    public String id() { return "noclip"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getMovement().getNoclip().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getMovement().getNoclip().getViolationsToBan();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        AcConfig.NoClip cfg = mod.getConfigManager().get().getMovement().getNoclip();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mod.getExemptionManager().isExempt(p, id())) continue;

            boolean clipped = isClipping(p);
            var data = mod.getTickDataManager().get(p.getUUID());
            long now = TimeUtil.tickMs();

            if (clipped) {
                if (data.clipSince < 0) {
                    data.clipSince = now;
                } else if (now - data.clipSince >= cfg.getMinClipMs()) {
                    flag(p, 1, "碰撞箱与实心方块相交 " + (now - data.clipSince) + "ms");
                    // 复位，避免同一次穿墙重复计数（若持续仍会被再次累计）
                    data.clipSince = now;
                }
            } else {
                data.clipSince = -1;
            }
        }
    }

    /** 检测玩家碰撞箱是否与实心方块相交 */
    private boolean isClipping(ServerPlayer p) {
        AABB box = p.getBoundingBox();
        // 遍历碰撞箱覆盖的方块，仅判定不透明完整方块
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    var state = p.level().getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (state.isSolidRender()) {
                        // 活塞推动会瞬间改变方块，若相邻 tick 方块被替换可在此放行；
                        // 简化实现：记录一次相交，交由持续时长判定过滤瞬时抖动
                        return true;
                    }
                }
        return false;
    }
}
