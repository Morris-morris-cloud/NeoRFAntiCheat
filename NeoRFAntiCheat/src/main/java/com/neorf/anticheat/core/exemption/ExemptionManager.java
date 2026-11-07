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
package com.neorf.anticheat.core.exemption;

import com.neorf.anticheat.config.AcConfig;
import com.neorf.anticheat.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ExemptionManager —— 豁免 / 白名单管理器
 * <p>
 * 支持三层豁免：
 * <ul>
 *   <li><b>UUID 白名单</b>：配置 {@code whitelist.exemptUuids}，完全豁免全部检测；</li>
 *   <li><b>权限节点</b>：玩家拥有 {@code whitelist.exemptPermissionPrefix + "." + checkName}
 *       （如 {@code neorf.exempt.fly}）即豁免对应检测；</li>
 *   <li><b>区域白名单</b>：主城等区域（配置 {@code whitelist.regions}）内关闭指定检测。</li>
 * </ul>
 */
public class ExemptionManager {

    private final ConfigManager configManager;
    /** 缓存已解析的 UUID，避免每次 String->UUID 转换 */
    private final Set<UUID> exemptUuidsCache = new HashSet<>();

    public ExemptionManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** 配置重载后刷新 UUID 缓存 */
    public void refresh() {
        exemptUuidsCache.clear();
        for (String s : configManager.get().getWhitelist().getExemptUuids()) {
            try {
                exemptUuidsCache.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // 忽略无效 UUID 配置项
            }
        }
    }

    /**
     * 玩家是否被完全豁免（UUID 白名单）。
     */
    public boolean isFullyExempt(ServerPlayer player) {
        return exemptUuidsCache.contains(player.getUUID());
    }

    /**
     * 玩家是否被权限豁免指定检测。
     *
     * @param checkName 检测名（如 "fly"），权限节点 neorf.exempt.fly
     */
    public boolean isPermissionExempt(ServerPlayer player, String checkName) {
        // 简化实现：未引入 fabric-permissions-api 时，用 op 权限等级近似——
        // 权限等级 >= 1（可执行命令的 op）视为豁免全部检测。
        // 若需按节点精细控制（neorf.exempt.fly 仅豁免飞行），
        // 可引入 Fabric Permissions API 并改为：
        //   return PermissionAPI.checkPermission(player, prefix + "." + checkName);
        return player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    /**
     * 玩家在指定位置、指定检测是否被区域白名单豁免。
     */
    public boolean isRegionExempt(ServerPlayer player, String checkName) {
        String worldId = player.level().dimension().identifier().toString();
        for (AcConfig.Region r : configManager.get().getWhitelist().getRegions()) {
            if (!worldId.equals(r.level())) continue;
            // 粗略权限判定：坐标在矩形内
            BlockPos p = player.blockPosition();
            if (p.getX() >= r.getMinX() && p.getX() <= r.getMaxX()
                    && p.getY() >= r.getMinY() && p.getY() <= r.getMaxY()
                    && p.getZ() >= r.getMinZ() && p.getZ() <= r.getMaxZ()) {
                if (r.getDisabledChecks().contains(checkName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 综合判定：玩家是否应对指定检测完全豁免。
     */
    public boolean isExempt(ServerPlayer player, String checkName) {
        if (isFullyExempt(player)) return true;
        if (isPermissionExempt(player, checkName)) return true;
        return isRegionExempt(player, checkName);
    }
}
