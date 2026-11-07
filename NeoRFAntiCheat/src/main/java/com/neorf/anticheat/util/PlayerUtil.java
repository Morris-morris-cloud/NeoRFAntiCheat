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
package com.neorf.anticheat.util;

import net.minecraft.server.level.ServerPlayer;

/**
 * PlayerUtil —— 玩家相关工具
 */
public final class PlayerUtil {

    private PlayerUtil() {}

    /** 玩家是否处于可检测的"活跃"状态（排除旁观模式等） */
    public static boolean isDetectable(ServerPlayer p) {
        if (p.isSpectator()) return false;
        if (!p.isAlive()) return false;
        return true;
    }

    /** 格式化玩家名（含 UUID 短码）用于日志 */
    public static String describe(ServerPlayer p) {
        String uuid = p.getUUID().toString().substring(0, 8);
        return p.getGameProfile().name() + "(" + uuid + ")";
    }
}
