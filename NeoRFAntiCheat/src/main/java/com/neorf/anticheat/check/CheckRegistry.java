
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
 */package com.neorf.anticheat.check;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.action.AutoInteractCheck;
import com.neorf.anticheat.check.action.ItemCheck;
import com.neorf.anticheat.check.action.NukerCheck;
import com.neorf.anticheat.check.combat.AimCheck;
import com.neorf.anticheat.check.combat.KillAuraCheck;
import com.neorf.anticheat.check.combat.ReachCheck;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.neorf.anticheat.check.movement.FlyCheck;
import com.neorf.anticheat.check.movement.NoClipCheck;
import com.neorf.anticheat.check.movement.SpeedCheck;
import com.neorf.anticheat.check.movement.TeleportCheck;
import com.neorf.anticheat.check.packet.PacketFloodCheck;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * CheckRegistry —— 检测模块注册器
 * <p>
 * 负责创建并持有全部检测实例，向服务端 tick 分发，以及事件类检测
 * 的订阅入口。新检测只需在此注册即可被统一调度。
 *
 * <p>当前装配（与需求一一对应）：
 * <ul>
 *   <li>movement：Fly / Speed / NoClip / Teleport</li>
 *   <li>combat  ：KillAura / Reach / Aim</li>
 *   <li>action  ：Nuker / AutoInteract / Item</li>
 *   <li>packet  ：PacketFlood</li>
 * </ul>
 */
public class CheckRegistry {

    private final NeoRFAntiCheat mod;
    private final List<Check> checks = new ArrayList<>();
    private KillAuraCheck killAuraCheck;
    private ReachCheck reachCheck;
    private AimCheck aimCheck;
    private PacketFloodCheck packetFloodCheck;

    public CheckRegistry(NeoRFAntiCheat mod) {
        this.mod = mod;
        registerAll();
    }

    /** 注册全部检测（如需增删，改这里即可） */
    private void registerAll() {
        checks.add(new FlyCheck(mod));
        checks.add(new SpeedCheck(mod));
        checks.add(new NoClipCheck(mod));
        checks.add(new TeleportCheck(mod));

        killAuraCheck = new KillAuraCheck(mod);
        reachCheck = new ReachCheck(mod);
        aimCheck = new AimCheck(mod);
        checks.add(killAuraCheck);
        checks.add(reachCheck);
        checks.add(aimCheck);

        checks.add(new NukerCheck(mod));
        checks.add(new AutoInteractCheck(mod));
        checks.add(new ItemCheck(mod));

        packetFloodCheck = new PacketFloodCheck(mod);
        checks.add(packetFloodCheck);
    }

    /** 服务端 tick 分发：把 tick 给所有检测 */
    public void onServerTick(MinecraftServer server) {
        if (!mod.getConfigManager().get().getGeneral().isEnabled()) {
            return; // 总开关关闭
        }
        for (Check c : checks) {
            if (c.isEnabled()) {
                c.onServerTick(server);
            }
        }
    }

    /** 已装配检测数（日志用） */
    public int size() {
        return checks.size();
    }

    public List<Check> all() {
        return checks;
    }

    public void onServerAttack(ServerPlayer player, Entity target) {
        if (!mod.getConfigManager().get().getGeneral().isEnabled()) return;
        killAuraCheck.onServerAttack(player);
        if (target != null) {
            reachCheck.onServerAttack(player, target);
            aimCheck.onServerAttack(player, target);
        }
    }

    public void onInboundPacket(ServerPlayer player) {
        if (mod.getConfigManager().get().getGeneral().isEnabled()) {
            packetFloodCheck.onPacket(player);
        }
    }

    public void onPlayerQuit(java.util.UUID uuid) {
        killAuraCheck.onQuit(uuid);
        aimCheck.onQuit(uuid);
        packetFloodCheck.onQuit(uuid);
    }
}
