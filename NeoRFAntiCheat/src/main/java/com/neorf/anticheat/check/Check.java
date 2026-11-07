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
package com.neorf.anticheat.check;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.core.exemption.ExemptionManager;
import com.neorf.anticheat.core.snapshot.SnapshotBuilder;
import com.neorf.anticheat.core.tick.PlayerTickDataManager;
import com.neorf.anticheat.punishment.BanManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Check —— 所有检测模块的统一抽象基类
 * <p>
 * 把"违规点累计 → 证据快照 → 触发封禁"的公共流程收敛到这里，
 * 每个具体检测只负责：判断是否违规，并调用 {@link #flag} 上报。
 *
 * <p>流程：
 * <ol>
 *   <li>检测逻辑判定违规 → {@link #flag(ServerPlayer, int, String)}；</li>
 *   <li>{@link #flag} 先做豁免判定（UUID/权限/区域白名单）；</li>
 *   <li>未豁免则累计违规点，到达阈值 → 构造证据快照 → 交
 *       {@link BanManager#applyAutomaticBan} 走阶梯封禁；</li>
 *   <li>封禁后清零该检测违规点。</li>
 * </ol>
 *
 * <p>检测子类实现 {@link #id()}（唯一标识，也用作权限节点与配置键），
 * 并按需重写 {@link #onServerTick} 或订阅 Fabric 事件。
 */
public abstract class Check {

    /** 模组实例（访问各管理器） */
    protected final NeoRFAntiCheat mod;
    /** 检测展示名（如 Fly、Speed） */
    private final String displayName;
    /** 检测类别（movement/combat/action/packet） */
    private final String category;

    protected Check(NeoRFAntiCheat mod, String displayName, String category) {
        this.mod = mod;
        this.displayName = displayName;
        this.category = category;
    }

    /** 检测唯一标识（小写，如 "fly"），用于配置、权限、违规点登记 */
    public abstract String id();

    /** 该检测当前是否启用（由子类读取对应配置段） */
    public abstract boolean isEnabled();

    /** 服务端 tick 轮询钩子（移动类检测在此实现） */
    public void onServerTick(MinecraftServer server) {
        // 默认空实现，事件驱动类检测无需重写
    }

    /**
     * 上报一次违规。内部处理豁免、累计、封禁触发。
     *
     * @param player  违规玩家
     * @param amount  本次增加的违规点数
     * @param detail  人类可读细节（写入证据快照）
     */
    protected void flag(ServerPlayer player, int amount, String detail) {
        flagAtThreshold(player, amount, detail, violationsToBan());
    }

    protected void flagAtThreshold(ServerPlayer player, int amount, String detail, int banThreshold) {
        if (!isEnabled() || !mod.getConfigManager().get().getGeneral().isEnabled()) return;
        // 豁免判定
        ExemptionManager exemption = mod.getExemptionManager();
        if (exemption.isExempt(player, id())) {
            return;
        }

        PlayerTickDataManager tick = mod.getTickDataManager();
        int v = tick.get(player.getUUID()).addViolation(id(), amount);
        int threshold = Math.max(1, banThreshold);

        if (v >= threshold) {
            // 构造证据快照并触发自动封禁
            SnapshotBuilder.Snapshot snap = mod.getSnapshotBuilder().capture(player, id(), detail);
            boolean banned = mod.getBanManager().applyAutomaticBan(player, id(), snap);
            if (banned) {
                // 封禁成功，清零违规点，避免反复触发
                tick.get(player.getUUID()).resetViolations(id());
            }
        }
    }

    protected void clearViolations(ServerPlayer player) {
        mod.getTickDataManager().get(player.getUUID()).resetViolations(id());
    }

    /** 该检测触发封禁所需的违规点阈值（由子类从配置读取） */
    protected abstract int violationsToBan();

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
}
