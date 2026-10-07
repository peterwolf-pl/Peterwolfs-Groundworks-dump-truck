package com.piotrek.groundworksdumptruck.vehicle;

/** Same idle/load diesel mix as the Groundworks excavator, driven by truck load. */
public final class DumpTruckEngineSoundProfile {

    private static final float IDLE_VOLUME = 0.42F;
    private static final float DRIVE_VOLUME = 0.64F;
    private static final float IDLE_PITCH = 0.94F;
    private static final float DRIVE_PITCH = 1.12F;
    private static final float LOAD_LAYER_VOLUME = 0.86F;

    private DumpTruckEngineSoundProfile() {}

    /**
     * @param volume idle-layer loudness
     * @param pitch idle-layer pitch, rises with RPM
     * @param loadVolume heavier exhaust layer, silent at idle
     * @param loadPitch exhaust pitch; drops when the engine lugs under load
     */
    public record Mix(float volume, float pitch, float loadVolume, float loadPitch) {}

    public static Mix forLoad(float engineLoad, float fillRatio) {
        float effort = Math.clamp(engineLoad, 0.0F, 1.0F);
        float payload = Math.clamp(fillRatio, 0.0F, 1.0F);
        float load = Math.max(effort, payload * 0.78F);
        float lug = payload * (1.0F - effort);

        return new Mix(
                IDLE_VOLUME + (DRIVE_VOLUME - IDLE_VOLUME) * load,
                IDLE_PITCH + (DRIVE_PITCH - IDLE_PITCH) * load,
                LOAD_LAYER_VOLUME * load,
                0.88F + 0.16F * effort - 0.06F * lug
        );
    }
}
