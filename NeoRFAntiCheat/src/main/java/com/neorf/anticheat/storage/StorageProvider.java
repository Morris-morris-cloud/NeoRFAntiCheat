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
import com.neorf.anticheat.punishment.NameBanRecord;

import java.util.Map;
import java.util.UUID;

/**
 * StorageProvider —— 存储抽象层
 * <p>
 * 定义持久化契约，方便在 JSON / H2 等实现间切换。
 * 当前默认实现为 {@link JsonStorage}（单文件 JSON）。
 *
 * <p>存储内容：以 UUID 为键的玩家封禁状态
 * （含 validBanCount 与全部 BanRecord 历史）。
 */
public interface StorageProvider {

    /**
     * 初始化存储（建目录、建表等）。
     * 在服务器启动时调用一次。
     */
    void init();

    /**
     * 加载全部封禁状态。
     *
     * @return UUID → 玩家封禁状态；文件不存在或损坏时返回空 Map（不得抛异常）
     */
    Map<UUID, BanManager.PlayerBanState> loadAllStates();

    /**
     * 保存全部封禁状态（全量写回）。
     */
    void saveAllStates(Map<UUID, BanManager.PlayerBanState> states);

    /** Load active name-based bans, keyed by lower-case player name. */
    Map<String, NameBanRecord> loadNameBans();

    /** Persist active name-based bans, keyed by lower-case player name. */
    void saveNameBans(Map<String, NameBanRecord> nameBans);

    /** 关闭存储（释放资源） */
    void close();
}
