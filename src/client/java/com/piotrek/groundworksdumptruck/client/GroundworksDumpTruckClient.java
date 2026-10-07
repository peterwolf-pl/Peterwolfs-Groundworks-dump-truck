package com.piotrek.groundworksdumptruck.client;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.client.input.DumpTruckInputHandler;
import com.piotrek.groundworksdumptruck.client.input.DumpTruckKeyBindings;
import com.piotrek.groundworksdumptruck.client.model.DumpTruckModel;
import com.piotrek.groundworksdumptruck.client.render.DumpTruckRenderer;
import com.piotrek.groundworksdumptruck.client.render.DumpTruckHudOverlay;
import com.piotrek.groundworksdumptruck.client.sound.DumpTruckEngineSoundController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class GroundworksDumpTruckClient implements ClientModInitializer {

    public static final ModelLayerLocation DUMP_TRUCK_LAYER =
            new ModelLayerLocation(GroundworksDumpTruckMod.id("dump_truck"), "main");

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                DUMP_TRUCK_LAYER,
                DumpTruckModel::createBodyLayer
        );
        EntityRendererRegistry.register(
                GroundworksDumpTruckMod.DUMP_TRUCK,
                DumpTruckRenderer::new
        );
        DumpTruckKeyBindings.register();
        ClientTickEvents.END_CLIENT_TICK.register(DumpTruckInputHandler::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(DumpTruckEngineSoundController::clientTick);
        DumpTruckHudOverlay.register();
    }
}
