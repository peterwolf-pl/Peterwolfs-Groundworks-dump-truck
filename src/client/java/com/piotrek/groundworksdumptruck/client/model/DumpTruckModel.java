package com.piotrek.groundworksdumptruck.client.model;

import com.piotrek.groundworksdumptruck.client.render.DumpTruckRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Detailed Minecraft-styled 6x4 rigid construction dump truck.
 *
 * <p>The model is intentionally geometry-heavy for a vehicle entity:
 * cab-over body, exposed ladder and mirrors, three axles, wider rear wheel
 * assemblies, chassis equipment, ribbed tipping body and hydraulic lift.
 * The dump body is a separate pivoted part so cargo and body use exactly the
 * same transform while tipping.
 */
public class DumpTruckModel extends EntityModel<DumpTruckRenderState> {

    private final ModelPart dumpBed;
    private final ModelPart hydraulicLift;
    private final ModelPart beacon;

    private final ModelPart frontLeftWheel;
    private final ModelPart frontRightWheel;
    private final ModelPart rearLeftWheelA;
    private final ModelPart rearRightWheelA;
    private final ModelPart rearLeftWheelB;
    private final ModelPart rearRightWheelB;

    public DumpTruckModel(ModelPart root) {
        super(root);
        this.dumpBed = root.getChild("dump_bed");
        this.hydraulicLift = root.getChild("hydraulic_lift");
        this.beacon = root.getChild("cab").getChild("beacon");

        this.frontLeftWheel = root.getChild("front_left_wheel");
        this.frontRightWheel = root.getChild("front_right_wheel");
        this.rearLeftWheelA = root.getChild("rear_left_wheel_a");
        this.rearRightWheelA = root.getChild("rear_right_wheel_a");
        this.rearLeftWheelB = root.getChild("rear_left_wheel_b");
        this.rearRightWheelB = root.getChild("rear_right_wheel_b");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Main ladder frame, cross members and bumpers.
        root.addOrReplaceChild(
                "chassis",
                CubeListBuilder.create()
                        .texOffs(312, 0).addBox(-13.0F, 5.0F, -49.0F, 4.0F, 4.0F, 96.0F)
                        .texOffs(312, 0).addBox(9.0F, 5.0F, -49.0F, 4.0F, 4.0F, 96.0F)
                        .texOffs(312, 0).addBox(-12.0F, 7.0F, -43.0F, 24.0F, 4.0F, 5.0F)
                        .texOffs(312, 0).addBox(-12.0F, 7.0F, -25.0F, 24.0F, 4.0F, 5.0F)
                        .texOffs(312, 0).addBox(-12.0F, 7.0F, -6.0F, 24.0F, 4.0F, 5.0F)
                        .texOffs(312, 0).addBox(-12.0F, 7.0F, 16.0F, 24.0F, 4.0F, 5.0F)
                        .texOffs(312, 0).addBox(-12.0F, 7.0F, 39.0F, 24.0F, 4.0F, 5.0F)
                        .texOffs(312, 0).addBox(-18.0F, 4.0F, 46.0F, 36.0F, 5.0F, 5.0F)
                        .texOffs(312, 0).addBox(-18.0F, 5.0F, -52.0F, 36.0F, 4.0F, 4.0F)

                        // Fuel tank, battery box and air tanks.
                        .texOffs(128, 288).addBox(-18.0F, 2.0F, 2.0F, 8.0F, 10.0F, 20.0F)
                        .texOffs(128, 288).addBox(10.0F, 3.0F, 3.0F, 9.0F, 9.0F, 17.0F)
                        .texOffs(128, 288).addBox(-8.0F, 8.0F, -13.0F, 7.0F, 6.0F, 18.0F)
                        .texOffs(128, 288).addBox(1.0F, 8.0F, -13.0F, 7.0F, 6.0F, 18.0F)

                        // Rear mud flap brackets and tow point.
                        .texOffs(312, 0).addBox(-18.0F, 6.0F, -50.0F, 3.0F, 12.0F, 2.0F)
                        .texOffs(312, 0).addBox(15.0F, 6.0F, -50.0F, 3.0F, 12.0F, 2.0F)
                        .texOffs(128, 288).addBox(-4.0F, 8.0F, -55.0F, 8.0F, 5.0F, 5.0F),
                PartPose.ZERO
        );

        PartDefinition cab = root.addOrReplaceChild(
                "cab",
                CubeListBuilder.create()
                        // Main cab shell.
                        .texOffs(0, 0).addBox(-20.0F, -12.0F, 22.0F, 40.0F, 23.0F, 28.0F)
                        .texOffs(0, 0).addBox(-21.0F, -27.0F, 19.0F, 42.0F, 5.0F, 34.0F)
                        .texOffs(0, 0).addBox(-20.0F, -22.0F, 20.0F, 40.0F, 10.0F, 5.0F)
                        .texOffs(0, 0).addBox(-20.0F, -22.0F, 47.0F, 40.0F, 10.0F, 4.0F)

                        // Windshield and side glass.
                        .texOffs(312, 160).addBox(-15.0F, -21.0F, 50.2F, 30.0F, 10.0F, 1.0F)
                        .texOffs(312, 160).addBox(-20.5F, -21.0F, 29.0F, 1.0F, 10.0F, 16.0F)
                        .texOffs(312, 160).addBox(19.5F, -21.0F, 29.0F, 1.0F, 10.0F, 16.0F)

                        // Front grille, bumper and headlights.
                        .texOffs(384, 288).addBox(-13.0F, -7.0F, 50.5F, 26.0F, 9.0F, 1.5F)
                        .texOffs(128, 288).addBox(-18.0F, 9.0F, 46.5F, 36.0F, 3.0F, 5.0F)
                        .texOffs(256, 416).addBox(-17.0F, -7.0F, 50.8F, 5.0F, 4.0F, 1.0F)
                        .texOffs(256, 416).addBox(12.0F, -7.0F, 50.8F, 5.0F, 4.0F, 1.0F)
                        .texOffs(320, 416).addBox(-17.0F, 2.0F, 50.8F, 4.0F, 2.0F, 1.0F)
                        .texOffs(320, 416).addBox(13.0F, 2.0F, 50.8F, 4.0F, 2.0F, 1.0F)

                        // Wheel arches / front fenders.
                        .texOffs(0, 0).addBox(-23.0F, 1.0F, 23.0F, 7.0F, 3.0F, 22.0F)
                        .texOffs(0, 0).addBox(16.0F, 1.0F, 23.0F, 7.0F, 3.0F, 22.0F)

                        // Boarding steps.
                        .texOffs(0, 416).addBox(-24.0F, 7.0F, 25.0F, 5.0F, 2.0F, 11.0F)
                        .texOffs(0, 416).addBox(-24.0F, 12.0F, 25.0F, 5.0F, 2.0F, 11.0F)
                        .texOffs(0, 416).addBox(19.0F, 7.0F, 25.0F, 5.0F, 2.0F, 11.0F)
                        .texOffs(0, 416).addBox(19.0F, 12.0F, 25.0F, 5.0F, 2.0F, 11.0F)

                        // Mirrors with arms.
                        .texOffs(128, 288).addBox(-24.0F, -18.0F, 39.0F, 3.0F, 7.0F, 5.0F)
                        .texOffs(128, 288).addBox(21.0F, -18.0F, 39.0F, 3.0F, 7.0F, 5.0F)
                        .texOffs(128, 288).addBox(-23.0F, -14.0F, 42.0F, 3.0F, 2.0F, 7.0F)
                        .texOffs(128, 288).addBox(20.0F, -14.0F, 42.0F, 3.0F, 2.0F, 7.0F)

                        // Exhaust stack.
                        .texOffs(312, 0).addBox(14.0F, -24.0F, 15.0F, 3.0F, 25.0F, 3.0F)
                        .texOffs(312, 0).addBox(13.0F, -27.0F, 14.0F, 5.0F, 3.0F, 5.0F)

                        // Cab interior visible through glass.
                        .texOffs(384, 288).addBox(-7.0F, -6.0F, 31.0F, 12.0F, 4.0F, 12.0F)
                        .texOffs(384, 288).addBox(-7.0F, -14.0F, 30.0F, 12.0F, 9.0F, 3.0F)
                        .texOffs(384, 288).addBox(-1.0F, -7.0F, 44.0F, 2.0F, 8.0F, 2.0F)
                        .texOffs(384, 288).addBox(-5.0F, -10.0F, 45.0F, 10.0F, 2.0F, 2.0F)
                        .texOffs(384, 288).addBox(-10.0F, -5.0F, 45.0F, 20.0F, 5.0F, 3.0F),
                PartPose.ZERO
        );

        cab.addOrReplaceChild(
                "beacon",
                CubeListBuilder.create()
                        .texOffs(256, 416).addBox(-2.5F, -31.0F, 33.0F, 5.0F, 4.0F, 5.0F)
                        .texOffs(0, 416).addBox(-1.0F, -30.0F, 32.0F, 2.0F, 2.0F, 7.0F),
                PartPose.ZERO
        );

        addWheel(root, "front_left_wheel", -22.0F, 13.0F, 34.0F, 10.0F, true);
        addWheel(root, "front_right_wheel", 22.0F, 13.0F, 34.0F, 10.0F, true);
        addWheel(root, "rear_left_wheel_a", -23.0F, 13.0F, -16.0F, 14.0F, false);
        addWheel(root, "rear_right_wheel_a", 23.0F, 13.0F, -16.0F, 14.0F, false);
        addWheel(root, "rear_left_wheel_b", -23.0F, 13.0F, -37.0F, 14.0F, false);
        addWheel(root, "rear_right_wheel_b", 23.0F, 13.0F, -37.0F, 14.0F, false);

        // Tipping body. Local Z=0 is the rear hinge and positive Z runs toward the cab.
        CubeListBuilder bed = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-22.0F, -5.0F, 0.0F, 44.0F, 4.0F, 65.0F)
                .texOffs(0, 0).addBox(-24.0F, -25.0F, 1.0F, 3.0F, 20.0F, 63.0F)
                .texOffs(0, 0).addBox(21.0F, -25.0F, 1.0F, 3.0F, 20.0F, 63.0F)
                .texOffs(0, 0).addBox(-22.0F, -27.0F, 62.0F, 44.0F, 22.0F, 3.0F)
                .texOffs(0, 0).addBox(-22.0F, -24.0F, -2.0F, 44.0F, 19.0F, 3.0F)
                .texOffs(0, 0).addBox(-25.0F, -28.0F, 0.0F, 4.0F, 3.0F, 65.0F)
                .texOffs(0, 0).addBox(21.0F, -28.0F, 0.0F, 4.0F, 3.0F, 65.0F)

