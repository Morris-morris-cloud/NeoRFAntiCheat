package com.neorf.anticheat.check.packet;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PacketFloodWindow {

    private static final long WINDOW_MS = 1000L;
    private static final long REPORT_INTERVAL_MS = 1000L;

    private final Deque<Long> timestamps = new ArrayDeque<>();
    private long overLimitSince = Long.MIN_VALUE;
    private long lastReportedAt = Long.MIN_VALUE;

    public synchronized Observation record(long nowMs, int maxPacketsPerSecond, int sustainSeconds) {
        while (!timestamps.isEmpty() && nowMs - timestamps.peekFirst() >= WINDOW_MS) {
            timestamps.removeFirst();
        }
        timestamps.addLast(nowMs);

        int packetCount = timestamps.size();
        if (packetCount <= maxPacketsPerSecond) {
            overLimitSince = Long.MIN_VALUE;
            lastReportedAt = Long.MIN_VALUE;
            return new Observation(packetCount, false);
        }

        if (overLimitSince == Long.MIN_VALUE) {
            overLimitSince = nowMs;
        }
        long requiredDuration = Math.max(1L, sustainSeconds) * 1000L;
        boolean sustained = nowMs - overLimitSince >= requiredDuration;
        boolean shouldFlag = sustained && (lastReportedAt == Long.MIN_VALUE
                || nowMs - lastReportedAt >= REPORT_INTERVAL_MS);
        if (shouldFlag) {
            lastReportedAt = nowMs;
        }
        return new Observation(packetCount, shouldFlag);
    }

    public record Observation(int packetCount, boolean shouldFlag) {}
}