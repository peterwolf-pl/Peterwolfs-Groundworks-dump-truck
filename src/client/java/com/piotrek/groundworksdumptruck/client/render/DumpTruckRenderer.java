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

public class DumpTruckRenderer extends EntityRenderer<GroundworksDumpTruckEntity, DumpTruckRenderState> {

    public static final Identifier TEXTURE =
            GroundworksDumpTruckMod.id("textures/entity/dump_truck.png");

    private final DumpTruckModel model;

    public DumpTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DumpTruckModel(context.bakeLayer(GroundworksDumpTruckClient.DUMP_TRUCK_LAYER));
        this.shadowRadius = 2.45F;
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
        state.bedAngle = entity.getBedAngle();
        state.steerAngle = 0.0F;
        state.wheelRotation = 0.0F;
        state.beaconSpin = (entity.tickCount + partialTick) * 0.45F;

        state.carriedMaterialId = entity.getCarriedMaterialId();
        state.carriedUnits = entity.getCarriedUnits();
        state.fillRatio = entity.getFillRatio();
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
        stack.scale(-1.0F, -1.0F, 1.0F);
        stack.translate(0.0F, -1.5F, 0.0F);

        this.model.setupAnim(state);

        collector.submitModel(
                this.model,
                state,
                stack,
                RenderTypes.entityCutout(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );

        GranularDumpBedContentsRenderer.submit(state, stack, collector);

        stack.popPose();
    }
}
