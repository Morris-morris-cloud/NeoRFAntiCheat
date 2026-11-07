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
import com.neorf.anticheat.command.subcommand.BanInfoSubCmd;
import com.neorf.anticheat.command.subcommand.BanSubCmd;
import com.neorf.anticheat.command.subcommand.ClearCountSubCmd;
import com.neorf.anticheat.command.subcommand.ReloadSubCmd;
import com.neorf.anticheat.command.subcommand.ReportListSubCmd;
import com.neorf.anticheat.command.subcommand.UnbanFastSubCmd;
import com.neorf.anticheat.command.subcommand.UnbanSubCmd;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

import static net.minecraft.commands.Commands.literal;

/**
 * NeoRFRootCommand —— /NeoRF 根指令
 * <p>
 * 组织 5 个子指令：
 * <ul>
 *   <li>{@code /NeoRF baninfo <player/uuid>} 查看封禁历史与有效计数；</li>
 *   <li>{@code /NeoRF unban <uuid> [falsepositive]} 解除封禁；</li>
 *   <li>{@code /NeoRF clearcount <uuid>} 清零有效封禁计数；</li>
 *   <li>{@code /NeoRF reportlist} 列出全部举报单与查端结果；</li>
 *   <li>{@code /NeoRF reload} 热重载配置。</li>
 * </ul>
 * 根指令默认要求权限等级 2（管理员），子指令可各自覆盖。
 */
public class NeoRFRootCommand {

    private NeoRFRootCommand() {}

    /** 注册 /NeoRF 根指令 */
    public static void register(NeoRFAntiCheat mod) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                mod.getServer().getCommands().getDispatcher();

        dispatcher.register(literal("NeoRF")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(BanInfoSubCmd.build(mod))
                .then(BanSubCmd.build(mod))
                .then(UnbanSubCmd.build(mod))
                .then(UnbanFastSubCmd.build(mod))
                .then(ClearCountSubCmd.build(mod))
                .then(ReportListSubCmd.build(mod))
                .then(ReloadSubCmd.build(mod))
        );
    }
}
