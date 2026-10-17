package com.piotrek.groundworksdumptruck.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Headlamp and tail-lamp beams. Same idea as the excavator work lights:
 * invisible light blocks on the ground ahead of each lamp, not a glowing mesh.
 */
public final class TruckLights {

    public static final int HEAD_LEVEL = 15;
    public static final int TAIL_LEVEL = 11;
    public static final int BRAKE_LEVEL = 15;

    /** Cab-local lens centres, in model pixels. The cab shift is applied later. */
    public static final float HEAD_LEFT_X = -9.5F;
    public static final float HEAD_RIGHT_X = 9.5F;
    public static final float HEAD_Y = -2.5F;
    public static final float HEAD_Z = 48.75F;

    /** Root-local points behind the tailgate. Not on the swinging gate. */
    public static final float TAIL_LEFT_X = -14.0F;
    public static final float TAIL_RIGHT_X = 14.0F;
    public static final float TAIL_Y = 0.0F;
    public static final float TAIL_Z = -61.0F;

    private static final int[] HEAD_RANGES = {2, 5, 9};
    private static final int[] TAIL_RANGES = {2, 5};

    private TruckLights() {}

    public static List<BlockPos> beams(
            Vec3 leftLamp,
            Vec3 rightLamp,
            Vec3 feet,
            Vec3 facing,
            boolean rear
    ) {
        Vec3 forward = horizontal(facing);
        Vec3 side = new Vec3(forward.z, 0.0D, -forward.x);
        Vec3 beam = rear ? forward.scale(-1.0D) : forward;
        int[] ranges = rear ? TAIL_RANGES : HEAD_RANGES;
        double spread = rear ? 0.40D : 0.55D;

        List<BlockPos> positions = new ArrayList<>(ranges.length * 4);
        addBeam(positions, leftLamp, feet, beam, side.scale(-spread), ranges);
        addBeam(positions, rightLamp, feet, beam, side.scale(spread), ranges);
        return positions;
    }

    private static void addBeam(
            List<BlockPos> positions,
            Vec3 lamp,
            Vec3 feet,
            Vec3 beam,
            Vec3 side,
            int[] ranges
    ) {
        for (int range : ranges) {
            double x = lamp.x + beam.x * range + side.x * range;
            double z = lamp.z + beam.z * range + side.z * range;
            add(positions, BlockPos.containing(x, feet.y + 1.0D, z));
            add(positions, BlockPos.containing(x, feet.y + 2.0D, z));
        }
    }

    private static Vec3 horizontal(Vec3 facing) {
        Vec3 flat = new Vec3(facing.x, 0.0D, facing.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return flat.normalize();
    }

    private static void add(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }
}
