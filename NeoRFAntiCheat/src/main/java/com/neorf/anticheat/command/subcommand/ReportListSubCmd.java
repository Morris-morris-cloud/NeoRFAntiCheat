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
import com.neorf.anticheat.report.ReportManager;
import com.neorf.anticheat.report.ReportRecord;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.literal;

/**
 * ReportListSubCmd —— /NeoRF reportlist
 * <p>
 * 列出全部举报单、查端状态、核验结果。
 */
public class ReportListSubCmd {

    private ReportListSubCmd() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(NeoRFAntiCheat mod) {
        return literal("reportlist")
                .executes(ctx -> list(mod, ctx.getSource()));
    }

    private static int list(NeoRFAntiCheat mod, CommandSourceStack src) {
        ReportManager rm = mod.getReportManager();
        var reports = rm.allReports();
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "===== 举报单总数: " + reports.size() + " ====="), false);
        for (ReportRecord r : reports) {
            src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    "  [" + r.getId() + "] " + r.getReporterName() + " → " + r.getTargetName()
                            + " | 查端=" + r.getCheckStatus()
                            + " | 命中=" + (r.getMatchedMods().isEmpty() ? "-" : r.getMatchedMods())
                            + " | " + (r.getVerdict() == null ? "" : r.getVerdict())), false);
        }
        return 1;
    }
}
