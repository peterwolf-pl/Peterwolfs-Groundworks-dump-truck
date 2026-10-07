package com.piotrek.groundworksdumptruck.client.model;

import com.piotrek.groundworksdumptruck.client.render.DumpTruckRenderState;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Detailed rigid 6x4 construction dump truck.
 *
 * <p>The hierarchy keeps all visibly mechanical parts independent: front
 * steering wheels, tandem rear wheels, dump body, tailgate, telescopic ram and
 * safety beacon. The load is rendered separately as a generated loose surface.</p>
 */
public class DumpTruckModel extends EntityModel<DumpTruckRenderState> {

    private final ModelPart frontLeftWheel;
    private final ModelPart frontRightWheel;
    private final ModelPart midLeftWheel;
    private final ModelPart midRightWheel;
    private final ModelPart rearLeftWheel;
    private final ModelPart rearRightWheel;
    private final ModelPart dumpBed;
    private final ModelPart tailgate;
    private final ModelPart ramUpper;
    private final ModelPart ramLower;
    private final ModelPart beaconReflector;

    public DumpTruckModel(ModelPart root) {
        super(root);
        this.frontLeftWheel = root.getChild("front_left_wheel");
        this.frontRightWheel = root.getChild("front_right_wheel");
        this.midLeftWheel = root.getChild("mid_left_wheel");
        this.midRightWheel = root.getChild("mid_right_wheel");
        this.rearLeftWheel = root.getChild("rear_left_wheel");
        this.rearRightWheel = root.getChild("rear_right_wheel");

        this.dumpBed = root.getChild("dump_bed");
        this.tailgate = this.dumpBed.getChild("tailgate");

        ModelPart hydraulics = root.getChild("hydraulics");
        this.ramLower = hydraulics.getChild("ram_lower");
        this.ramUpper = hydraulics.getChild("ram_upper");

        this.beaconReflector = root.getChild("cab")
                .getChild("beacon_base")
                .getChild("beacon_reflector");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Main ladder frame, bumper, tanks and tandem suspension.
        root.addOrReplaceChild(
                "chassis",
                CubeListBuilder.create()
                        .texOffs(256, 0).addBox(-8.5F, 6.0F, -49.0F, 17.0F, 7.0F, 94.0F)
                        .texOffs(256, 0).addBox(-15.5F, 9.0F, -49.5F, 31.0F, 5.0F, 5.0F)
                        .texOffs(256, 0).addBox(-14.0F, 9.0F, 40.0F, 28.0F, 5.0F, 5.0F)
                        .texOffs(256, 0).addBox(-11.5F, 3.0F, -18.0F, 23.0F, 4.0F, 42.0F)

                        // Fuel tank, AdBlue/tool box and exhaust silencer.
                        .texOffs(256, 64).addBox(-15.0F, 1.0F, 9.0F, 7.0F, 10.0F, 19.0F)
                        .texOffs(256, 64).addBox(8.0F, 2.0F, 10.0F, 7.0F, 9.0F, 14.0F)
                        .texOffs(256, 64).addBox(8.5F, -12.0F, 7.0F, 5.0F, 16.0F, 5.0F)

                        // Rear mudguards and tandem suspension beam hints.
                        .texOffs(256, 0).addBox(-21.0F, 0.0F, -45.0F, 9.0F, 2.0F, 38.0F)
                        .texOffs(256, 0).addBox(12.0F, 0.0F, -45.0F, 9.0F, 2.0F, 38.0F)
                        .texOffs(256, 64).addBox(-13.0F, 10.0F, -39.0F, 26.0F, 3.0F, 27.0F),
                PartPose.ZERO
        );

        // Cab-over-engine body with a deliberately non-cubic silhouette.
        PartDefinition cab = root.addOrReplaceChild(
                "cab",
                CubeListBuilder.create()
                        // Hollow lower cab. The old 32x18x28 solid block
                        // filled the whole footwell and hid the driver interior.
                        // Keep only a floor, rear lower bulkhead and low sill panels.
                        .texOffs(0, 128).addBox(-15.0F, 4.0F, 19.0F, 30.0F, 4.0F, 26.0F)
                        .texOffs(0, 128).addBox(-15.0F, -10.0F, 19.0F, 30.0F, 14.0F, 3.0F)
                        .texOffs(0, 128).addBox(-15.5F, -6.0F, 22.0F, 3.5F, 10.0F, 20.0F)
                        .texOffs(0, 128).addBox(12.0F, -6.0F, 22.0F, 3.5F, 10.0F, 20.0F)

                        // Real hollow upper cab: rear wall, A/B pillars, door
                        // lower panels and window frames. These boxes replace
                        // the old solid upper cab so the interior is actually
                        // visible through the windscreen and side openings.
                        .texOffs(0, 128).addBox(-15.0F, -30.0F, 19.0F, 30.0F, 20.0F, 3.0F)
                        // Side window division: narrow rear and front pillars.
                        .texOffs(0, 128).addBox(-15.5F, -30.0F, 22.0F, 3.5F, 20.0F, 3.0F)
                        .texOffs(0, 128).addBox(12.0F, -30.0F, 22.0F, 3.5F, 20.0F, 3.0F)
                        .texOffs(0, 128).addBox(-15.5F, -30.0F, 39.0F, 3.5F, 20.0F, 3.0F)
                        .texOffs(0, 128).addBox(12.0F, -30.0F, 39.0F, 3.5F, 20.0F, 3.0F)

                        // Lower door skins leave the upper side windows open.
                        .texOffs(0, 128).addBox(-15.5F, -12.0F, 24.5F, 3.5F, 12.0F, 14.5F)
                        .texOffs(0, 128).addBox(12.0F, -12.0F, 24.5F, 3.5F, 12.0F, 14.5F)

                        // Front windscreen surround and centre divider. No glass.
                        .texOffs(0, 128).addBox(-15.0F, -30.0F, 41.0F, 3.0F, 20.0F, 3.0F)
                        .texOffs(0, 128).addBox(12.0F, -30.0F, 41.0F, 3.0F, 20.0F, 3.0F)
                        .texOffs(0, 128).addBox(-1.0F, -29.0F, 41.5F, 2.0F, 17.0F, 2.0F)
                        .texOffs(0, 128).addBox(-15.0F, -13.0F, 41.0F, 30.0F, 3.0F, 3.0F)

                        // Sloped nose and grille surround.
                        .texOffs(0, 128).addBox(-15.0F, -8.0F, 42.0F, 30.0F, 14.0F, 6.0F)
                        .texOffs(256, 96).addBox(-11.5F, -4.0F, 47.5F, 23.0F, 8.0F, 1.0F)

                        // Roof with visor lip.
                        .texOffs(0, 128).addBox(-16.0F, -33.0F, 18.0F, 32.0F, 3.0F, 25.0F)
                        .texOffs(0, 128).addBox(-15.5F, -31.5F, 42.5F, 31.0F, 2.0F, 4.0F)

                        // Twin vertical stacks just behind the cab, clear of the side muffler.
                        .texOffs(256, 64).addBox(-16.8F, -42.0F, 14.6F, 2.6F, 44.0F, 2.6F)
                        .texOffs(384, 0).addBox(-17.4F, -43.4F, 14.0F, 3.8F, 1.6F, 3.8F)
                        .texOffs(256, 64).addBox(14.2F, -42.0F, 14.6F, 2.6F, 44.0F, 2.6F)
                        .texOffs(384, 0).addBox(13.6F, -43.4F, 14.0F, 3.8F, 1.6F, 3.8F)

                        // Door seams / lower steps.
                        .texOffs(256, 0).addBox(-19.0F, 3.0F, 21.0F, 4.0F, 3.0F, 18.0F)
                        .texOffs(256, 0).addBox(15.0F, 3.0F, 21.0F, 4.0F, 3.0F, 18.0F)
                        .texOffs(256, 0).addBox(-20.0F, 8.0F, 22.0F, 6.0F, 2.0F, 15.0F)
                        .texOffs(256, 0).addBox(14.0F, 8.0F, 22.0F, 6.0F, 2.0F, 15.0F)

                        // Mirror arms and housings.
                        .texOffs(256, 64).addBox(-20.5F, -25.0F, 39.0F, 5.0F, 2.0F, 2.0F)
                        .texOffs(256, 64).addBox(15.5F, -25.0F, 39.0F, 5.0F, 2.0F, 2.0F)
                        .texOffs(256, 64).addBox(-23.0F, -28.0F, 37.0F, 3.0F, 7.0F, 5.0F)
                        .texOffs(256, 64).addBox(20.0F, -28.0F, 37.0F, 3.0F, 7.0F, 5.0F)

                        // Interior: clearly left-hand driver's seat and dashboard.
                        .texOffs(256, 64).addBox(-11.0F, -9.0F, 27.0F, 9.0F, 4.0F, 9.0F)
                        .texOffs(256, 64).addBox(-11.0F, -18.0F, 27.0F, 9.0F, 9.0F, 3.0F)
                        .texOffs(256, 64).addBox(-12.0F, -13.0F, 38.5F, 24.0F, 5.0F, 3.0F)

                        // Pedal/tunnel details keep the lower cabin readable without
                        // filling the footwell.
                        .texOffs(256, 64).addBox(-1.5F, -2.0F, 25.0F, 3.0F, 6.0F, 16.0F)
                        .texOffs(256, 64).addBox(-10.0F, 1.5F, 38.0F, 3.0F, 1.0F, 4.0F)
                        .texOffs(256, 64).addBox(-5.0F, 1.5F, 38.0F, 3.0F, 1.0F, 4.0F)

                        // Headlamps and indicators.
                        .texOffs(448, 0).addBox(-13.0F, -5.0F, 48.1F, 7.0F, 5.0F, 1.0F)
                        .texOffs(448, 0).addBox(6.0F, -5.0F, 48.1F, 7.0F, 5.0F, 1.0F)
                        .texOffs(448, 32).addBox(-15.0F, 1.0F, 48.1F, 5.0F, 3.0F, 1.0F)
                        .texOffs(448, 32).addBox(10.0F, 1.0F, 48.1F, 5.0F, 3.0F, 1.0F),
                PartPose.offset(
                        0.0F,
                        0.0F,
                        GroundworksDumpTruckEntity.CAB_SHIFT_Z_PX
                )
        );

        // Round wheel, lowered slightly. The column is short and runs into the
        // dashboard (local +Z). The old 7 px shaft continued through the glass.
        PartDefinition steeringWheel = cab.addOrReplaceChild(
                "steering_wheel",
                CubeListBuilder.create()
                        .texOffs(384, 64).addBox(-1.3F, -1.3F, -0.8F, 2.6F, 2.6F, 1.8F)
                        .texOffs(256, 64).addBox(-0.55F, -0.55F, 0.2F, 1.1F, 1.1F, 3.6F),
                PartPose.offsetAndRotation(
                        -6.5F,
                        -13.0F,
                        36.6F,
                        (float) Math.toRadians(-24.0F),
                        0.0F,
                        0.0F
                )
        );
        addRoundSteeringWheel(steeringWheel);

        // Beacon uses a local pivot at its own center. Keeping absolute cab-space
        // coordinates in the rotating child made the reflector orbit around the cab.
        PartDefinition beaconBase = cab.addOrReplaceChild(
                "beacon_base",
                CubeListBuilder.create()
                        .texOffs(256, 64).addBox(-2.5F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, -34.5F, 28.5F)
        );
        beaconBase.addOrReplaceChild(
                "beacon_reflector",
                CubeListBuilder.create()
                        .texOffs(448, 32).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.ZERO
        );

        addWheel(root, "front_left_wheel", -19.0F, 12.0F, 30.0F, true);
        addWheel(root, "front_right_wheel", 19.0F, 12.0F, 30.0F, false);
        addWheel(root, "mid_left_wheel", -19.0F, 12.0F, -17.0F, true);
        addWheel(root, "mid_right_wheel", 19.0F, 12.0F, -17.0F, false);
        addWheel(root, "rear_left_wheel", -19.0F, 12.0F, -37.0F, true);
        addWheel(root, "rear_right_wheel", 19.0F, 12.0F, -37.0F, false);

        // Dump body pivots at the rear. Local +Z points toward the cab.
        PartDefinition bed = root.addOrReplaceChild(
                "dump_bed",
                CubeListBuilder.create()
                        // Floor and double-wall side plates.
                        .texOffs(0, 256).addBox(-16.5F, -2.0F, 0.0F, 33.0F, 4.0F, 68.0F)
                        .texOffs(0, 256).addBox(-20.0F, -22.0F, 0.0F, 4.0F, 24.0F, 68.0F)
                        .texOffs(0, 256).addBox(16.0F, -22.0F, 0.0F, 4.0F, 24.0F, 68.0F)

                        // Front bulkhead toward cab.
                        .texOffs(0, 256).addBox(-20.0F, -26.0F, 64.0F, 40.0F, 28.0F, 4.0F)

                        // Top rails.
                        .texOffs(0, 256).addBox(-21.0F, -24.0F, -1.0F, 5.0F, 4.0F, 70.0F)
                        .texOffs(0, 256).addBox(16.0F, -24.0F, -1.0F, 5.0F, 4.0F, 70.0F)

                        // Underside longitudinal beams.
                        .texOffs(256, 0).addBox(-11.0F, 2.0F, 5.0F, 5.0F, 5.0F, 58.0F)
                        .texOffs(256, 0).addBox(6.0F, 2.0F, 5.0F, 5.0F, 5.0F, 58.0F),
                PartPose.offset(
                        0.0F,
                        0.0F,
                        GroundworksDumpTruckEntity.BED_PIVOT_Z_PX
                )
        );

        // Strong external ribs, separate so the silhouette reads from distance.
        for (int i = 0; i < 6; i++) {
            float z = 7.0F + i * 10.8F;
            bed.addOrReplaceChild(
                    "rib_left_" + i,
                    CubeListBuilder.create()
                            .texOffs(0, 256).addBox(-22.0F, -22.0F, -1.0F, 3.0F, 24.0F, 3.0F),
                    PartPose.offset(0.0F, 0.0F, z)
            );
            bed.addOrReplaceChild(
                    "rib_right_" + i,
                    CubeListBuilder.create()
                            .texOffs(0, 256).addBox(19.0F, -22.0F, -1.0F, 3.0F, 24.0F, 3.0F),
                    PartPose.offset(0.0F, 0.0F, z)
            );
        }

        bed.addOrReplaceChild(
                "tailgate",
                CubeListBuilder.create()
                        .texOffs(0, 256).addBox(-19.0F, 0.0F, -3.0F, 38.0F, 24.0F, 4.0F)
                        .texOffs(256, 0).addBox(-20.0F, 4.0F, -4.0F, 40.0F, 3.0F, 3.0F)
                        .texOffs(256, 0).addBox(-20.0F, 18.0F, -4.0F, 40.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, -22.0F, 1.0F)
        );

        PartDefinition hydraulics = root.addOrReplaceChild(
                "hydraulics",
                CubeListBuilder.create(),
                PartPose.ZERO
        );

        hydraulics.addOrReplaceChild(
                "ram_lower",
                CubeListBuilder.create()
                        .texOffs(256, 64).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 8.0F, 34.0F),
                PartPose.offset(0.0F, 5.0F, -34.0F)
        );

