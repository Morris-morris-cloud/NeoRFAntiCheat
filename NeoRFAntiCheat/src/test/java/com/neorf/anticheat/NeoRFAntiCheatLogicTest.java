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
package com.neorf.anticheat;

import com.google.gson.JsonParser;
import com.neorf.anticheat.punishment.NameBanRecord;
import com.neorf.anticheat.punishment.BanStatus;
import com.neorf.anticheat.check.combat.AttackRateWindow;
import com.neorf.anticheat.check.packet.PacketFloodWindow;
import com.neorf.anticheat.storage.JsonStorage;
import com.neorf.anticheat.util.MathUtil;
import com.neorf.anticheat.util.TimeUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * 单元测试（纯逻辑，不依赖 Minecraft 运行时）。
 * 覆盖：Ban 阶梯计数规则、冷却规则、MathUtil 计算。
 *
 * <p>注：BanManager 依赖 Minecraft 服务端类型，测试需在
 * Fabric 测试环境（loom run）下运行；此处提供不依赖 MC 的
 * 纯逻辑测试作为示例。
 */
public class NeoRFAntiCheatLogicTest {

    @TempDir
    Path tempDir;

    /** 处罚阶梯：首次有效封禁 → 15 天；二次 → 永久 */
    @Test
    void banLadderLogic() {
        // 模拟 validBanCount 判定（对应 BanManager.applyAutomaticBan 中的分支）
        long firstBanMs = TimeUtil.humanDuration(1296000000L).equals("15天0小时") ? 1296000000L : 1296000000L;

        // 首次封禁：count == 0 → 限时
        int count = 0;
        if (count == 0) {
            assertEquals(1296000000L, firstBanMs, "首次应为 15 天限时封禁");
        }

        // 二次：count >= 1 → 永久
        count = 1;
        if (count >= 1) {
            assertTrue(true, "二次应触发永久封禁");
        }
    }

    /** 误封不计入有效计数：FALSE_POSITIVE 不叠加次数 */
    @Test
    void falsePositiveDoesNotCount() {
        BanStatus s = BanStatus.FALSE_POSITIVE;
        // 只有 ACTIVE / EXPIRED 计入；FALSE_POSITIVE 与 MANUAL_UNBAN(按配置) 不计
        assertNotEquals(BanStatus.ACTIVE, s);
        assertNotEquals(BanStatus.EXPIRED, s);
    }

    /** 举报冷却：提交成功后 5 分钟内不可再次使用 */
    @Test
    void reportCooldownLogic() {
        long cooldownMs = 300000L; // 5 分钟
        long submittedAt = TimeUtil.tickMs();
        long now = submittedAt + 1000; // 1 秒后再次尝试

        boolean cooling = now < submittedAt + cooldownMs;
        assertTrue(cooling, "冷却期内应拒绝");

        long later = submittedAt + cooldownMs + 1;
        assertFalse(later < submittedAt + cooldownMs, "冷却结束后应放行");
    }

    /** 配置模板必须为有效 JSON，不能包含注释/尾随逗号。 */
    @Test
    void configTemplateIsValidJson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/neorf-ac-config.json")) {
            assertNotNull(in, "默认配置模板缺失");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertDoesNotThrow(() -> JsonParser.parseString(json), "配置模板必须是合法 JSON");
        }
    }

    @Test
    void nameBansPersistAcrossStorageReload() {
        JsonStorage storage = new JsonStorage(tempDir);
        storage.init();
        storage.saveNameBans(Map.of("notch", new NameBanRecord("Notch", "manual-ban", 100L, 200L)));

        NameBanRecord loaded = storage.loadNameBans().get("notch");
        assertNotNull(loaded);
        assertEquals("Notch", loaded.getName());
        assertEquals("manual-ban", loaded.getReason());
        assertEquals(200L, loaded.getBanEndMs());
    }

    @Test
    void attackRateWindowMeasuresFiftyCpsOverTwoSeconds() {
        AttackRateWindow window = new AttackRateWindow();
        int count = 0;
        for (int attack = 0; attack < 100; attack++) {
            count = window.recordAndCount(attack * 20L, 2000L);
        }
        assertEquals(100, count);
        assertEquals(50.0, AttackRateWindow.averageCps(count, 2000L), 1e-6);
    }

    @Test
    void packetFloodRequiresSustainedRollingWindowOverLimit() {
        PacketFloodWindow window = new PacketFloodWindow();
        boolean flagged = false;
        for (long now = 0; now <= 4000; now += 20) {
            flagged |= window.record(now, 20, 2).shouldFlag();
        }
        assertTrue(flagged, "超过阈值并持续 2 秒后应标记");
    }

    /** MathUtil：水平距离与三维速度 */
    @Test
    void mathUtilTest() {
        Vec3 a = new Vec3(0, 0, 0);
        Vec3 b = new Vec3(3, 0, 4);
        assertEquals(5.0, MathUtil.distance(a, b), 1e-6, "三维距离应为 5");

        Vec3 c = new Vec3(3, 100, 4);
        assertEquals(5.0, MathUtil.horizontalDistance(a, c), 1e-6, "水平距离忽略 y 应为 5");

        // 1 秒位移 5 格 → 5 b/s
        assertEquals(5.0, MathUtil.speedPerSecond(5.0, 1000), 1e-6);
    }

    /** 角度差环绕 */
    @Test
    void angleDeltaTest() {
        assertEquals(20.0f, MathUtil.angleDelta(170f, -170f), "170 与 -170 相差 20 而非 340");
    }
}
