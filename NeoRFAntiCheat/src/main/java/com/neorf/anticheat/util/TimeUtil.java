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

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TimeUtil —— 时间工具
 * <p>
 * 统一时间来源，便于未来切换（如使用服务端 tick 时间还是墙钟时间）。
 */
public final class TimeUtil {

    private TimeUtil() {}

    /** 当前服务端 tick 对应的毫秒时间戳（墙钟） */
    public static long tickMs() {
        return System.currentTimeMillis();
    }

    /** 当前纳秒（高精度计时用） */
    public static long nano() {
        return System.nanoTime();
    }

    /** 格式化时间戳为可读字符串 */
    public static String format(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
    }

    /** 把毫秒时长转成"x天x小时"可读 */
    public static String humanDuration(long ms) {
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins = (totalSec % 3600) / 60;
        if (days > 0) return days + "天" + hours + "小时";
        if (hours > 0) return hours + "小时" + mins + "分";
        return mins + "分";
    }
}
