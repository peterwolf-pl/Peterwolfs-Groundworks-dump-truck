package com.piotrek.groundworksdumptruck.vehicle;

import com.piotrek.groundworks.api.GroundworksApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Three-axle road/construction truck movement model.
 *
 * <p>All six wheel contact points sample the real Groundworks surface height,
 * so the cab and dump body follow partially filled granular cells instead of
 * snapping to full vanilla block height.</p>
 */
public final class DumpTruckMovementController {

    public static final float MAX_FORWARD_SPEED = 0.34F;
    public static final float MAX_REVERSE_SPEED = -0.18F;
    public static final float ACCELERATION = 0.018F;
    public static final float BRAKING = 0.034F;
    public static final float FRICTION = 0.010F;

    public static final float MAX_STEER_ANGLE = 32.0F;
    public static final float STEER_SPEED = 3.2F;
    public static final float STEER_RECENTER = 2.7F;

    public static final float WHEEL_RADIUS_METERS = 0.59F;
    public static final double FRONT_AXLE_OFFSET = 2.10D;
    public static final double MID_AXLE_OFFSET = -1.05D;
    public static final double REAR_AXLE_OFFSET = -2.05D;
    public static final double TRACK_GAUGE = 2.25D;
    private static final double EFFECTIVE_WHEELBASE = 3.65D;

    private float forwardSpeed;
    private float steerAngle;
    private float wheelRotation;
    private float vehiclePitch;
    private float vehicleRoll;

    public record StepResult(
            float forwardSpeed,
            float steerAngle,
            float wheelRotation,
            float deltaYaw
    ) {}

    public StepResult step(float throttle, float steer) {
        if (throttle > 0.05F) {
            if (forwardSpeed < 0.0F) {
                forwardSpeed = Math.min(0.0F, forwardSpeed + BRAKING);
            } else {
                forwardSpeed = Math.min(MAX_FORWARD_SPEED, forwardSpeed + ACCELERATION * throttle);
            }
        } else if (throttle < -0.05F) {
            if (forwardSpeed > 0.0F) {
                forwardSpeed = Math.max(0.0F, forwardSpeed - BRAKING);
            } else {
                forwardSpeed = Math.max(MAX_REVERSE_SPEED, forwardSpeed + ACCELERATION * throttle);
            }
        } else if (forwardSpeed > 0.0F) {
            forwardSpeed = Math.max(0.0F, forwardSpeed - FRICTION);
        } else if (forwardSpeed < 0.0F) {
            forwardSpeed = Math.min(0.0F, forwardSpeed + FRICTION);
        }

        if (steer > 0.05F) {
            steerAngle = Math.min(MAX_STEER_ANGLE, steerAngle + STEER_SPEED * steer);
        } else if (steer < -0.05F) {
            steerAngle = Math.max(-MAX_STEER_ANGLE, steerAngle + STEER_SPEED * steer);
        } else if (steerAngle > 0.0F) {
            steerAngle = Math.max(0.0F, steerAngle - STEER_RECENTER);
        } else if (steerAngle < 0.0F) {
            steerAngle = Math.min(0.0F, steerAngle + STEER_RECENTER);
        }

        float turn = (float) Math.tan(Math.toRadians(steerAngle));
        float deltaYaw = (float) Math.toDegrees((forwardSpeed / EFFECTIVE_WHEELBASE) * turn);

        float circumference = 2.0F * (float) Math.PI * WHEEL_RADIUS_METERS;
        wheelRotation = Mth.wrapDegrees(
                wheelRotation + (forwardSpeed / circumference) * 360.0F
        );

        return new StepResult(forwardSpeed, steerAngle, wheelRotation, deltaYaw);
    }

    public void updateTerrainOrientation(Level level, Vec3 pos, float yaw) {
        WheelHeights h = sampleAll(level, pos, yaw);

        double frontY = (h.fl + h.fr) * 0.5D;
        double rearGroupY = (h.ml + h.mr + h.rl + h.rr) * 0.25D;
        double leftY = (h.fl + h.ml + h.rl) / 3.0D;
        double rightY = (h.fr + h.mr + h.rr) / 3.0D;

        double targetPitch = Math.toDegrees(
                Math.atan2(rearGroupY - frontY, EFFECTIVE_WHEELBASE)
        );
        double targetRoll = Math.toDegrees(
                Math.atan2(leftY - rightY, TRACK_GAUGE)
        );

        targetPitch = Mth.clamp(targetPitch, -24.0D, 24.0D);
        targetRoll = Mth.clamp(targetRoll, -16.0D, 16.0D);

        vehiclePitch = (float) Mth.lerp(0.16D, vehiclePitch, targetPitch);
        vehicleRoll = (float) Mth.lerp(0.16D, vehicleRoll, targetRoll);
    }

    public double getAverageGroundY(Level level, Vec3 pos, float yaw) {
        WheelHeights h = sampleAll(level, pos, yaw);
        return (h.fl + h.fr + h.ml + h.mr + h.rl + h.rr) / 6.0D;
    }

    private WheelHeights sampleAll(Level level, Vec3 pos, float yaw) {
        double yawRad = Math.toRadians(yaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double halfTrack = TRACK_GAUGE * 0.5D;

        return new WheelHeights(
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, FRONT_AXLE_OFFSET, -halfTrack),
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, FRONT_AXLE_OFFSET, halfTrack),
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, MID_AXLE_OFFSET, -halfTrack),
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, MID_AXLE_OFFSET, halfTrack),
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, REAR_AXLE_OFFSET, -halfTrack),
                sample(level, pos, fwdX, fwdZ, rightX, rightZ, REAR_AXLE_OFFSET, halfTrack)
        );
    }

    private static double sample(
            Level level,
            Vec3 pos,
            double fwdX,
            double fwdZ,
            double rightX,
            double rightZ,
            double axleOffset,
            double sideOffset
    ) {
        double x = pos.x + fwdX * axleOffset + rightX * sideOffset;
        double z = pos.z + fwdZ * axleOffset + rightZ * sideOffset;
        return sampleGroundHeight(level, x, pos.y, z);
    }

    public static double sampleGroundHeight(Level level, double x, double vehicleY, double z) {
        BlockPos bp = BlockPos.containing(x, vehicleY + 0.8D, z);

        for (int dy = 0; dy <= 4; dy++) {
            BlockPos check = bp.below(dy);

            if (level instanceof ServerLevel serverLevel) {
                double surfaceY = GroundworksApi.getSurfaceWorldY(serverLevel, check, x, z);
                if (Double.isFinite(surfaceY)) {
                    return surfaceY;
                }
            }

            BlockState state = level.getBlockState(check);
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(level, check);
                if (!shape.isEmpty()) {
                    return check.getY() + shape.max(Direction.Axis.Y);
                }
                if (state.isSolid()) {
                    return check.getY() + 1.0D;
                }
            }
        }

        return vehicleY;
    }

    public float forwardSpeed() { return forwardSpeed; }
    public float steerAngle() { return steerAngle; }
    public float wheelRotation() { return wheelRotation; }
    public float vehiclePitch() { return vehiclePitch; }
    public float vehicleRoll() { return vehicleRoll; }

    public void setForwardSpeed(float value) { forwardSpeed = value; }
    public void setSteerAngle(float value) { steerAngle = value; }
    public void setWheelRotation(float value) { wheelRotation = value; }
    public void setOrientation(float pitch, float roll) {
        vehiclePitch = pitch;
        vehicleRoll = roll;
    }

    private record WheelHeights(
            double fl, double fr,
            double ml, double mr,
            double rl, double rr
    ) {}
}
