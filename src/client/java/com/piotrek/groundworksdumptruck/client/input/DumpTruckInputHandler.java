package com.piotrek.groundworksdumptruck.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.network.DumpTruckInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class DumpTruckInputHandler {

    private static float lastThrottle;
    private static float lastSteer;
    private static float lastBedLift;
    private static int keepaliveTicks;

    private DumpTruckInputHandler() {}

    public static void clientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (!(client.player.getVehicle() instanceof GroundworksDumpTruckEntity)) {
            lastThrottle = 0.0F;
            lastSteer = 0.0F;
            lastBedLift = 0.0F;
            keepaliveTicks = 0;
            return;
        }

        boolean inGame = client.mouseHandler != null && client.mouseHandler.isMouseGrabbed();

        boolean forward = (client.options.keyUp != null && client.options.keyUp.isDown())
                || (client.player.input != null && client.player.input.keyPresses.forward())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_W));
        boolean backward = (client.options.keyDown != null && client.options.keyDown.isDown())
                || (client.player.input != null && client.player.input.keyPresses.backward())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_S));
        boolean left = (client.options.keyLeft != null && client.options.keyLeft.isDown())
                || (client.player.input != null && client.player.input.keyPresses.left())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_A));
        boolean right = (client.options.keyRight != null && client.options.keyRight.isDown())
                || (client.player.input != null && client.player.input.keyPresses.right())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_D));

        boolean bedUp = (DumpTruckKeyBindings.KEY_BED_UP != null
                && DumpTruckKeyBindings.KEY_BED_UP.isDown())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_UP));
        boolean bedDown = (DumpTruckKeyBindings.KEY_BED_DOWN != null
                && DumpTruckKeyBindings.KEY_BED_DOWN.isDown())
                || (inGame && InputConstants.isKeyDown(InputConstants.KEY_DOWN));

        float throttle = 0.0F;
        float steer = 0.0F;
        float bedLift = 0.0F;

        if (forward) throttle += 1.0F;
        if (backward) throttle -= 1.0F;
        if (left) steer -= 1.0F;
        if (right) steer += 1.0F;
        if (bedUp) bedLift += 1.0F;
        if (bedDown) bedLift -= 1.0F;

        boolean changed = throttle != lastThrottle
                || steer != lastSteer
                || bedLift != lastBedLift;

        if (changed || --keepaliveTicks <= 0) {
            ClientPlayNetworking.send(new DumpTruckInputPayload(
                    throttle, steer, bedLift
            ));
            lastThrottle = throttle;
            lastSteer = steer;
            lastBedLift = bedLift;
            keepaliveTicks = 3;
        }
    }
}
