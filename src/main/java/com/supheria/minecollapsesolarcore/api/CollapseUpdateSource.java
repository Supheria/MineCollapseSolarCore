package com.supheria.minecollapsesolarcore.api;

public enum CollapseUpdateSource {
    PLAYER_ACTION,
    FALLING_BLOCK_SETTLE,
    NEIGHBOR_UPDATE,
    SOLAR_RANDOM_TICK,
    SOLAR_SPREAD,
    EXPLOSION;

    public static CollapseUpdateSource fromName(String name) {
        if (name == null || name.isBlank()) {
            return NEIGHBOR_UPDATE;
        }
        try {
            return CollapseUpdateSource.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NEIGHBOR_UPDATE;
        }
    }

    public boolean isSolarDriven() {
        return this == SOLAR_RANDOM_TICK || this == SOLAR_SPREAD;
    }
}

