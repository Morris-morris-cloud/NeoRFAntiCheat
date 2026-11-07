package com.neorf.anticheat.punishment;

/** A time-limited ban keyed by normalized Minecraft player name. */
public class NameBanRecord {

    private String name;
    private String reason;
    private long banStartMs;
    private long banEndMs;

    public NameBanRecord() {}

    public NameBanRecord(String name, String reason, long banStartMs, long banEndMs) {
        this.name = name;
        this.reason = reason;
        this.banStartMs = banStartMs;
        this.banEndMs = banEndMs;
    }

    public String getName() { return name; }
    public String getReason() { return reason; }
    public long getBanStartMs() { return banStartMs; }
    public long getBanEndMs() { return banEndMs; }

    public boolean isActiveNow(long nowMs) {
        return banEndMs < 0 || nowMs < banEndMs;
    }
}