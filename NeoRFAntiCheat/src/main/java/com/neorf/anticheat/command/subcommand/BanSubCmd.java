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
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * BanSubCmd —— /NeoRF ban <player-name|uuid> [reason]
 *
 * 该命令作为更好的手工封禁入口，适合替代 /ipban 等笼统封禁。
    * 支持按玩家名或 UUID 封禁，并将结果写入 BanManager 的记录流。
 */
public class BanSubCmd {

    private BanSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("ban")
                .then(argument("target", string())
                    .executes(ctx -> ban(mod, ctx.getSource(), getString(ctx, "target"), "manual-ban"))
                        .then(argument("reason", greedyString())
                        .executes(ctx -> ban(mod, ctx.getSource(), getString(ctx, "target"), getString(ctx, "reason")))));
    }

            private static int ban(NeoRFAntiCheat mod, CommandSourceStack src, String target, String reason) {
        BanManager bm = mod.getBanManager();
            String result = bm.ban(target, reason, 10080L * 60_000L, false);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), true);
        return 1;
    }
}
