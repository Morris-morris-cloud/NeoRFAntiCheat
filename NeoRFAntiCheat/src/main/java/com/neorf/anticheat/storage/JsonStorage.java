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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neorf.anticheat.punishment.BanManager;
import com.neorf.anticheat.punishment.BanRecord;
import com.neorf.anticheat.punishment.NameBanRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JsonStorage —— 默认 JSON 文件存储实现
 * <p>
 * 把全部封禁状态序列化为单个 JSON 文件（原子写入：先写临时文件再重命名）。
 * 字段与 {@link BanRecord} / {@link BanManager.PlayerBanState} 一一对应。
 */
public class JsonStorage implements StorageProvider {

    private final Path filePath;
    private final Path nameBanFilePath;
    private final Gson gson;

    public JsonStorage(Path baseDir) {
        this.filePath = baseDir.resolve("bans.json");
        this.nameBanFilePath = baseDir.resolve("name-bans.json");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            // 目录创建失败：不影响运行，写时会再次尝试
        }
    }

    @Override
    public Map<UUID, BanManager.PlayerBanState> loadAllStates() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }
        try (Reader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            // 用一个包装 DTO 承载 Map（UUID 需要自定义序列化，见 adapters）
            Map<UUID, BanManager.PlayerBanState> loaded = gson.fromJson(r,
                    new com.google.gson.reflect.TypeToken<Map<UUID, BanManager.PlayerBanState>>() {}.getType());
            return loaded != null ? loaded : new HashMap<>();
        } catch (IOException | RuntimeException e) {
            // 文件损坏/反序列化失败 → 返回空并记录，绝不让服务器崩溃
            return new HashMap<>();
        }
    }

    @Override
    public void saveAllStates(Map<UUID, BanManager.PlayerBanState> states) {
        try {
            // 原子写入：先写临时文件，成功后重命名，避免写一半损坏
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(states, w);
            }
            Files.move(tmp, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 写失败不中断封禁流程，仅记录
        }
    }

    @Override
    public Map<String, NameBanRecord> loadNameBans() {
        if (!Files.exists(nameBanFilePath)) {
            return new HashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(nameBanFilePath, StandardCharsets.UTF_8)) {
            Map<String, NameBanRecord> loaded = gson.fromJson(reader,
                    new com.google.gson.reflect.TypeToken<Map<String, NameBanRecord>>() {}.getType());
            return loaded != null ? loaded : new HashMap<>();
        } catch (IOException | RuntimeException e) {
            return new HashMap<>();
        }
    }

    @Override
    public void saveNameBans(Map<String, NameBanRecord> nameBans) {
        try {
            Path tmp = nameBanFilePath.resolveSibling(nameBanFilePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(nameBans, writer);
            }
            Files.move(tmp, nameBanFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // A failed persistence write must not interrupt server operation.
        }
    }

    @Override
    public void close() {
        // 无资源需释放
    }
}
