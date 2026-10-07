package com.piotrek.groundworksdumptruck.client.sound;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.vehicle.DumpTruckEngineSoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Crossfades the excavator idle diesel loop and its heavier load loop. */
public final class DumpTruckEngineSoundController {

    private static final Map<Integer, EnginePair> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private DumpTruckEngineSoundController() {}

    public static void clientTick(Minecraft client) {
        if (activeLevel != client.level) {
            ACTIVE.values().forEach(EnginePair::stopNow);
            ACTIVE.clear();
            activeLevel = client.level;
        }
        if (client.level == null) {
            return;
        }

        Iterator<EnginePair> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            EnginePair pair = iterator.next();
            if (pair.stopped()) {
                pair.stopNow();
                iterator.remove();
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof GroundworksDumpTruckEntity truck)
                    || !truck.isEngineRunning()
                    || ACTIVE.containsKey(truck.getId())) {
                continue;
            }
            EnginePair pair = new EnginePair(truck);
            ACTIVE.put(truck.getId(), pair);
            client.getSoundManager().play(pair.idle);
            client.getSoundManager().play(pair.load);
        }
    }

    private record EnginePair(EngineLoop idle, EngineLoop load) {
        private EnginePair(GroundworksDumpTruckEntity truck) {
            this(
                    new EngineLoop(truck, GroundworksDumpTruckMod.ENGINE_LOOP, false),
                    new EngineLoop(truck, GroundworksDumpTruckMod.ENGINE_LOAD, true)
            );
        }

        private boolean stopped() {
            return idle.isStopped() || load.isStopped();
        }

        private void stopNow() {
            idle.stopNow();
            load.stopNow();
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {

        private final GroundworksDumpTruckEntity truck;
        private final boolean loadLayer;

        private EngineLoop(
                GroundworksDumpTruckEntity truck,
                SoundEvent sound,
                boolean loadLayer
        ) {
            super(sound, SoundSource.NEUTRAL, RandomSource.create());
            this.truck = truck;
            this.loadLayer = loadLayer;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updateSound();
        }

        @Override
        public void tick() {
            if (truck.isRemoved() || !truck.isEngineRunning()) {
                stop();
                return;
            }
            updateSound();
        }

        private void updateSound() {
            this.x = truck.getX();
            this.y = truck.getY() + 1.0D;
            this.z = truck.getZ();
            DumpTruckEngineSoundProfile.Mix mix =
                    DumpTruckEngineSoundProfile.forLoad(
                            truck.getEngineLoad(),
                            truck.getFillRatio()
                    );
            this.volume = loadLayer ? mix.loadVolume() : mix.volume();
            this.pitch = loadLayer ? mix.loadPitch() : mix.pitch();
        }

        private void stopNow() {
            stop();
        }
    }
}
