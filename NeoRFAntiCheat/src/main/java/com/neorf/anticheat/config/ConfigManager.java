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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ConfigManager —— 配置加载、保存与热重载
 * <p>
 * 职责：
 * <ul>
 *   <li>首次启动：把 resources 中的默认模板 {@code neorf-ac-config.json}
 *       复制到 {@code config/NeoRF-ac-config.json}，供管理员编辑；</li>
 *   <li>之后以 config 目录中的文件为准加载；</li>
 *   <li>提供 {@link #reload()}，供 /NeoRF reload 与定时监听调用。</li>
 * </ul>
 *
 * <p>线程安全：配置在服务端主线程读取，reload 在主线程执行，
 * 通过 volatile 引用保证可见性。字段采用不可变 POJO 引用，
 * reload 时整体替换 {@link #current}，不修改旧对象，避免检测线程读到半更新状态。
 */
public class ConfigManager {

    /** 配置文件名（config 目录下） */
    private static final String FILE_NAME = "neorf-ac-config.json";
    /** 打包内默认模板路径 */
    private static final String DEFAULT_TEMPLATE = "/neorf-ac-config.json";

    private final Gson gson;
    private final Path configPath;
    /** 当前生效配置，volatile 保证跨线程可见 */
    private volatile AcConfig current;

    /**
     * 构造并加载配置。
     *
     * @param configDir 服务端 config 目录
     */
    public ConfigManager(Path configDir) {
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.configPath = configDir.resolve(FILE_NAME);
        load();
    }

    /**
     * 加载配置。若 config 目录无文件则写入默认模板。
     */
    public void load() {
        try {
            if (!Files.exists(configPath)) {
                writeDefaultTemplate();
            }
            this.current = readFromDisk();
            if (this.current == null) {
                // 解析失败时兜底为默认配置，避免模组不可用
                this.current = defaultConfig();
            }
        } catch (Exception e) {
            // 任何加载失败都回退默认配置并记录，绝不因配置问题导致服务器崩溃
            this.current = defaultConfig();
            // 实际项目可接入日志框架（如 slf4j）记录 e
        }
    }

    /**
     * 热重载配置（/NeoRF reload 调用）。
     *
     * @return true 表示重载成功且配置有效
     */
    public boolean reload() {
        try {
            AcConfig loaded = readFromDisk();
            if (loaded == null) {
                return false;
            }
            this.current = loaded;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 当前生效配置（只读使用，禁止修改返回对象的字段） */
    public AcConfig get() {
        return current;
    }

    /* ---------------- 内部实现 ---------------- */

    /** 把 resources 默认模板复制到 config 目录 */
    private void writeDefaultTemplate() throws IOException {
        try (var in = ConfigManager.class.getResourceAsStream(DEFAULT_TEMPLATE)) {
            if (in == null) {
                // 模板缺失也写一个默认配置
                Files.writeString(configPath, gson.toJson(defaultConfig()), StandardCharsets.UTF_8);
                return;
            }
            Files.createDirectories(configPath.getParent());
            Files.copy(in, configPath);
        }
    }

    /** 从磁盘读取并反序列化 */
    private AcConfig readFromDisk() throws IOException {
        if (!Files.exists(configPath)) {
            return defaultConfig();
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, AcConfig.class);
        } catch (JsonSyntaxException e) {
            // 语法错误 → 返回 null，由调用方兜底
            return null;
        }
    }

    /** 构造一份全新默认配置 */
    private AcConfig defaultConfig() {
        return new AcConfig();
    }

    /** 把当前配置写回磁盘（调试/管理用） */
    public boolean save() {
        try (Writer w = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            gson.toJson(current, w);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
