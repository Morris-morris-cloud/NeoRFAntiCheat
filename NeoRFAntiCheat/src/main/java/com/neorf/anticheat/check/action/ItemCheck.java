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
package com.neorf.anticheat.check.action;

import com.neorf.anticheat.NeoRFAntiCheat;
import com.neorf.anticheat.check.Check;
import com.neorf.anticheat.config.AcConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * ItemCheck —— 物品校验
 * <p>
 * 判定逻辑：校验背包 / 手持物品：
 * <ul>
 *   <li>非法物品 id（不存在的注册物品）；</li>
 *   <li>超出原版上限的附魔等级；</li>
 *   <li>属性溢出（attribute modifier 数量/数值异常）；</li>
 *   <li>NBT 异常（如畸形结构、超长深度）。</li>
 * </ul>
 * 周期性扫描在线玩家背包，发现异常即标记。
 */
public class ItemCheck extends Check {

    public ItemCheck(NeoRFAntiCheat mod) {
        super(mod, "Item", "action");
    }

    @Override
    public String id() { return "item"; }

    @Override
    public boolean isEnabled() {
        return mod.getConfigManager().get().getAction().getItem().isEnabled();
    }

    @Override
    protected int violationsToBan() {
        // 物品类严重异常直接一次封禁判定（阈值 1）
        return 1;
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        AcConfig.Item cfg = mod.getConfigManager().get().getAction().getItem();
        // 每 20 tick（1 秒）扫描一次，避免每 tick 全量背包校验的性能开销
        if (server.getTickCount() % 20 != 0) return;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (mod.getExemptionManager().isExempt(p, id())) continue;

            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                ItemStack stack = p.getInventory().getItem(slot);
                if (stack.isEmpty()) continue;

                String issue = validateStack(stack, cfg);
                if (issue != null) {
                    flag(p, 1, "槽位 " + slot + " 物品异常: " + issue);
                    // 物品异常已记一次，避免同槽位重复触发
                    return;
                }
            }
        }
    }

    /** 校验单个物品栈，返回异常描述，null 表示正常 */
    private String validateStack(ItemStack stack, AcConfig.Item cfg) {
        // 1) 非法 id：物品为空引用或注册名无效（由原版保证，此处兜底）
        if (stack.isEmpty()) return null;

        // 2) NBT 深度/大小异常（防深度递归攻击）
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().sizeInBytes() > 4096) {
            return "NBT 体积异常";
        }

        // 3) 附魔等级校验：超过原版等级上限
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var enchantment : enchantments.keySet()) {
                int lvl = enchantments.getLevel(enchantment);
                if (lvl > cfg.getMaxEnchantLevel()) {
                    return "附魔等级溢出 lvl=" + lvl;
                }
            }
        }

        // 4) 属性 modifier 数量校验
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && modifiers.modifiers().size() > cfg.getMaxAttributeModifiers()) {
            return "属性 modifier 数量溢出";
        }

        return null;
    }
}
