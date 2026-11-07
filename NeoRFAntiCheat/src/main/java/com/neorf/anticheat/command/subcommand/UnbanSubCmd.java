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
import com.neorf.anticheat.punishment.BanManager;
import net.minecraft.commands.CommandSourceStack;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;

/**
 * UnbanSubCmd —— /NeoRF unban <player-name|uuid> [falsepositive]
 * <p>
 * 解除封禁；带 {@code falsepositive} 参数时标记为误封：
 * <ul>
 *   <li>falsepositive=true → 标记 FALSE_POSITIVE，不修改 validBanCount（不叠加次数）；</li>
 *   <li>默认普通解除 → 是否计入计数由配置 {@code manualUnbanCounts} 决定。</li>
 * </ul>
 */
public class UnbanSubCmd {

    private UnbanSubCmd() {}

    /**
    * 兼容旧命令：/NeoRF unban <player-name|uuid> [falsepositive]
    * 新命令统一为 /NeoRF unbanf <player-name|uuid>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("unban")
                .then(argument("target", string())
                    .executes(ctx -> unban(mod, ctx.getSource(), getString(ctx, "target"), false))
                        .then(argument("falsepositive", bool())
                                .executes(ctx -> unban(mod, ctx.getSource(),
                            getString(ctx, "target"),
                                        getBool(ctx, "falsepositive")))));
    }

            private static int unban(NeoRFAntiCheat mod, CommandSourceStack src, String target, boolean fp) {
        BanManager bm = mod.getBanManager();
            String result = bm.unban(target, fp);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), true);
        return 1;
    }
}
