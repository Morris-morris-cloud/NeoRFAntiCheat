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

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.UuidArgument.getUuid;
import static net.minecraft.commands.arguments.UuidArgument.uuid;

/**
 * ClearCountSubCmd —— /NeoRF clearcount <uuid>
 * <p>
 * 管理员手动清零某玩家的有效封禁计数（validBanCount）。
 * 兜底运维指令：用于纠正计数异常，不影响既有记录。
 */
public class ClearCountSubCmd {

    private ClearCountSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("clearcount")
                .then(argument("uuid", uuid())
                        .executes(ctx -> clear(mod, ctx.getSource(), getUuid(ctx, "uuid"))));
    }

    private static int clear(NeoRFAntiCheat mod, CommandSourceStack src, UUID uuid) {
        BanManager bm = mod.getBanManager();
        String result = bm.clearCount(uuid);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), true);
        return 1;
    }
}
