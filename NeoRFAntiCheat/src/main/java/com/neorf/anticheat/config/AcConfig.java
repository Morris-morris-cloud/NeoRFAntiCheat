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
package com.neorf.anticheat.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AcConfig —— 配置 POJO
 * <p>
 * 与 resources/neorf-ac-config.json 一一对应，使用 Gson 反序列化。
 * 所有字段提供默认值，保证配置缺失或损坏时模组仍可启动。
 * 修改配置后通过 {@link ConfigManager#reload()} 热重载（/NeoRF reload）。
 *
 * <p>结构分层：
 * <pre>
 * general        总开关、检测频率、TPS 自适应
 * movement       Fly / Speed / NoClip / Teleport
 * combat         KillAura / Reach / Aim
 * action         Nuker / AutoInteract / Item
 * packet         PacketFlood
 * report         举报冷却、查端超时
 * punishment     首封时长、普通解除是否计数
 * whitelist      UUID / 权限 / 区域白名单
 * checks         违规模组黑名单
 * </pre>
 */
public class AcConfig {

    // ---------- 顶层 ----------
    private General general = new General();
    private Movement movement = new Movement();
    private Combat combat = new Combat();
    private Action action = new Action();
    private Packet packet = new Packet();
    private Report report = new Report();
    private Punishment punishment = new Punishment();
    private Whitelist whitelist = new Whitelist();
    private Checks checks = new Checks();

    // ---------- getter / setter ----------
    public General getGeneral() { return general; }
    public Movement getMovement() { return movement; }
    public Combat getCombat() { return combat; }
    public Action getAction() { return action; }
    public Packet getPacket() { return packet; }
    public Report getReport() { return report; }
    public Punishment getPunishment() { return punishment; }
    public Whitelist getWhitelist() { return whitelist; }
    public Checks getChecks() { return checks; }

    /* ================================================================
     * 通用配置
     * ================================================================ */
    public static class General {
        /** 反作弊总开关 */
        private boolean enabled = true;
        /** 是否允许自定义规则覆盖默认阈值 */
        private boolean customRulesEnabled = true;
        /** 每 N 个服务端 tick 跑一次检测 */
        private int checkIntervalTicks = 1;
        /** TPS 自适应：MSPT 超过该值(ms)放宽阈值 */
        private long tpsAdaptiveMsptThreshold = 50;
        /** TPS 低于该值跳过时序敏感检测（瞬移/速度） */
        private double minTpsForTimingChecks = 15.0;
        /** 允许启用/禁用的检测名白名单，支持按需关闭模块 */
        private List<String> allowedChecks = new ArrayList<>(Arrays.asList(
                "fly", "speed", "noclip", "teleport",
                "killaura", "reach", "aim",
                "nuker", "autoInteract", "item",
                "packetFlood"));

        public boolean isEnabled() { return enabled; }
        public boolean isCustomRulesEnabled() { return customRulesEnabled; }
        public int getCheckIntervalTicks() { return checkIntervalTicks; }
        public long getTpsAdaptiveMsptThreshold() { return tpsAdaptiveMsptThreshold; }
        public double getMinTpsForTimingChecks() { return minTpsForTimingChecks; }
        public List<String> getAllowedChecks() { return allowedChecks; }
    }

    /* ================================================================
     * 移动检测
     * ================================================================ */
    public static class Movement {
        private Fly fly = new Fly();
        private Speed speed = new Speed();
        private NoClip noclip = new NoClip();
        private Teleport teleport = new Teleport();
        public Fly getFly() { return fly; }
        public Speed getSpeed() { return speed; }
        public NoClip getNoclip() { return noclip; }
        public Teleport getTeleport() { return teleport; }
    }

    public static class Fly {
        private boolean enabled = true;
        /** 持续无合法支持的空中时间(ms)达到该值开始累计违规 */
        private long minSuspiciousAirMs = 3000;
        /** 达到该违规点数触发封禁判定 */
        private int violationsToBan = 20;
        public boolean isEnabled() { return enabled; }
        public long getMinSuspiciousAirMs() { return minSuspiciousAirMs; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class Speed {
        private boolean enabled = true;
        /** 水平速度超出理论最大值的比例 */
        private double overSpeedRatio = 0.30;
        private int violationsToBan = 20;
        public boolean isEnabled() { return enabled; }
        public double getOverSpeedRatio() { return overSpeedRatio; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class NoClip {
        private boolean enabled = true;
        /** 碰撞箱与实心方块相交持续 ms */
        private long minClipMs = 500;
        private int violationsToBan = 15;
        public boolean isEnabled() { return enabled; }
        public long getMinClipMs() { return minClipMs; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class Teleport {
        private boolean enabled = true;
        /** 速度超过原版极限的倍数判为瞬移 */
        private double limitMultiplier = 2.0;
        private int violationsToBan = 10;
        public boolean isEnabled() { return enabled; }
        public double getLimitMultiplier() { return limitMultiplier; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    /* ================================================================
     * 战斗检测
     * ================================================================ */
    public static class Combat {
        private KillAura killaura = new KillAura();
        private Reach reach = new Reach();
        private Aim aim = new Aim();
        public KillAura getKillaura() { return killaura; }
        public Reach getReach() { return reach; }
        public Aim getAim() { return aim; }
    }

    public static class KillAura {
        private boolean enabled = true;
        /** Rolling-window attack packet limit */
        private int maxCps = 20;
        private long windowMs = 1000;
        /** Sustained CPS violations needed to trigger a ban */
        private int cpsViolationsToBan = 5;
        public boolean isEnabled() { return enabled; }
        public int getMaxCps() { return maxCps; }
        public long getWindowMs() { return windowMs; }
        public int getCpsViolationsToBan() { return cpsViolationsToBan; }
    }

    public static class Reach {
        private boolean enabled = true;
        /** 近战攻击距离上限 */
        private double maxReach = 3.2;
        private int violationsToBan = 15;
        public boolean isEnabled() { return enabled; }
        public double getMaxReach() { return maxReach; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class Aim {
        private boolean enabled = true;
        /** 单 tick 视角突变角度上限 */
        private double maxYawDeltaPerTick = 45.0;
        /** 视角平滑锁向实体的相关性阈值 */
        private double lockCorrelationThreshold = 0.9;
        private int violationsToBan = 20;
        public boolean isEnabled() { return enabled; }
        public double getMaxYawDeltaPerTick() { return maxYawDeltaPerTick; }
        public double getLockCorrelationThreshold() { return lockCorrelationThreshold; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    /* ================================================================
     * 交互检测
     * ================================================================ */
    public static class Action {
        private Nuker nuker = new Nuker();
        private AutoInteract autoInteract = new AutoInteract();
        private Item item = new Item();
        public Nuker getNuker() { return nuker; }
        public AutoInteract getAutoInteract() { return autoInteract; }
        public Item getItem() { return item; }
    }

    public static class Nuker {
        private boolean enabled = true;
        /** 窗口内最大合法破坏方块数 */
        private int maxBlocksPerWindow = 12;
        private long windowMs = 1000;
        private int violationsToBan = 15;
        public boolean isEnabled() { return enabled; }
        public int getMaxBlocksPerWindow() { return maxBlocksPerWindow; }
        public long getWindowMs() { return windowMs; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class AutoInteract {
        private boolean enabled = true;
        /** 放置/右键间隔(ms)低于该值判为自动交互 */
        private long minIntervalMs = 120;
        private int violationsToBan = 20;
        public boolean isEnabled() { return enabled; }
        public long getMinIntervalMs() { return minIntervalMs; }
        public int getViolationsToBan() { return violationsToBan; }
    }

    public static class Item {
        private boolean enabled = true;
        /** 超出原版上限的附魔等级阈值 */
        private int maxEnchantLevel = 255;
        /** 属性 modifier 数量上限 */
        private int maxAttributeModifiers = 6;
        public boolean isEnabled() { return enabled; }
        public int getMaxEnchantLevel() { return maxEnchantLevel; }
        public int getMaxAttributeModifiers() { return maxAttributeModifiers; }
    }

    /* ================================================================
     * 发包检测
     * ================================================================ */
    public static class Packet {
        private Flood flood = new Flood();
        public Flood getFlood() { return flood; }
    }

    public static class Flood {
        private boolean enabled = true;
        /** 单玩家每秒发包上限 */
        private int maxPacketsPerSecond = 1500;
        /** 连续超限秒数触发标记 */
        private int sustainSeconds = 5;
        public boolean isEnabled() { return enabled; }
        public int getMaxPacketsPerSecond() { return maxPacketsPerSecond; }
        public int getSustainSeconds() { return sustainSeconds; }
    }

    /* ================================================================
     * 举报 / 查端
     * ================================================================ */
    public static class Report {
        /** 举报人全局冷却(ms)，重启清空 */
        private long reporterCooldownMs = 300000;
        /** 强制查端超时(ms) */
        private long clientCheckTimeoutMs = 5000;
        /** 强制查端允许的额外容错时长(ms) */
        private long clientCheckGraceMs = 1500;
        /** 是否启用强制查端 */
        private boolean forceClientCheck = true;
        /** 是否在严格模式下要求客户端必须是 Fabric 并返回有效 ModList */
        private boolean strictClientCheck = true;
        /** 是否允许“未知客户端/非 Fabric”继续进入人工复核 */
        private boolean allowUnknownClient = true;
        /** 允许的 modId 白名单；为空表示放行所有合法 Fabric 模组 */
        private List<String> modAllowlist = new ArrayList<>();
        /** 违规模组黑名单，命中即封禁 */
        private List<String> modBlacklist = new ArrayList<>(Arrays.asList(
                "injector", "advanced_antiautobuild", "wurst"));

        public long getReporterCooldownMs() { return reporterCooldownMs; }
        public long getClientCheckTimeoutMs() { return clientCheckTimeoutMs; }
        public long getClientCheckGraceMs() { return clientCheckGraceMs; }
        public boolean isForceClientCheck() { return forceClientCheck; }
        public boolean isStrictClientCheck() { return strictClientCheck; }
        public boolean isAllowUnknownClient() { return allowUnknownClient; }
        public List<String> getModAllowlist() { return modAllowlist; }
        public List<String> getModBlacklist() { return modBlacklist; }
    }

    /* ================================================================
     * 处罚
     * ================================================================ */
    public static class Punishment {
        /** 首次有效封禁时长(ms)：15天 */
        private long firstBanMs = 1296000000L;
        /** unban 普通解除是否计入 validBanCount */
        private boolean manualUnbanCounts = false;
        /** 是否允许通过 /NeoRF ban 手工定制封禁理由 */
        private boolean allowCustomBanReason = true;
        /** 手工封禁允许的最大时长（分钟），超出则强制为永久 */
        private long maxManualBanMinutes = 10080L;
        /** 允许使用的手工理由白名单 */
        private List<String> allowedBanReasons = new ArrayList<>(Arrays.asList(
                "cheat", "hack", "mod", "manual", "admin", "exploit"));

        public long getFirstBanMs() { return firstBanMs; }
        public boolean isManualUnbanCounts() { return manualUnbanCounts; }
        public boolean isAllowCustomBanReason() { return allowCustomBanReason; }
        public long getMaxManualBanMinutes() { return maxManualBanMinutes; }
        public List<String> getAllowedBanReasons() { return allowedBanReasons; }
    }

    /* ================================================================
     * 白名单
     * ================================================================ */
    public static class Whitelist {
        /** 完全豁免的 UUID 列表 */
        private List<String> exemptUuids = new ArrayList<>();
        /** 豁免权限节点前缀，拥有 neorf.exempt.<check> 即豁免 */
        private String exemptPermissionPrefix = "neorf.exempt";
        /** 区域白名单 */
        private List<Region> regions = new ArrayList<>();

        public List<String> getExemptUuids() { return exemptUuids; }
        public String getExemptPermissionPrefix() { return exemptPermissionPrefix; }
        public List<Region> getRegions() { return regions; }
    }

    /** 区域白名单：矩形区域内关闭指定检测 */
    public static class Region {
        private String name;
        private String world;
        private double minX, minY, minZ, maxX, maxY, maxZ;
        private List<String> disabledChecks = new ArrayList<>();

        public String name() { return name; }
        public String level() { return world; }
        public double getMinX() { return minX; }
        public double getMinY() { return minY; }
        public double getMinZ() { return minZ; }
        public double getMaxX() { return maxX; }
        public double getMaxY() { return maxY; }
        public double getMaxZ() { return maxZ; }
        public List<String> getDisabledChecks() { return disabledChecks; }
    }

    /* ================================================================
     * 违规模组黑名单
     * ================================================================ */
    public static class Checks {
        /** /report 强制查端命中即封禁的 modId（小写） */
        private List<String> modBlacklist = new ArrayList<>();
        public List<String> getModBlacklist() { return modBlacklist; }
    }
}
