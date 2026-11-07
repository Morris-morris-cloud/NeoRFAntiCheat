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
package com.neorf.anticheat.check.action;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.Check;
import com.neorf.anticheat.config.AcConfig;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NukerCheck —— 批量挖方块检测
 * <p>
 * 判定逻辑：短时间连续破坏大量方块且无合理间隔。
 * <ul>
 *   <li>统计窗口内被破坏的方块数；</li>
 *   <li>排除合法批量破坏：TNT、爆炸、以及白名单机械模组触发的破坏
 *       （通过事件源区分，机械模组走独立通道则不计数）；</li>
 * </ul>
 */
public class NukerCheck extends Check {

    /** 每个玩家窗口内被破坏方块的时间戳（毫秒） */
    private final Map<UUID, Deque<Long>> breakTimes = new ConcurrentHashMap<>();

    public NukerCheck(NeoRFAntiCheat mod) {
        super(mod, "Nuker", "action");
        registerEvent();
    }

    @Override
    public String id() { return "nuker"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getAction().getNuker().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getAction().getNuker().getViolationsToBan();
    }

    /** 订阅方块破坏事件 */
    private void registerEvent() {
        // AFTER 事件在方块实际破坏后触发，且带破坏者信息
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp) {
                onBlockBreak(sp, state);
            }
        });
    }

    private void onBlockBreak(ServerPlayer p, net.minecraft.world.level.block.state.BlockState state) {
        if (!isEnabled()) return;
        if (mod.getExemptionManager().isExempt(p, id())) return;
        // 排除 TNT / 爆炸等合法批量破坏（此处按方块类型过滤，实际应结合事件源）
        if (state.is(Blocks.TNT)) return;

        AcConfig.Nuker cfg = mod.getConfigManager().get().getAction().getNuker();
        long now = System.currentTimeMillis();

        Deque<Long> times = breakTimes.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > cfg.getWindowMs()) {
            times.pollFirst();
        }

        if (times.size() >= cfg.getMaxBlocksPerWindow()) {
            flag(p, 1, "窗口 " + cfg.getWindowMs() + "ms 内破坏 " + times.size() + " 方块");
            times.clear();
        }
    }
}
