package com.piotrek.groundworksdumptruck.vehicle;

/** Maps authoritative engine demand and payload mass to a diesel sound mix. */
public final class DumpTruckEngineSoundProfile {

    private static final float IDLE_VOLUME = 0.48F;
    private static final float LOAD_VOLUME = 0.86F;
    private static final float IDLE_PITCH = 0.80F;
    private static final float LOAD_PITCH = 1.08F;

    private DumpTruckEngineSoundProfile() {}

    public record Mix(float volume, float pitch) {}

    public static Mix forLoad(float engineLoad, float fillRatio) {
        float load = Math.clamp(engineLoad, 0.0F, 1.0F);
        float payload = Math.clamp(fillRatio, 0.0F, 1.0F);

        float volume = IDLE_VOLUME
                + (LOAD_VOLUME - IDLE_VOLUME) * load
                + 0.06F * payload;

        // A loaded truck sounds slightly deeper at the same throttle.
        float pitch = IDLE_PITCH
                + (LOAD_PITCH - IDLE_PITCH) * load
                - 0.07F * payload;

        return new Mix(
                Math.clamp(volume, 0.0F, 1.0F),
                Math.clamp(pitch, 0.70F, 1.15F)
        );
    }
}
