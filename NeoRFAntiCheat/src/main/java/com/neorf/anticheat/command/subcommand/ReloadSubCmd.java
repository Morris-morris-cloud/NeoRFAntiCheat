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
package com.neorf.anticheat.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.neorf.anticheat.NeoRFAntiCheat;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.literal;

/**
 * ReloadSubCmd —— /NeoRF reload
 * <p>
 * 重载配置：违规模组黑名单、阈值、超时、冷却。
 * 重载成功后同步刷新豁免 UUID 缓存。
 */
public class ReloadSubCmd {

    private ReloadSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("reload")
                .executes(ctx -> reload(mod, ctx.getSource()));
    }

    private static int reload(NeoRFAntiCheat mod, CommandSourceStack src) {
        boolean ok = mod.getConfigManager().reload();
        if (ok) {
            // 配置变化后刷新豁免缓存
            mod.getExemptionManager().refresh();
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    "NeoRFAntiCheat 配置已重载"), true);
            return 1;
        }
        src.sendFailure(net.minecraft.network.chat.Component.literal("配置重载失败，请检查 config/neorf-ac-config.json 语法"));
        return 0;
    }
}
