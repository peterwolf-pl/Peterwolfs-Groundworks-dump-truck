package com.piotrek.groundworksdumptruck.vehicle;

import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Predictive traffic safety, independent of route/queue state and tick order. */
public final class TruckTrafficGuard {
    private TruckTrafficGuard() {}

    public record Motion(Vec3 center, Vec3 forward, double speed, double yawRate, int id) {
        Vec3 travel() { return forward.scale(speed < 0 ? -1 : 1); }
        Motion advance(double ticks) {
            double angle = yawRate * ticks;
            double c = Math.cos(angle), s = Math.sin(angle);
            Vec3 nextForward = new Vec3(forward.x * c - forward.z * s, 0, forward.x * s + forward.z * c);
            Vec3 displacement = Math.abs(yawRate) < 1.0E-6
                    ? forward.scale(speed * ticks)
                    : new Vec3(speed * (forward.x * s + forward.z * (c - 1)) / yawRate, 0,
                        speed * (forward.x * (1 - c) + forward.z * s) / yawRate);
            return new Motion(center.add(displacement), nextForward, speed, yawRate, id);
        }
    }

    public static boolean mustStop(ServerLevel level, GroundworksDumpTruckEntity truck) {
        Motion own = motion(truck);
        if (Math.abs(own.speed()) < 0.001D) return false;
        for (GroundworksDumpTruckEntity other : level.getEntitiesOfClass(GroundworksDumpTruckEntity.class,
                truck.getBoundingBox().inflate(25), e -> e != truck && !e.isRemoved())) {
            if (Math.abs(truck.getY() - other.getY()) > 2.5D) continue;
            if (mustYield(own, motion(other))) return true;
        }
        return false;
    }

    private static Motion motion(GroundworksDumpTruckEntity truck) {
        double speed = truck.getTrafficDesiredSpeed();
        double rate = speed / 3.65D * Math.tan(Math.toRadians(truck.getMovementController().steerAngle()));
        return new Motion(truck.position(), truck.forwardVector(), speed, rate, truck.getId());
    }

    public static boolean mustYield(Motion own, Motion other) {
        // Even priority cannot authorize driving into a body already in the way.
        if (overlap(own.advance(3), other.advance(3), 0.25D)) return true;
        if (!conflict(own, other)) return false;
        if (Math.abs(other.speed()) < 0.001D) return true;
        Vec3 delta = other.center().subtract(own.center());
        double aligned = own.travel().dot(other.travel());
        if (aligned > 0.8D) {
            // Following traffic yields; the leader must not stop for its follower.
            return delta.dot(own.travel()) > 0;
        }
        if (aligned < -0.8D) {
            // A single occupied track has no safe passing lane. Both brake.
            return true;
        }
        // Minecraft yaw increases toward the driver's right. The entity's
        // historical rightVector() is the opposite, so derive this explicitly.
        Vec3 right = new Vec3(-own.travel().z, 0, own.travel().x);
        double fromRight = delta.dot(right);
        if (Math.abs(fromRight) > 0.25D) return fromRight > 0;
        return own.id() > other.id(); // deterministic tie, never two winners
    }

    private static boolean conflict(Motion a, Motion b) {
        for (int tick = 0; tick <= 50; tick += 2) {
            if (overlap(a.advance(tick), b.advance(tick), 0.4D)) return true;
        }
        return false;
    }

    public static boolean overlap(Motion a, Motion b, double margin) {
        Vec3 ar = new Vec3(-a.forward().z, 0, a.forward().x);
        Vec3 br = new Vec3(-b.forward().z, 0, b.forward().x);
        Vec3 delta = b.center().subtract(a.center());
        for (Vec3 axis : List.of(a.forward(), ar, b.forward(), br)) {
            double ra = (3.2D + margin) * Math.abs(axis.dot(a.forward())) + (1.4D + margin) * Math.abs(axis.dot(ar));
            double rb = (3.2D + margin) * Math.abs(axis.dot(b.forward())) + (1.4D + margin) * Math.abs(axis.dot(br));
            if (Math.abs(delta.dot(axis)) >= ra + rb) return false;
        }
        return true;
    }
}