                // Inner wear plates and external reflective strips.
                .texOffs(256, 288).addBox(-20.5F, -22.5F, 2.0F, 1.0F, 16.0F, 59.0F)
                .texOffs(256, 288).addBox(19.5F, -22.5F, 2.0F, 1.0F, 16.0F, 59.0F)
                .texOffs(320, 416).addBox(-24.6F, -9.0F, 4.0F, 1.0F, 2.0F, 54.0F)
                .texOffs(320, 416).addBox(23.6F, -9.0F, 4.0F, 1.0F, 2.0F, 54.0F)

                // Rear tailgate latch, lamps and hinge bosses.
                .texOffs(128, 288).addBox(-20.0F, -8.0F, -4.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(128, 288).addBox(15.0F, -8.0F, -4.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(320, 416).addBox(-18.0F, -19.0F, -3.1F, 6.0F, 3.0F, 1.0F)
                .texOffs(320, 416).addBox(12.0F, -19.0F, -3.1F, 6.0F, 3.0F, 1.0F);

        // Deep side ribs make the body read as pressed heavy-gauge steel.
        for (int z = 8; z <= 56; z += 12) {
            bed.texOffs(0, 0).addBox(-26.0F, -24.0F, z, 3.0F, 18.0F, 2.0F);
            bed.texOffs(0, 0).addBox(23.0F, -24.0F, z, 3.0F, 18.0F, 2.0F);
        }

        // Rear inspection ladder.
        bed.texOffs(128, 288).addBox(-18.0F, -20.0F, -3.0F, 3.0F, 14.0F, 2.0F);
        bed.texOffs(128, 288).addBox(-9.0F, -20.0F, -3.0F, 3.0F, 14.0F, 2.0F);
        bed.texOffs(128, 288).addBox(-18.0F, -18.0F, -3.2F, 12.0F, 2.0F, 2.0F);
        bed.texOffs(128, 288).addBox(-18.0F, -12.0F, -3.2F, 12.0F, 2.0F, 2.0F);
        bed.texOffs(128, 288).addBox(-18.0F, -6.0F, -3.2F, 12.0F, 2.0F, 2.0F);

        root.addOrReplaceChild(
                "dump_bed",
                bed,
                PartPose.offset(0.0F, 0.0F, -44.0F)
        );

        PartDefinition hydraulic = root.addOrReplaceChild(
                "hydraulic_lift",
                CubeListBuilder.create()
                        .texOffs(312, 0).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 6.0F, 28.0F)
                        .texOffs(0, 416).addBox(-2.0F, -1.0F, 24.0F, 4.0F, 4.0F, 27.0F),
                PartPose.offset(0.0F, 7.0F, -27.0F)
        );

        hydraulic.addOrReplaceChild(
                "pivot_cap",
                CubeListBuilder.create()
                        .texOffs(128, 288).addBox(-4.0F, -4.0F, -3.0F, 8.0F, 8.0F, 6.0F),
                PartPose.ZERO
        );

        return LayerDefinition.create(mesh, 512, 512);
    }

