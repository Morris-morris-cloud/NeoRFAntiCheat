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

import com.neorf.anticheat.core.snapshot.SnapshotBuilder;

import java.util.UUID;

/**
 * BanRecord —— 封禁记录实体类
 * <p>
 * 与存储层（JSON）直接对应，一次封禁/解除操作产生一条记录。
 * 核心字段：
 * <ul>
 *   <li>{@code uuid}：玩家 UUID（主键，阶梯判定基于它）；</li>
 *   <li>{@code status}：当前状态（ACTIVE/EXPIRED/FALSE_POSITIVE/MANUAL_UNBAN）；</li>
 *   <li>{@code banStartMs}/{@code banEndMs}：封禁起止时间；</li>
 *   <li>{@code evidence}：证据快照（坐标、tick、ping、动作数据）。</li>
 * </ul>
 */
public class BanRecord {

    /** 玩家 UUID（主键） */
    private UUID uuid;
    /** 玩家名（快照用，可能改名） */
    private String name;
    /** 记录状态 */
    private BanStatus status = BanStatus.ACTIVE;
    /** 触发检测名（如 "fly"、"report-mod"） */
    private String checkName;
    /** 封禁开始时间戳(ms) */
    private long banStartMs;
    /** 封禁结束时间戳(ms)，-1 表示永久封禁 */
    private long banEndMs = -1;
    /** 封禁时长(ms)，-1 表示永久 */
    private long durationMs = -1;
    /** 是否为自动封禁（vs 管理员手动） */
    private boolean automatic;
    /** 证据快照 */
    private SnapshotBuilder.Snapshot evidence;

    /** 记录创建时间(ms) */
    private long createdAtMs;

    public BanRecord() {}

    public UUID getUUID() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public BanStatus getStatus() { return status; }
    public void setStatus(BanStatus status) { this.status = status; }

    public String getCheckName() { return checkName; }
    public void setCheckName(String checkName) { this.checkName = checkName; }

    public long getBanStartMs() { return banStartMs; }
    public void setBanStartMs(long banStartMs) { this.banStartMs = banStartMs; }

    public long getBanEndMs() { return banEndMs; }
    public void setBanEndMs(long banEndMs) { this.banEndMs = banEndMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isAutomatic() { return automatic; }
    public void setAutomatic(boolean automatic) { this.automatic = automatic; }

    public SnapshotBuilder.Snapshot getEvidence() { return evidence; }
    public void setEvidence(SnapshotBuilder.Snapshot evidence) { this.evidence = evidence; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    /** 该记录当前是否仍生效 */
    public boolean isActiveNow(long nowMs) {
        if (status != BanStatus.ACTIVE) return false;
        return banEndMs < 0 || nowMs < banEndMs;
    }

    /** 是否为永久封禁 */
    public boolean isPermanent() {
        return banEndMs < 0;
    }
}
