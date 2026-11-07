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

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * PacketUtil —— 数据包工具
 * <p>
 * 用于 PacketFlood 检测的入站包计数挂钩，以及自定义 payload 收发辅助。
 * 具体 netty 管道 API 随 MC 版本内部结构变化，接入时按 26.1 校订。
 */
public final class PacketUtil {

    private PacketUtil() {}

    /**
     * 判断某入站包是否属于"高频类"（可据此决定是否计数/限流）。
     * 实际按包类型（如移动包、交互包）细化。
     */
    public static boolean isHighFrequency(Packet<?> packet) {
        return true; // 简化：所有入站包都纳入洪水统计
    }

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

    /**
     * 玩家连接建立时的管道钩子入口说明（接入点）：
     * <pre>
     * 在 ServerPlayConnectionEvents.JOIN 中，获取该连接的 netty pipeline，
     * 在末端加入 {@code PacketFloodCheck#onPacket} 的 ChannelInboundHandler。
     * </pre>
     * 因依赖内部类，此处只保留调用约定，具体实现按版本补充。
     */
    public static void installFloodCounter(ServerPlayer player, FloodCounter counter) {
        // 接入点：pipeline.addLast("neorf-flood", ...)
    }

    /** 入站包计数回调接口（由 PacketFloodCheck 实现） */
    @FunctionalInterface
    public interface FloodCounter {
        void count(ServerPlayer player);
    }
}
