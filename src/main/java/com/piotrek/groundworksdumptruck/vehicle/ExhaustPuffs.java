package com.piotrek.groundworksdumptruck.vehicle;

/**
 * Small white exhaust puffs between 5% and 20% machine load.
 * Same curve as the Groundworks excavator: rare at 5%, several puffs at 20%.
 */
public final class ExhaustPuffs {

    public static final float MIN_LOAD = 0.05F;
    public static final float MAX_LOAD = 0.20F;

    private ExhaustPuffs() {}

    public static int whitePuffCount(float load, int tick) {
        if (load < MIN_LOAD || load > MAX_LOAD) {
            return 0;
        }
        float blend = (load - MIN_LOAD) / (MAX_LOAD - MIN_LOAD);
        int period = Math.max(1, Math.round(16.0F - 15.0F * blend));
        if (Math.floorMod(tick, period) != 0) {
            return 0;
        }
        return 1 + Math.round(3.0F * blend);
    }
}
