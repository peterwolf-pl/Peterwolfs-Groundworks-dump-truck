package com.piotrek.groundworksdumptruck.client.sound;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.vehicle.DumpTruckEngineSoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Starts and tracks one positional diesel loop for each running dump truck. */
public final class DumpTruckEngineSoundController {

    private static final Map<Integer, EngineLoop> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private DumpTruckEngineSoundController() {}

    public static void clientTick(Minecraft client) {
        if (activeLevel != client.level) {
            ACTIVE.values().forEach(EngineLoop::stopNow);
            ACTIVE.clear();
            activeLevel = client.level;
        }
        if (client.level == null) return;

        Iterator<EngineLoop> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isStopped()) iterator.remove();
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof GroundworksDumpTruckEntity truck)
                    || !truck.isEngineRunning()
                    || ACTIVE.containsKey(truck.getId())) {
                continue;
            }

            EngineLoop sound = new EngineLoop(truck);
            ACTIVE.put(truck.getId(), sound);
            client.getSoundManager().play(sound);
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {

        private final GroundworksDumpTruckEntity truck;

        private EngineLoop(GroundworksDumpTruckEntity truck) {
            super(GroundworksDumpTruckMod.ENGINE_LOOP, SoundSource.NEUTRAL, RandomSource.create());
            this.truck = truck;
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
            this.volume = mix.volume();
            this.pitch = mix.pitch();
        }

        private void stopNow() {
            stop();
        }
    }
}
