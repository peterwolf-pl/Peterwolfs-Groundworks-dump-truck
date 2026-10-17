package com.piotrek.groundworksdumptruck.mixin;

import com.piotrek.groundworksdumptruck.route.DumpTruckRouteManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPlaySoundMixin {

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD")
    )
    private void groundworksDumpTruck$onPlayPosSound(
            Entity entity,
            double x,
            double y,
            double z,
            Holder<SoundEvent> sound,
            SoundSource source,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        if (sound != null) {
            String path = sound.unwrapKey()
                    .map(k -> k.identifier().getPath())
                    .orElseGet(() -> sound.value().location().getPath());

            if (path.contains("truck_horn_long") || path.contains("horn_long")) {
                DumpTruckRouteManager.onExcavatorHorn((ServerLevel) (Object) this, x, y, z);
            }
        }
    }

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD")
    )
    private void groundworksDumpTruck$onPlayEntitySound(
            Entity entity,
            Entity target,
            Holder<SoundEvent> sound,
            SoundSource source,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        if (sound != null && target != null) {
            String path = sound.unwrapKey()
                    .map(k -> k.identifier().getPath())
                    .orElseGet(() -> sound.value().location().getPath());

            if (path.contains("truck_horn_long") || path.contains("horn_long")) {
                DumpTruckRouteManager.onExcavatorHorn((ServerLevel) (Object) this, target.getX(), target.getY(), target.getZ());
            }
        }
    }
}
