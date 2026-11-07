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

/**
 * UnbanFastSubCmd —— /NeoRF unbanf <player-name|uuid>
 *
 * 这是“误封快速解除”的新入口，语义等同于旧命令中的
 * /NeoRF unban <player-name|uuid> true 但更短、更直观。
 */
public class UnbanFastSubCmd {

    private UnbanFastSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("unbanf")
                .then(argument("target", string())
                    .executes(ctx -> unbanf(mod, ctx.getSource(), getString(ctx, "target"))));
    }

            private static int unbanf(NeoRFAntiCheat mod, CommandSourceStack src, String target) {
        BanManager bm = mod.getBanManager();
            String result = bm.unban(target, true);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), true);
        return 1;
    }
}
