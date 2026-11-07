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
package com.neorf.anticheat.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * MathUtil —— 距离、速度、角度计算工具
 */
public final class MathUtil {

    private MathUtil() {}

    /** 两点水平距离（忽略 y） */
    public static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 两点三维距离 */
    public static double distance(Vec3 a, Vec3 b) {
        return a.distanceTo(b);
    }

    /** 点到 AABB 碰撞箱的最近距离 */
    public static double distanceToBox(Vec3 point, AABB box) {
        // 先夹取到盒内最近点
        double cx = clamp(point.x, box.minX, box.maxX);
        double cy = clamp(point.y, box.minY, box.maxY);
        double cz = clamp(point.z, box.minZ, box.maxZ);
        double dx = point.x - cx, dy = point.y - cy, dz = point.z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 角度差（考虑 -180/180 环绕，返回 [0,180]） */
    public static float angleDelta(float a, float b) {
        float d = Math.abs(a - b) % 360;
        if (d > 180) d = 360 - d;
        return d;
    }

    /** 计算速度：位移(方块) / 时间(毫秒) → 方块/秒 */
    public static double speedPerSecond(double distance, long dtMs) {
        if (dtMs <= 0) return 0;
        return distance / (dtMs / 1000.0);
    }

    /** 两点间欧氏速度（方块/秒） */
    public static double speedBetween(Vec3 a, Vec3 b, long dtMs) {
        return speedPerSecond(a.distanceTo(b), dtMs);
    }

    /** 归一化点积（两单位向量的相关性，[−1,1]，1 表示同向） */
    public static double correlation(Vec3 a, Vec3 b) {
        return a.normalize().dot(b.normalize());
    }
}