    private static void addWheel(
            PartDefinition root,
            String name,
            float x,
            float y,
            float z,
            float width,
            boolean front
    ) {
        float half = width * 0.5F;

        CubeListBuilder wheel = CubeListBuilder.create()
                // Layered rectangular facets approximate a rounded construction tire.
                .texOffs(0, 288).addBox(-half, -11.0F, -4.0F, width, 22.0F, 8.0F)
                .texOffs(0, 288).addBox(-half, -10.0F, -7.0F, width, 20.0F, 14.0F)
                .texOffs(0, 288).addBox(-half, -8.0F, -9.0F, width, 16.0F, 18.0F)
                .texOffs(0, 288).addBox(-half, -5.0F, -10.0F, width, 10.0F, 20.0F)

                // Rim and hub.
                .texOffs(128, 288).addBox(-half - 0.5F, -6.0F, -6.0F, width + 1.0F, 12.0F, 12.0F)
                .texOffs(384, 288).addBox(-half - 1.0F, -3.0F, -3.0F, width + 2.0F, 6.0F, 6.0F);

        // Chunky tread lugs. They deliberately sit slightly proud of the tire.
        for (int offset = -7; offset <= 7; offset += 7) {
            wheel.texOffs(384, 288).addBox(-half - 0.6F, -11.8F, offset - 2.0F, width + 1.2F, 2.0F, 4.0F);
            wheel.texOffs(384, 288).addBox(-half - 0.6F, 9.8F, offset - 2.0F, width + 1.2F, 2.0F, 4.0F);
        }

        // Rear wheel assemblies are visibly wider to read as duals.
        if (!front) {
            wheel.texOffs(0, 288).addBox(-1.0F, -10.0F, -9.8F, 2.0F, 20.0F, 19.6F);
        }

        root.addOrReplaceChild(name, wheel, PartPose.offset(x, y, z));
    }

    @Override
    public void setupAnim(DumpTruckRenderState state) {
        float wheelRad = (float) Math.toRadians(-state.wheelRotation);

        this.frontLeftWheel.xRot = wheelRad;
        this.frontRightWheel.xRot = wheelRad;
        this.rearLeftWheelA.xRot = wheelRad;
        this.rearRightWheelA.xRot = wheelRad;
        this.rearLeftWheelB.xRot = wheelRad;
        this.rearRightWheelB.xRot = wheelRad;

        float steerRad = (float) Math.toRadians(state.steerAngle);
        this.frontLeftWheel.yRot = steerRad;
        this.frontRightWheel.yRot = steerRad;

        float bedRad = (float) Math.toRadians(-state.bedAngle);
        this.dumpBed.xRot = bedRad;
        this.hydraulicLift.xRot = bedRad * 0.52F;
        this.beacon.yRot = state.beaconSpin;
    }
}
