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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ReportRecord —— 举报单实体
 * <p>
 * 一次 /report 举报对应一条记录，记录举报人、被举报人、
 * 备注、查端状态与核验结果。
 */
public class ReportRecord {

    /** 举报单唯一 id */
    private String id;
    /** 举报人 UUID */
    private UUID reporterUuid;
    /** 举报人名称 */
    private String reporterName;
    /** 被举报人 UUID */
    private UUID targetUuid;
    /** 被举报人名称 */
    private String targetName;
    /** 举报备注 */
    private String remark;
    /** 举报时间戳(ms) */
    private long createdAtMs;
    /** 强制查端状态 */
    private CheckClientStatus checkStatus = CheckClientStatus.PENDING;
    /** 查端命中黑名单的 modId（未命中为空） */
    private final List<String> matchedMods = new ArrayList<>();
    /** 目标上报的完整 modId 列表 */
    private final List<String> reportedModIds = new ArrayList<>();
    /** 核验结论（供管理员复核） */
    private String verdict;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getReporterUuid() { return reporterUuid; }
    public void setReporterUuid(UUID reporterUuid) { this.reporterUuid = reporterUuid; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public UUID getTargetUuid() { return targetUuid; }
    public void setTargetUuid(UUID targetUuid) { this.targetUuid = targetUuid; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public CheckClientStatus getCheckStatus() { return checkStatus; }
    public void setCheckStatus(CheckClientStatus checkStatus) { this.checkStatus = checkStatus; }

    public List<String> getMatchedMods() { return matchedMods; }
    public List<String> getReportedModIds() { return reportedModIds; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
}
