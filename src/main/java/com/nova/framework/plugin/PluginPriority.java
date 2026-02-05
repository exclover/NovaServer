package com.nova.framework.plugin;

/**
 * Plugin execution priority
 * Determines order of plugin execution in request processing
 */
public enum PluginPriority {
    /** Execute first (critical infrastructure) */
    HIGHEST(0),

    /** Execute very early (security, logging) */
    VERY_HIGH(10),

    /** Execute early (protocol detection, SSL) */
    HIGH(20),

    /** Execute before normal (authentication) */
    ABOVE_NORMAL(40),

    /** Normal execution order (middleware) */
    NORMAL(50),

    /** Execute after normal (routing) */
    BELOW_NORMAL(60),

    /** Execute late (cleanup, monitoring) */
    LOW(80),

    /** Execute very late (final processing) */
    VERY_LOW(90),

    /** Execute last */
    LOWEST(100);

    private final int value;

    PluginPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public boolean isHigherThan(PluginPriority other) {
        return this.value < other.value;
    }

    public boolean isLowerThan(PluginPriority other) {
        return this.value > other.value;
    }
}
