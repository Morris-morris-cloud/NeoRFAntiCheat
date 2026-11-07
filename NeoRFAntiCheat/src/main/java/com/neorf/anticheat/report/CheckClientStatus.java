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

/**
 * CheckClientStatus —— 强制查端状态枚举
 * <p>
 * /report 触发强制查端后，目标客户端的状态流转：
 * <pre>
 * PENDING ──收到 ModList──▶ SUCCESS（比对黑名单 → 命中封禁 / 未命中归档复核）
 *    │
 *    ├──超时──▶ TIMEOUT（查端超时，归档）
 *    └──拒绝/不支持 Fabric payload──▶ NON_FABRIC（客户端非 Fabric，无法读取 ModList）
 * </pre>
 */
public enum CheckClientStatus {
    /** 已发查端请求，等待 ModList 返回 */
    PENDING,
    /** 已收到 ModList 并完成黑名单比对（含命中/未命中结果字段） */
    SUCCESS,
    /** 查端超时（可配置，默认 5s） */
    TIMEOUT,
    /** 客户端拒绝或不支持 Fabric payload（原版无 Fabric） */
    NON_FABRIC,
    /** 客户端返回了无效 payload 或空 ModList */
    INVALID_RESPONSE,
    /** 命中黑名单且触发封禁 */
    BLACKLIST_MATCH
}
