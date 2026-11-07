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

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.config.ConfigManager;
import com.neorf.anticheat.punishment.BanManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ReportManager —— 举报管理、冷却缓存、payload 发包
 * <p>
 * 职责：
 * <ol>
 *   <li>解析 /report 请求，校验举报人全局冷却（内存缓存，重启清空）；</li>
 *   <li>入库举报记录，设置举报人冷却截止时间；</li>
 *   <li>强制查端：发送 Fabric 自定义 Payload 包，请求目标客户端上报 ModList；</li>
 *   <li>监听 ModList 返回，与黑名单比对，走封禁流程或归档。</li>
 * </ol>
 */
public class ReportManager {

    private final ConfigManager configManager;
    private final BanManager banManager;
    private final NeoRFAntiCheat mod;

    /** 举报人冷却截止时间缓存（内存，重启清空）：UUID → 可再次举报的时间戳 */
    private final Map<UUID, Long> reporterCooldownUntil = new ConcurrentHashMap<>();
    /** 举报单列表 */
    private final List<ReportRecord> reports = new ArrayList<>();
    /** targetUuid → 进行中的举报（查端回调用） */
    private final Map<UUID, ReportRecord> pendingCheck = new ConcurrentHashMap<>();
    /** 查端超时任务统一调度，避免裸线程泄漏 */
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NeoRF-ClientCheck-Timeout");
        t.setDaemon(true);
        return t;
    });

    public ReportManager(ConfigManager configManager, BanManager banManager) {
        this.configManager = configManager;
        this.banManager = banManager;
        this.mod = NeoRFAntiCheat.getInstance();
    }

    /** 服务器启动：注册 payload 收发 */
    public void onServerStart(MinecraftServer server) {
        registerPayload();
    }

    /* ---------------- 举报流程 ---------------- */

    /**
     * 处理 /report 请求。
     *
     * @param reporter 举报人
     * @param target   被举报人
     * @param remark   备注（可为空）
     * @return 给举报人的反馈文案
     */
    public String submitReport(ServerPlayer reporter, ServerPlayer target, String remark) {
        UUID ruuid = reporter.getUUID();

        // 1) 冷却校验
        long now = System.currentTimeMillis();
        Long until = reporterCooldownUntil.get(ruuid);
        if (until != null && now < until) {
            long remainSec = (until - now) / 1000;
            return "举报冷却中，请 " + remainSec + " 秒后再试。";
        }

        // 2) 入库举报记录
        ReportRecord rec = new ReportRecord();
        rec.setId(UUID.randomUUID().toString().substring(0, 8));
        rec.setReporterUuid(ruuid);
        rec.setReporterName(reporter.getGameProfile().name());
        rec.setTargetUuid(target.getUUID());
        rec.setTargetName(target.getGameProfile().name());
        rec.setRemark(remark == null ? "" : remark);
        rec.setCreatedAtMs(now);
        reports.add(rec);

        // 3) 设置举报人冷却截止时间
        reporterCooldownUntil.put(ruuid, now + configManager.get().getReport().getReporterCooldownMs());

        // 4) 强制查端
        if (configManager.get().getReport().isForceClientCheck()) {
            startClientCheck(rec, target);
        } else {
            rec.setCheckStatus(CheckClientStatus.NON_FABRIC);
            rec.setVerdict("未启用强制查端，等待管理员复核");
        }

        return "已举报 " + rec.getTargetName() + "，查端已发起，结果将归档。";
    }

    /** 发起强制查端 */
    private void startClientCheck(ReportRecord rec, ServerPlayer target) {
        rec.setCheckStatus(CheckClientStatus.PENDING);
        pendingCheck.put(target.getUUID(), rec);

        // 发送请求 ModList 的自定义 payload（载荷携带举报单 id）
        ServerPlayNetworking.send(target, new FabricModListPayload.Request(rec.getId()));

        // 开启超时计时器：可配置，默认 5s；超时标记 TIMEOUT
        long timeoutMs = configManager.get().getReport().getClientCheckTimeoutMs();
        scheduleTimeout(rec, timeoutMs);
    }

    /** 使用线程池调度超时，保证查端状态可控且不会泄漏线程。 */
    private void scheduleTimeout(ReportRecord rec, long timeoutMs) {
        timeoutExecutor.schedule(() -> {
            if (rec.getCheckStatus() == CheckClientStatus.PENDING) {
                rec.setCheckStatus(CheckClientStatus.TIMEOUT);
                rec.setVerdict("查端超时，等待管理员复核");
                pendingCheck.remove(rec.getTargetUuid());
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /* ---------------- payload 注册与回调 ---------------- */

    /** 注册自定义 payload 收发 */
    private void registerPayload() {
        // 服务端可发：请求 ModList（服务端→客户端）
        PayloadTypeRegistry.serverboundPlay().register(FabricModListPayload.Response.ID, FabricModListPayload.Response.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FabricModListPayload.Request.ID, FabricModListPayload.Request.CODEC);

        // 接收客户端返回的 ModList
        ServerPlayNetworking.registerGlobalReceiver(FabricModListPayload.Response.ID,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer target = context.player();
                    onModListReceived(payload, target);
                }));
    }

    /** 收到客户端 ModList：提取 modId 与黑名单/白名单比对 */
    private void onModListReceived(FabricModListPayload.Response payload, ServerPlayer target) {
        ReportRecord rec = pendingCheck.remove(target.getUUID());
        if (rec == null) return;

        if (payload == null || payload.modIds() == null || payload.modIds().isEmpty()) {
            rec.setCheckStatus(CheckClientStatus.INVALID_RESPONSE);
            rec.setVerdict("客户端返回空 ModList 或无效 payload，等待管理员复核");
            return;
        }

        rec.setCheckStatus(CheckClientStatus.SUCCESS);
        rec.getReportedModIds().addAll(payload.modIds());

        List<String> blacklist = configManager.get().getReport().getModBlacklist();
        List<String> allowlist = configManager.get().getReport().getModAllowlist();

        for (String modId : payload.modIds()) {
            String mod = modId == null ? "" : modId.trim();
            if (mod.isEmpty()) continue;

            for (String banned : blacklist) {
                if (banned.equalsIgnoreCase(mod)) {
                    rec.getMatchedMods().add(mod);
                }
            }

            if (!allowlist.isEmpty() && !allowlist.stream().anyMatch(v -> v.equalsIgnoreCase(mod))) {
                rec.getReportedModIds().remove(mod);
            }
        }

        if (!rec.getMatchedMods().isEmpty()) {
            rec.setCheckStatus(CheckClientStatus.BLACKLIST_MATCH);
            rec.setVerdict("命中违规模组: " + rec.getMatchedMods());
            boolean banned = banManager.applyAutomaticBan(target, "report-mod",
                    mod.getSnapshotBuilder().capture(target, "report-mod",
                            "强制查端命中模组: " + rec.getMatchedMods()));
            rec.setVerdict(rec.getVerdict() + (banned ? "（已封禁）" : "（封禁失败）"));
            return;
        }

        if (configManager.get().getReport().isStrictClientCheck() && (
                payload.modIds().stream().noneMatch(m -> m != null && !m.isBlank())
                        || target.level().getServer() == null)) {
            rec.setCheckStatus(CheckClientStatus.INVALID_RESPONSE);
            rec.setVerdict("严格查端模式要求返回有效 ModList，当前返回无效，归档复核");
            return;
        }

        rec.setVerdict("未检出违规模组，归档等待管理员复核");
    }

    /* ---------------- 查询 ---------------- */

    public List<ReportRecord> allReports() {
        return reports;
    }

    public Map<UUID, Long> getReporterCooldownUntil() { return reporterCooldownUntil; }
}
