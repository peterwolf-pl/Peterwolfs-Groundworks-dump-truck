package com.piotrek.groundworksdumptruck.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.client.GroundworksDumpTruckClient;
import com.piotrek.groundworksdumptruck.client.model.DumpTruckModel;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class DumpTruckRenderer
        extends EntityRenderer<GroundworksDumpTruckEntity, DumpTruckRenderState> {

    public static final Identifier TEXTURE =
            GroundworksDumpTruckMod.id("textures/entity/dump_truck.png");

    private final DumpTruckModel model;

    public DumpTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DumpTruckModel(
                context.bakeLayer(GroundworksDumpTruckClient.DUMP_TRUCK_LAYER)
        );
        this.shadowRadius = 2.25F;
    }

    @Override
    public DumpTruckRenderState createRenderState() {
        return new DumpTruckRenderState();
    }

    @Override
    public void extractRenderState(
            GroundworksDumpTruckEntity entity,
            DumpTruckRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);

        state.baseYaw = entity.getYRot(partialTick);
        state.basePitch = entity.getVehiclePitch();
        state.baseRoll = entity.getVehicleRoll();
        state.bedAngle = entity.getBedAngle();
        state.steerAngle = entity.getSteerAngle();
        state.forwardSpeed = entity.getForwardSpeed();
        state.wheelRotation = entity.getWheelRotation();
        state.carriedMaterialId = entity.getCarriedMaterialId();
        state.carriedUnits = entity.getCarriedUnits();
        state.fillRatio = (float) entity.getCarriedUnits()
                / (float) GroundworksDumpTruckEntity.BED_CAPACITY;
        state.engineRunning = entity.isEngineRunning();

        state.beaconSpin = (entity.tickCount + partialTick) * 0.70F;
        state.beaconFlash = state.engineRunning && ((entity.tickCount / 4) % 2 == 0);
    }

    @Override
    public void submit(
            DumpTruckRenderState state,
            PoseStack stack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        stack.pushPose();

        stack.rotateDegrees(Axis.YP, -state.baseYaw);

        if (Math.abs(state.basePitch) > 0.01F) {
            stack.rotateDegrees(Axis.XP, state.basePitch);
        }
        if (Math.abs(state.baseRoll) > 0.01F) {
            stack.rotateDegrees(Axis.ZP, state.baseRoll);
        }

        stack.scale(-1.0F, -1.0F, 1.0F);
        stack.translate(0.0F, -1.5F, 0.0F);

        model.setupAnim(state);

        collector.submitModel(
                model,
                state,
                stack,
                RenderTypes.entityCutout(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );

        DumpTruckBedLoadRenderer.submit(state, stack, collector);

        stack.popPose();
    }
}
