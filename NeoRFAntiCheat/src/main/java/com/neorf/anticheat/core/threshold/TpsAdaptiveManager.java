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
package com.neorf.anticheat.core.threshold;

import com.neorf.anticheat.config.AcConfig;
import com.neorf.anticheat.config.ConfigManager;
import net.minecraft.server.MinecraftServer;

/**
 * TpsAdaptiveManager —— TPS 自适应阈值管理器
 * <p>
 * 反作弊最大误判来源是"服务器卡顿"：当服务端 MSPT 过高、TPS 掉帧时，
 * 玩家的合法移动会因为 tick 间隔不均而被误判为瞬移/加速。
 *
 * <p>策略：
 * <ul>
 *   <li>实时采样服务端 MSPT 与 TPS；</li>
 *   <li>当 MSPT 超过配置阈值时，进入"降敏模式"，对时序敏感检测
 *       （瞬移、速度）返回一个放大系数，放宽判定阈值；</li>
 *   <li>当 TPS 低于 {@link AcConfig.General#getMinTpsForTimingChecks()} 时，
 *       直接让时序敏感检测跳过（返回 0 或禁用标志）。</li>
 * </ul>
 */
public class TpsAdaptiveManager {

    private final ConfigManager configManager;
    /** 最近一次采样到的 MSPT(ms) */
    private volatile long currentMspt;
    /** 最近一次采样到的 TPS */
    private volatile double currentTps = 20.0;
    /** 上次 tick 时间戳，用于计算 MSPT */
    private long lastTickNanos = -1;

    public TpsAdaptiveManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** 服务器启动：初始化采样基线 */
    public void onServerStart(MinecraftServer server) {
        lastTickNanos = System.nanoTime();
        currentTps = 20.0;
    }

    /** 服务端 tick 调用：更新采样 */
    public void onServerTick(MinecraftServer server) {
        long now = System.nanoTime();
        if (lastTickNanos > 0) {
            long deltaNanos = now - lastTickNanos;
            // MSPT = 两次 tick 间隔（毫秒）
            currentMspt = deltaNanos / 1_000_000L;
            // 简单的指数滑动平均平滑 TPS 抖动
            double instantTps = 1000.0 / Math.max(1, currentMspt);
            currentTps = currentTps * 0.8 + Math.min(20.0, instantTps) * 0.2;
        }
        lastTickNanos = now;
    }

    /**
     * 查询当前 TPS 自适应放大系数。
     *
     * @return 阈值放大系数：正常 1.0；MSPT 高时按比例放大（最多 2.0），
     *         检测方把原始阈值乘以该系数即可放宽。
     */
    public double multiplier() {
        AcConfig.General g = configManager.get().getGeneral();
        if (currentMspt <= g.getTpsAdaptiveMsptThreshold()) {
            return 1.0;
        }
        double ratio = (double) currentMspt / g.getTpsAdaptiveMsptThreshold();
        return Math.min(2.0, ratio);
    }

    /**
     * 时序敏感检测是否应跳过。
     *
     * @return true 表示服务器过卡，应跳过瞬移/速度等检测
     */
    public boolean shouldSkipTimingChecks() {
        AcConfig.General g = configManager.get().getGeneral();
        return currentTps < g.getMinTpsForTimingChecks();
    }

    /** 当前 TPS（对外展示用） */
    public double getCurrentTps() { return currentTps; }
    /** 当前 MSPT（对外展示用） */
    public long getCurrentMspt() { return currentMspt; }
}
