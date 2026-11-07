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
package com.neorf.anticheat.report;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * FabricModListPayload —— 自定义 payload 包封装（强制查端用）
 * <p>
 * 定义两个方向的 Fabric 自定义 Payload：
 * <ul>
 *   <li>{@link Request}：服务端 → 客户端，请求上报 ModList；</li>
 *   <li>{@link Response}：客户端 → 服务端，上报全部 Fabric Loader 注册的 modId。</li>
 * </ul>
 *
 * <p><b>重要限制（写入文档）：</b>通过该机制只能拿到 Fabric Loader 注册上报的
 * modId；进程注入外挂、非 Fabric 作弊程序<b>不会</b>出现在 ModList 中，
 * 因此无法通过此手段检出。详见 docs/风险局限性说明.md。
 */
public class FabricModListPayload {

    private FabricModListPayload() {}

    /**
     * 请求 ModList 的 payload（服务端 → 客户端）。
     * 载荷携带举报单 id，客户端响应时原样带回，用于关联举报单。
     */
    public record Request(String reportId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("neorf", "request_modlist"));
        /** 简化编解码：仅一个字符串字段 */
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Request::reportId, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * 返回 ModList 的 payload（客户端 → 服务端）。
     * 载荷为客户端全部已注册 modId 列表（小写）。
     */
        public record Response(String reportId, List<String> modIds) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Response> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("neorf", "response_modlist"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> MOD_IDS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Response::reportId,
                MOD_IDS_CODEC, Response::modIds, Response::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
