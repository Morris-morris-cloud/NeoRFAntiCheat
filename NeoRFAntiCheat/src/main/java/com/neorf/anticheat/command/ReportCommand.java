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
package com.neorf.anticheat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.report.ReportManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.arguments.EntityArgument.player;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * ReportCommand —— /report 独立指令
 * <p>
 * 语法：{@code /report <target> [remark]}
 * <ul>
 *   <li>玩家可用（受全局冷却限制），服务器控制台也可直接查端且不受冷却限制；</li>
 *   <li>解析指令 → 入库举报记录 → 强制查端。</li>
 * </ul>
 */
public class ReportCommand {

    private ReportCommand() {}

    /** 注册 /report 指令 */
    public static void register(NeoRFAntiCheat mod) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                mod.getServer().getCommands().getDispatcher();

        dispatcher.register(literal("report")
                .then(argument("target", player())
                        .executes(ctx -> execute(mod, ctx.getSource(),
                                getPlayer(ctx, "target"), null))
                        .then(argument("remark", greedyString())
                                .executes(ctx -> execute(mod, ctx.getSource(),
                                        getPlayer(ctx, "target"),
                                        getString(ctx, "remark"))))));
    }

    /** 执行举报 */
    private static int execute(NeoRFAntiCheat mod, CommandSourceStack src,
                               ServerPlayer target, String remark) {
        ServerPlayer reporter = src.getEntity() instanceof ServerPlayer player ? player : null;
        if (reporter != null && reporter.getUUID().equals(target.getUUID())) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("不能举报自己"));
            return 0;
        }

        ReportManager rm = mod.getReportManager();
        String result = reporter == null
                ? rm.submitServerReport(target, remark)
                : rm.submitReport(reporter, target, remark);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), false);
        return 1;
    }
}
