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

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * PacketUtil —— 自定义 payload 收发辅助。
 */
public final class PacketUtil {

    private PacketUtil() {}

    /**
     * 向玩家发送自定义 payload（封装 ServerPlayNetworking.send）。
     * 返回是否发送成功。
     */
    public static boolean sendPayload(ServerPlayer player, CustomPacketPayload payload) {
        try {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

}
