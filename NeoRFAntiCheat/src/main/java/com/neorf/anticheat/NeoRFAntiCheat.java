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

package com.neorf.anticheat;

import com.neorf.anticheat.command.NeoRFRootCommand;
import com.neorf.anticheat.command.ReportCommand;
import com.neorf.anticheat.config.ConfigManager;
import com.neorf.anticheat.core.exemption.ExemptionManager;
import com.neorf.anticheat.core.snapshot.SnapshotBuilder;
import com.neorf.anticheat.core.threshold.TpsAdaptiveManager;
import com.neorf.anticheat.core.tick.PlayerTickDataManager;
import com.neorf.anticheat.punishment.BanManager;
import com.neorf.anticheat.report.ReportManager;
import com.neorf.anticheat.storage.JsonStorage;
import com.neorf.anticheat.storage.StorageProvider;
import com.neorf.anticheat.check.CheckRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * NeoRFAntiCheat —— 模组主入口（Fabric ModInitializer）
 * <p>
 * 职责：
 * <ol>
 *   <li>初始化各管理层（配置、存储、封禁、举报、豁免、TPS、tick 数据）；</li>
 *   <li>注册服务端生命周期与网络连接事件；</li>
 *   <li>注册 /NeoRF 根指令与 /report 独立指令；</li>
 *   <li>通过 {@link CheckRegistry} 装配全部检测模块。</li>
 * </ol>
 *
 * <p>该模组为 <b>纯服务端</b>（fabric.mod.json 中 environment = "server"），
 * 所有检测基于数据包/事件监听，不依赖客户端上报。
 */
public class NeoRFAntiCheat implements ModInitializer {

    public static final String MOD_ID = "neorf-anticheat";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeoRF AntiCheat");

    // ---------- 单例（简单起见用静态持有；项目内通过它访问各管理器） ----------
    private static NeoRFAntiCheat INSTANCE;

    private ConfigManager configManager;
    private StorageProvider storage;
    private BanManager banManager;
    private ReportManager reportManager;
    private ExemptionManager exemptionManager;
    private TpsAdaptiveManager tpsAdaptiveManager;
    private PlayerTickDataManager tickDataManager;
    private SnapshotBuilder snapshotBuilder;
    private CheckRegistry checkRegistry;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        LOGGER.info("NeoRFAntiCheat 正在初始化...");

        // 1. 配置层
        this.configManager = new ConfigManager(Path.of("config"));
        LOGGER.info("配置加载完成");

        // 2. 存储层（默认 JSON；后续可替换为 H2）
        this.storage = new JsonStorage(Path.of("config/neorf-anticheat-data"));
        this.storage.init();

        // 3. 核心管理层
        this.banManager = new BanManager(this.storage, this.configManager);
        this.exemptionManager = new ExemptionManager(this.configManager);
        this.tpsAdaptiveManager = new TpsAdaptiveManager(this.configManager);
        this.tickDataManager = new PlayerTickDataManager();
        this.snapshotBuilder = new SnapshotBuilder();

        // 4. 举报 + 强制查端
        this.reportManager = new ReportManager(this.configManager, this.banManager);

        // 5. 装配检测模块
        this.checkRegistry = new CheckRegistry(this);

        // 6. 注册事件
        registerEvents();

        LOGGER.info("NeoRFAntiCheat 初始化完成，已装配 {} 个检测模块", checkRegistry.size());
    }

    /** 注册服务端生命周期与连接事件 */
    private void registerEvents() {
        // 服务器启动完成：加载既有封禁数据、启动 tps 采样、注册指令
        // （指令依赖 server 就绪，故在此注册而非 onInitialize)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            this.banManager.onServerStart(server);
            this.reportManager.onServerStart(server);
            this.tpsAdaptiveManager.onServerStart(server);
            registerCommands();
            LOGGER.info("NeoRFAntiCheat 已挂载到服务器 {}", server.getMotd());
        });

        // 服务器关闭：持久化、关闭资源
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.banManager.onServerStop();
            this.storage.close();
        });

        // 玩家加入：初始化 tick 数据、恢复封禁检查
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            this.tickDataManager.onJoin(player);
            if (this.banManager.isNameBanned(player.getGameProfile().name())) {
                player.connection.disconnect(this.banManager.nameBanMessage(player.getGameProfile().name()));
                return;
            }
            // 若该玩家存在生效封禁，立即断开
            if (this.banManager.isActiveBanned(player.getUUID())) {
                player.connection.disconnect(this.banManager.activeBanMessage(player.getUUID()));
            }
        });

        // 玩家离开：清理 tick 数据
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.getPlayer().getUUID();
            this.tickDataManager.onQuit(uuid);
            this.checkRegistry.onPlayerQuit(uuid);
        });

        // 服务端 tick：tps 采样 + 检测循环
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            this.tpsAdaptiveManager.onServerTick(server);
            this.tickDataManager.onServerTick(server);
            this.checkRegistry.onServerTick(server);
        });
    }

    /** 注册 /NeoRF 根指令与 /report 指令 */
    private void registerCommands() {
        // /NeoRF ... 及其子指令
        NeoRFRootCommand.register(this);
        // /report <target> [remark] 独立指令
        ReportCommand.register(this);
    }

    /* ---------------- 访问器（供各检测模块与指令使用） ---------------- */

    public static NeoRFAntiCheat getInstance() { return INSTANCE; }

    public ConfigManager getConfigManager() { return configManager; }
    public StorageProvider getStorage() { return storage; }
    public BanManager getBanManager() { return banManager; }
    public ReportManager getReportManager() { return reportManager; }
    public ExemptionManager getExemptionManager() { return exemptionManager; }
    public TpsAdaptiveManager getTpsAdaptiveManager() { return tpsAdaptiveManager; }
    public PlayerTickDataManager getTickDataManager() { return tickDataManager; }
    public SnapshotBuilder getSnapshotBuilder() { return snapshotBuilder; }
    public CheckRegistry getCheckRegistry() { return checkRegistry; }
    public MinecraftServer getServer() { return server; }
}
