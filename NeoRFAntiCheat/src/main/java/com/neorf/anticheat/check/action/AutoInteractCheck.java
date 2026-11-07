
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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoInteractCheck —— 自动放置 / 自动交互检测
 * <p>
 * 判定逻辑：方块放置、右键交互的间隔频率。
 * <ul>
 *   <li>统计玩家右键交互（放置方块、使用物品）的时间戳；</li>
 *   <li>间隔低于配置阈值（如 120ms）且持续稳定 → 识别固定周期自动化点击；</li>
 * </ul>
 */
public class AutoInteractCheck extends Check {

    /** 每个玩家的交互时间戳队列（毫秒） */
    private final Map<UUID, Deque<Long>> interactTimes = new ConcurrentHashMap<>();

    public AutoInteractCheck(NeoRFAntiCheat mod) {
        super(mod, "AutoInteract", "action");
        registerEvent();
    }

    @Override
    public String id() { return "autointeract"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getAction().getAutoInteract().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        return mod.getConfigManager().get().getAction().getAutoInteract().getViolationsToBan();
    }

    private void registerEvent() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer sp) {
                onInteract(sp);
            }
            return InteractionResult.PASS;
        });
    }

    private void onInteract(ServerPlayer p) {
        if (!isEnabled()) return;
        if (mod.getExemptionManager().isExempt(p, id())) return;

        AcConfig.AutoInteract cfg = mod.getConfigManager().get().getAction().getAutoInteract();
        long now = System.currentTimeMillis();

        Deque<Long> times = interactTimes.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        times.addLast(now);
        while (times.size() > 6) times.pollFirst(); // 只保留最近 6 次

        if (times.size() >= 4) {
            // 计算最近间隔是否稳定且过低 → 固定周期自动化
            Long[] arr = times.toArray(new Long[0]);
            double avg = 0;
            for (int i = 1; i < arr.length; i++) avg += (arr[i] - arr[i - 1]);
            avg /= (arr.length - 1);

            double var = 0;
            for (int i = 1; i < arr.length; i++) {
                double d = (arr[i] - arr[i - 1]) - avg;
                var += d * d;
            }
            var /= (arr.length - 1);

            if (avg < cfg.getMinIntervalMs() && var < 4.0) {
                flag(p, 1, "固定周期自动交互，平均间隔 " + String.format("%.0f", avg) + "ms");
                times.clear();
            }
        }
    }
}
