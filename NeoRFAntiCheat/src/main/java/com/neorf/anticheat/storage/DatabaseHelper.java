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
package com.neorf.anticheat.storage;

import com.neorf.anticheat.punishment.BanManager;
import com.neorf.anticheat.punishment.BanRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DatabaseHelper —— 存储数据辅助
 * <p>
 * 规划用于 H2 等 SQL 存储实现的数据访问辅助（建表 DDL、行↔实体映射）。
 * 当前 JSON 实现暂未使用，保留作扩展骨架，方便后续把 bans 迁移到数据库。
 *
 * <p>若启用 H2（通过切换 StorageProvider 实现），可在此定义：
 * <pre>
 *   CREATE TABLE IF NOT EXISTS bans (
 *     uuid        VARCHAR(36) PRIMARY KEY,
 *     valid_count INT         NOT NULL DEFAULT 0
 *   );
 *   CREATE TABLE IF NOT EXISTS ban_records (
 *     id          BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     uuid        VARCHAR(36) NOT NULL,
 *     check_name  VARCHAR(64),
 *     status      VARCHAR(32),
 *     ban_start   BIGINT,
 *     ban_end     BIGINT,
 *     evidence    TEXT,
 *     created_at  BIGINT
 *   );
 * </pre>
 */
public class DatabaseHelper {

    private DatabaseHelper() {}

    /** 把 UUID 序列化为字符串键（JSON 存储使用） */
    public static String keyOf(UUID uuid) {
        return uuid.toString();
    }

    /** 从字符串键解析 UUID（非法则返回 null） */
    public static UUID uuidOf(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 深拷贝一份玩家状态（防止外部修改破坏内存缓存一致性） */
    public static BanManager.PlayerBanState copyState(BanManager.PlayerBanState src) {
        BanManager.PlayerBanState copy = new BanManager.PlayerBanState();
        copy.validBanCount = src.validBanCount;
        copy.records = new ArrayList<>(src.records);
        return copy;
    }
}
