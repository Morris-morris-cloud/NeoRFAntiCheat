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
import com.neorf.anticheat.punishment.BanRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.arguments.EntityArgument.player;
import static net.minecraft.commands.arguments.UuidArgument.getUuid;
import static net.minecraft.commands.arguments.UuidArgument.uuid;

/**
 * BanInfoSubCmd —— /NeoRF baninfo <player/uuid>
 * <p>
 * 查看封禁历史、有效计数、每条记录状态。
 */
public class BanInfoSubCmd {

    private BanInfoSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("baninfo")
                // 按玩家
                .then(argument("player", player())
                        .executes(ctx -> show(mod, ctx.getSource(),
                                getPlayer(ctx, "player").getUUID())))
                // 按 uuid
                .then(argument("uuid", uuid())
                        .executes(ctx -> show(mod, ctx.getSource(), getUuid(ctx, "uuid"))));
    }

    private static int show(NeoRFAntiCheat mod, CommandSourceStack src, UUID uuid) {
        BanManager bm = mod.getBanManager();
        BanManager.PlayerBanState state = bm.getState(uuid);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "===== 封禁信息 " + uuid + " ====="), false);
        if (state == null || state.records.isEmpty()) {
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("该玩家无封禁记录"), false);
            return 0;
        }
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "有效封禁计数(validBanCount): " + state.validBanCount), false);
        for (BanRecord r : state.records) {
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    "  # " + r.getCheckName()
                            + " | 状态=" + r.getStatus()
                            + " | " + (r.isPermanent() ? "永久" : "时长=" + r.getDurationMs() + "ms")
                            + " | 自动=" + r.isAutomatic()), false);
        }
        return 1;
    }
}