        hydraulics.addOrReplaceChild(
                "ram_upper",
                CubeListBuilder.create()
                        .texOffs(384, 64).addBox(-2.7F, -2.0F, -2.0F, 5.4F, 5.4F, 36.0F),
                PartPose.offset(0.0F, 2.0F, -22.0F)
        );

        return LayerDefinition.create(mesh, 512, 512);
    }

    private static void addRoundSteeringWheel(PartDefinition wheel) {
        int rimSegments = 12;
        float rimRadius = 4.05F;
        for (int i = 0; i < rimSegments; i++) {
            float angle = (float) (i * Math.PI * 2.0D / rimSegments);
            wheel.addOrReplaceChild(
                    "rim_" + i,
                    CubeListBuilder.create()
                            .texOffs(384, 64).addBox(-1.25F, -0.62F, -0.55F, 2.5F, 1.24F, 1.1F),
                    PartPose.offsetAndRotation(
                            (float) Math.sin(angle) * rimRadius,
                            (float) -Math.cos(angle) * rimRadius,
                            0.0F,
                            0.0F,
                            0.0F,
                            angle
                    )
            );
        }

        for (int i = 0; i < 3; i++) {
            float angle = (float) (i * Math.PI * 2.0D / 3.0D);
            wheel.addOrReplaceChild(
                    "spoke_" + i,
                    CubeListBuilder.create()
                            .texOffs(384, 64).addBox(-0.38F, -3.55F, -0.4F, 0.76F, 3.7F, 0.8F),
                    PartPose.offsetAndRotation(
                            0.0F,
                            0.0F,
                            0.0F,
                            0.0F,
                            0.0F,
                            angle
                    )
            );
        }
    }

    private static void addWheel(
            PartDefinition root,
            String name,
            float x,
            float y,
            float z,
            boolean left
    ) {
        float rimX = left ? -6.5F : 4.5F;
        float capX = left ? -7.5F : 6.5F;

        root.addOrReplaceChild(
                name,
                CubeListBuilder.create()
                        // Multi-box rounded tire profile.
                        .texOffs(0, 0).addBox(-5.5F, -10.0F, -4.5F, 11.0F, 20.0F, 9.0F)
                        .texOffs(0, 0).addBox(-5.5F, -9.0F, -7.0F, 11.0F, 18.0F, 14.0F)
                        .texOffs(0, 0).addBox(-5.5F, -7.0F, -9.0F, 11.0F, 14.0F, 18.0F)
                        .texOffs(0, 0).addBox(-5.5F, -4.5F, -10.0F, 11.0F, 9.0F, 20.0F)

                        // Steel rim and hub.
                        .texOffs(384, 0).addBox(rimX, -6.0F, -6.0F, 2.0F, 12.0F, 12.0F)
                        .texOffs(384, 0).addBox(rimX, -7.0F, -3.0F, 2.0F, 14.0F, 6.0F)
                        .texOffs(384, 0).addBox(rimX, -3.0F, -7.0F, 2.0F, 6.0F, 14.0F)
                        .texOffs(256, 64).addBox(capX, -2.5F, -2.5F, 1.5F, 5.0F, 5.0F),
                PartPose.offset(x, y, z)
        );
    }

    @Override
    public void setupAnim(DumpTruckRenderState state) {
        float wheelRad = (float) Math.toRadians(-state.wheelRotation);
        float steerRad = (float) Math.toRadians(state.steerAngle);

        frontLeftWheel.xRot = wheelRad;
        frontRightWheel.xRot = wheelRad;
        midLeftWheel.xRot = wheelRad;
        midRightWheel.xRot = wheelRad;
        rearLeftWheel.xRot = wheelRad;
        rearRightWheel.xRot = wheelRad;

        frontLeftWheel.yRot = steerRad;
        frontRightWheel.yRot = steerRad;

        float bedRad = (float) Math.toRadians(state.bedAngle);
        dumpBed.xRot = bedRad;

        // Gravity-driven tailgate. It starts moving only after a visible tip angle.
        float gateFactor = Math.clamp((state.bedAngle - 12.0F) / 30.0F, 0.0F, 1.0F);
        tailgate.xRot = (float) Math.toRadians(-32.0F * gateFactor);

        // Two-stage telescopic ram follows the dump body approximately.
        ramLower.xRot = (float) Math.toRadians(-13.0F - state.bedAngle * 0.42F);
        ramUpper.xRot = (float) Math.toRadians(-22.0F - state.bedAngle * 0.68F);

        beaconReflector.yRot = state.beaconSpin;
        beaconReflector.visible = state.beaconFlash;
    }
}
