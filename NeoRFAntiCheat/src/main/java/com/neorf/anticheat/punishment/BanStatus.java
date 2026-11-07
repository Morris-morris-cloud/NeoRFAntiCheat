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
package com.neorf.anticheat.punishment;

/**
 * BanStatus —— 封禁记录状态枚举
 * <p>
 * 状态流转（详见 docs/封禁状态流转.md）：
 * <pre>
 * ┌─────────────┐   到期自动解除     ┌──────────────┐
 * │   ACTIVE    │ ───────────────▶ │   EXPIRED    │  （计入 validBanCount）
 * └─────┬───────┘                  └──────────────┘
 *       │ 管理员 unban falsepositive
 *       ▼
 * ┌──────────────┐   不计入计数
 * │ FALSE_POSITIVE│  （不叠加阶梯）
 * └──────────────┘
 *
 * MANUAL_UNBAN：管理员普通解除（是否计入计数由配置 manualUnbanCounts 决定）
 * </pre>
 */
public enum BanStatus {
    /** 生效封禁（当前正在封禁中） */
    ACTIVE,
    /** 到期自动解除（计入 validBanCount） */
    EXPIRED,
    /** 管理员标记误封解除（不计入 validBanCount，不叠加次数） */
    FALSE_POSITIVE,
    /** 管理员普通解除（可配置是否计入计数） */
    MANUAL_UNBAN
}
