package com.neorf.anticheat.check.combat;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AttackRateWindow {

    private final Deque<Long> timestamps = new ArrayDeque<>();

    public synchronized int recordAndCount(long nowMs, long windowMs) {
        long effectiveWindowMs = Math.max(1L, windowMs);
        while (!timestamps.isEmpty() && nowMs - timestamps.peekFirst() >= effectiveWindowMs) {
            timestamps.removeFirst();
        }
        timestamps.addLast(nowMs);
        return timestamps.size();
    }

    public static double averageCps(int attacks, long windowMs) {
        return attacks * 1000.0 / Math.max(1L, windowMs);
    }
}