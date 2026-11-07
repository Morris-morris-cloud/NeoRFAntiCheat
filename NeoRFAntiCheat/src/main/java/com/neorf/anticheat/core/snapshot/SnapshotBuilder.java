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
package com.neorf.anticheat.core.snapshot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * SnapshotBuilder —— 证据快照构造器
 * <p>
 * 每次检测违规触发封禁/标记时，都会把"当下现场"固化为快照存入封禁记录，
 * 供管理员复核。快照包含：坐标、tick 时间、ping、动作数据、所属检测。
 *
 * <p>快照是只读的现场复制，不引用玩家对象，避免玩家离线后数据失效。
 */
public class SnapshotBuilder {

    /**
     * 从玩家现场构造快照。
     *
     * @param player    违规玩家
     * @param checkName 触发的检测名（如 "fly"）
     * @param detail    人类可读的细节描述
     * @return 证据快照
     */
    public Snapshot capture(ServerPlayer player, String checkName, String detail) {
        Snapshot s = new Snapshot();
        s.checkName = checkName;
        s.uuid = player.getUUID().toString();
        s.name = player.getGameProfile().name();

        Vec3 pos = player.position();
        s.x = pos.x;
        s.y = pos.y;
        s.z = pos.z;
        s.dimension = player.level().dimension().identifier().toString();
        s.onGround = player.onGround();
        s.velocityY = player.getDeltaMovement().y;

        s.tickTime = player.level().getServer().getTickCount();
        s.ping = -1;
        s.yaw = player.getYRot();
        s.pitch = player.getXRot();
        s.detail = detail;

        return s;
    }

    /**
     * 证据快照实体。
     * 字段与封禁记录 JSON 存储对应，保证可序列化。
     */
    public static class Snapshot {
        public String checkName;
        public String uuid;
        public String name;
        public double x, y, z;
        public String dimension;
        public boolean onGround;
        public double velocityY;
        /** 服务端 tick 计数 */
        public long tickTime;
        /** 玩家 ping（ms） */
        public int ping;
        public float yaw, pitch;
        public String detail;

        /** 便于日志输出 */
        @Override
        public String toString() {
            return "[" + checkName + "] " + name + " @ (" + fmt(x) + "," + fmt(y) + "," + fmt(z)
                    + ") in " + dimension + " tick=" + tickTime + " ping=" + ping + "ms | " + detail;
        }

        private static String fmt(double v) {
            return String.format("%.2f", v);
        }
    }
}
