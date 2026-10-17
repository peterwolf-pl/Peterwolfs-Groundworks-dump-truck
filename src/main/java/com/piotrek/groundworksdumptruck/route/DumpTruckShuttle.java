package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworks.api.GroundworksApi;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Haul cycle without a recorded route.
 * Parks with the excavator on the left at bed height, backs into the
 * gradation screen, tips, and returns. Refuses any step that leaves the pad.
 */
public final class DumpTruckShuttle {

    public enum Result {
        ARMED,
        NO_EXCAVATOR,
        NO_SCREEN
    }

    private static final Identifier EXCAVATOR_ID =
            Identifier.fromNamespaceAndPath("pw_groundworks_excavator", "excavator");
    private static final Identifier SCREEN_ID =
            Identifier.fromNamespaceAndPath("pw_groundworks_gradation_screen", "gradation_screen");

    static final double SIDE_GAP = 4.0D;
    static final double BED_BEHIND = 1.8D;
    static final double STANDOFF = 8.0D;
    static final double LIP_BEHIND = 3.675D;
    static final double MAX_DROP = 0.75D;
    static final double SEARCH_RADIUS = 96.0D;

    private final GroundworksDumpTruckEntity truck;

    private boolean armed;
    private DumpTruckAutopilot.AutopilotState phase = DumpTruckAutopilot.AutopilotState.IDLE;
    private int trackedExcavatorId = -1;
    private int trackedScreenId = -1;
    private float approachYaw;
    private double padY = Double.NaN;
    private int stuckTicks;
    private int dumpTicks;
    private int creepCount;
    private Vec3 lastProgress = Vec3.ZERO;
    private List<ShuttlePathPlanner.Point> path = List.of();
    private int pathIndex;
    private Vec3 pathGoal;
    private Vec3 approachScreenPosition;
    private int retryTicks;
    private Component blocked = null;
    private boolean loadHornPending;

    public DumpTruckShuttle(GroundworksDumpTruckEntity truck) {
        this.truck = truck;
    }

    public boolean isArmed() {
        return armed;
    }

    public DumpTruckAutopilot.AutopilotState phase() {
        return armed ? phase : DumpTruckAutopilot.AutopilotState.IDLE;
    }

    public void disarm() {
        armed = false;
        phase = DumpTruckAutopilot.AutopilotState.IDLE;
        trackedExcavatorId = -1;
        trackedScreenId = -1;
        stuckTicks = 0;
        dumpTicks = 0;
        creepCount = 0;
        clearPath();
        blocked = null;
        loadHornPending = false;
        if (truck.level() instanceof ServerLevel level) DumpStationReservations.release(level, truck);
    }

    public Result arm(ServerLevel level) {
        Entity excavator = findNearest(level, truck.position(), EXCAVATOR_ID, 32.0D);
        if (excavator == null) {
            return Result.NO_EXCAVATOR;
        }
        trackedExcavatorId = excavator.getId();
        padY = groundY(level, excavator.getX(), excavator.getY(), excavator.getZ());
        Entity screen = findNearest(level, truck.position(), SCREEN_ID, SEARCH_RADIUS);
        trackedScreenId = screen != null ? screen.getId() : -1;
        armed = true;
        stuckTicks = 0;
        creepCount = 0;
        phase = DumpTruckAutopilot.AutopilotState.RETURNING;
        return screen == null ? Result.NO_SCREEN : Result.ARMED;
    }

    public void onHorn() {
        if (!armed || !truck.isBedFull()) return;
        if (phase == DumpTruckAutopilot.AutopilotState.RETURNING) {
            loadHornPending = true;
            return;
        }
        if (phase != DumpTruckAutopilot.AutopilotState.PARKED) return;
        loadHornPending = false;
        phase = DumpTruckAutopilot.AutopilotState.TO_SCREEN;
        clearPath();
        approachScreenPosition = null;
        stuckTicks = 0;
        creepCount = 0;
        dumpTicks = 0;
    }

    public void tick(ServerLevel level) {
        if (!armed) {
            return;
        }

        blocked = null;
        if (retryTicks > 0) retryTicks--;
        Entity excavator = resolve(level, trackedExcavatorId, EXCAVATOR_ID, 48.0D);
        if (excavator == null) {
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_excavator"));
            return;
        }
        trackedExcavatorId = excavator.getId();
        if (Math.abs(truck.getY() - padY) > 2.0D) {
            DumpStationReservations.release(level, truck);
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.off_grade"));
            return;
        }

        switch (phase) {
            case RETURNING -> tickReturn(level, excavator);
            case PARKED -> {
                if (horizontal(truck.position(), parkPosition(excavator.position(), excavator.getYRot())) > 2.0D) {
                    phase = DumpTruckAutopilot.AutopilotState.RETURNING;
                    clearPath();
                }
                hold(-0.5F);
            }
            case TO_SCREEN -> tickToScreen(level);
            case REVERSING -> tickReverse(level);
            case DUMPING -> tickDump(level);
            default -> phase = DumpTruckAutopilot.AutopilotState.PARKED;
        }
    }

    public Component status() {
        if (blocked != null) return Component.translatable("shuttle.pw_groundworks_dump_truck.blocked_prefix", blocked);
        return switch (phase) {
            case PARKED -> Component.translatable("shuttle.pw_groundworks_dump_truck.parked");
            case TO_SCREEN -> Component.translatable("shuttle.pw_groundworks_dump_truck.to_screen");
            case REVERSING -> Component.translatable("shuttle.pw_groundworks_dump_truck.reversing");
            case DUMPING -> Component.translatable("shuttle.pw_groundworks_dump_truck.dumping", truck.storedUnits());
            case RETURNING -> Component.translatable("shuttle.pw_groundworks_dump_truck.returning");
            default -> CommonComponents.EMPTY;
        };
    }

    private void tickReturn(ServerLevel level, Entity excavator) {
        Vec3 park = parkPosition(excavator.position(), excavator.getYRot());
        float parkYaw = excavator.getYRot();
        if (horizontal(truck.position(), dumpStationTarget(level)) > 12.0D && truck.getBedAngle() <= 1.0F)
            DumpStationReservations.release(level, truck);
        double ownDistance = horizontal(truck.position(), park);
        boolean occupied = !level.getEntitiesOfClass(GroundworksDumpTruckEntity.class,
                new AABB(park, park).inflate(5, 2, 5), other -> other != truck && !other.isRemoved()
                        && ownsLoadingSlot(ownDistance, truck.getId(), horizontal(other.position(), park), other.getId(),
                        !other.getAutopilot().isActive()
                                || other.getAutopilot().getState() == DumpTruckAutopilot.AutopilotState.PARKED)).isEmpty();
        if (occupied) {
            Vec3 queue = park.subtract(forwardVector(parkYaw).scale(12.0D));
            if (horizontal(truck.position(), queue) < 1.5D) hold(-0.5F);
            else navigate(level, queue, parkYaw);
            if (blocked == null) blocked = Component.translatable("shuttle.pw_groundworks_dump_truck.queue_load");
            return;
        }
        double dist = horizontal(truck.position(), park);
        if (dist < 1.6D && Math.abs(Mth.wrapDegrees(parkYaw - truck.getYRot())) < 12.0F) {
            truck.getMovementController().stopMotion();
            hold(-0.5F);
            phase = DumpTruckAutopilot.AutopilotState.PARKED;
            if (loadHornPending) onHorn();
            return;
        }
        navigate(level, park, parkYaw);
    }

    private void tickToScreen(ServerLevel level) {
        Entity screen = resolve(level, trackedScreenId, SCREEN_ID, SEARCH_RADIUS);
        if (screen == null) {
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_screen"));
            return;
        }
        trackedScreenId = screen.getId();
        if (!DumpStationReservations.acquire(level, truck, screen.position())) {
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.slot_occupied"));
            return;
        }
        if (approachScreenPosition == null || horizontal(approachScreenPosition, screen.position()) > 1.0D) {
            if (retryTicks > 0) {
                pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_access"));
                return;
            }
            clearPath();
            if (!selectApproach(level, screen)) {
                DumpStationReservations.release(level, truck);
                retryTicks = 40;
                pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_access"));
                return;
            }
            approachScreenPosition = screen.position();
        }
        Vec3 standoff = pose(screen.position(), approachYaw, STANDOFF);
        double dist = horizontal(truck.position(), standoff);
        float yawErr = Math.abs(Mth.wrapDegrees(approachYaw - truck.getYRot()));
        if (dist < 1.5D && yawErr < 10.0F && Math.abs(truck.getMovementController().forwardSpeed()) < 0.02F) {
            phase = DumpTruckAutopilot.AutopilotState.REVERSING;
            stuckTicks = 0;
            hold(-0.5F);
            return;
        }
        navigate(level, standoff, approachYaw);
    }

    private void tickReverse(ServerLevel level) {
        Entity screen = resolve(level, trackedScreenId, SCREEN_ID, SEARCH_RADIUS);
        if (screen == null) {
            hold(0.0F);
            return;
        }
        Vec3 lip = truck.getDumpLipWorldPosition();
        if (horizontal(lip, screen.position()) <= (creepCount > 0 ? 0.6D : 1.0D)) {
            truck.getMovementController().stopMotion();
            phase = DumpTruckAutopilot.AutopilotState.DUMPING;
            dumpTicks = 0;
            truck.setControlInputs(0.0F, 0.0F, 1.0F);
            return;
        }
        Vec3 pose = pose(screen.position(), approachYaw, LIP_BEHIND);
        driveToward(level, pose, true, screen);
    }

    private void tickDump(ServerLevel level) {
        dumpTicks++;
        if (truck.getBedAngle() < 8.0F && truck.storedUnits() <= 0) {
            phase = DumpTruckAutopilot.AutopilotState.RETURNING;
            stuckTicks = 0;
            hold(-0.5F);
            return;
        }
        if (truck.storedUnits() > 0) {
            if (dumpTicks > 400) blocked = Component.translatable("shuttle.pw_groundworks_dump_truck.screen_full");
            if (dumpTicks > 80 && truck.storedUnits() > 0 && creepCount < 2) {
                Entity screen = resolve(level, trackedScreenId, SCREEN_ID, SEARCH_RADIUS);
                if (screen != null && horizontal(truck.getDumpLipWorldPosition(), screen.position()) > 1.2D) {
                    creepCount++;
                    dumpTicks = 0;
                    phase = DumpTruckAutopilot.AutopilotState.REVERSING;
                    return;
                }
            }
            truck.setControlInputs(0.0F, 0.0F, 1.0F);
            return;
        }
        truck.setControlInputs(0.0F, 0.0F, -1.0F);
    }

    private void driveToward(ServerLevel level, Vec3 target, boolean reverse, Entity screen) {
        Vec3 delta = target.subtract(truck.position());
        float faceYaw = reverse
                ? (float) Math.toDegrees(Math.atan2(delta.x, -delta.z))
                : (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float yawErr = Mth.wrapDegrees(faceYaw - truck.getYRot());
        float steer = DumpTruckAutopilot.steeringInput(wheelAngle(delta, truck.getYRot()),
                truck.getMovementController().steerAngle());
        float speed = truck.getMovementController().forwardSpeed();

        Vec3 probe = probeAhead(reverse);
        if (!footprintHolds(level, probe, truck.getYRot())) {
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.edge_reverse"));
            noteStuck();
            return;
        }

        float desiredSpeed = (reverse ? -1 : 1) * (Math.abs(yawErr) > 28.0F ? 0.035F : 0.065F);
        truck.setControlInputs(DumpTruckAutopilot.speedInput(desiredSpeed, speed), steer, -0.5F);
        noteStuck();
    }

    static float wheelAngle(Vec3 targetDelta, float yaw) {
        double lateral = targetDelta.dot(leftVector(yaw));
        return Mth.clamp((float) Math.toDegrees(Math.atan(-2 * 3.65D * lateral
                / Math.max(0.25D, targetDelta.horizontalDistanceSqr()))), -32.0F, 32.0F);
    }

    private void clearPath() {
        path = List.of();
        pathGoal = null;
        pathIndex = 0;
        retryTicks = 0;
    }

    private void pause(Component reason) {
        blocked = reason;
        hold(-0.5F);
        if (phase != DumpTruckAutopilot.AutopilotState.REVERSING
                && phase != DumpTruckAutopilot.AutopilotState.DUMPING
                && truck.level() instanceof ServerLevel level) DumpStationReservations.release(level, truck);
    }

    static boolean ownsLoadingSlot(double ownDistance, int ownId, double otherDistance, int otherId, boolean parked) {
        if (otherDistance >= 5.0D) return false;
        if (parked) return true;
        return otherDistance < ownDistance - 0.25D
                || (Math.abs(otherDistance - ownDistance) <= 0.25D && otherId < ownId);
    }

    private Vec3 dumpStationTarget(ServerLevel level) {
        Entity screen = level.getEntity(trackedScreenId);
        return screen == null ? truck.position().add(100, 0, 0) : screen.position();
    }

    private boolean footprintHolds(ServerLevel level, Vec3 center, float yaw) {
        Vec3 forward = forwardVector(yaw), left = leftVector(yaw);
        for (double longitudinal : new double[]{-3.2D, 0, 3.2D}) {
            for (double lateral : new double[]{-1.4D, 1.4D}) {
                if (!holdsLevel(level, center.add(forward.scale(longitudinal)).add(left.scale(lateral)), null))
                    return false;
            }
        }
        return true;
    }

    private void navigate(ServerLevel level, Vec3 target, float yaw) {
        target = new Vec3(target.x, padY, target.z);
        if (pathGoal != null && horizontal(pathGoal, target) > 1.0D) clearPath();
        if (path.isEmpty()) {
            if (retryTicks > 0) { pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_route")); return; }
            for (var candidate : ShuttlePathPlanner.candidates(truck.position(), truck.getYRot(), target, yaw)) {
                if (candidate.points().stream().allMatch(p -> footprintHolds(level, p.position(), p.yaw()))) {
                    path = candidate.points();
                    pathGoal = target;
                    pathIndex = 0;
                    break;
                }
            }
            if (path.isEmpty()) {
                retryTicks = 40;
                pause(Component.translatable("shuttle.pw_groundworks_dump_truck.no_turning_space"));
                return;
            }
        }
        int nearest = pathIndex;
        double best = Double.MAX_VALUE;
        for (int i = pathIndex; i < Math.min(path.size(), pathIndex + 14); i++) {
            double distance = horizontal(truck.position(), path.get(i).position());
            if (distance < best) { best = distance; nearest = i; }
        }
        pathIndex = nearest;
        Vec3 aim = path.get(Math.min(path.size() - 1, pathIndex + 7)).position();
        if (!footprintHolds(level, probeAhead(false), truck.getYRot())) {
            clearPath();
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.edge_forward"));
            return;
        }
        double remaining = horizontal(truck.position(), target);
        if (remaining < 1.0D && Math.abs(Mth.wrapDegrees(yaw - truck.getYRot())) < 10.0F) {
            hold(-0.5F);
            return;
        }
        Vec3 delta = aim.subtract(truck.position());
        float angle = wheelAngle(delta, truck.getYRot());
        float desiredSpeed = remaining < 4.0D ? 0.055F : 0.12F;
        truck.setControlInputs(DumpTruckAutopilot.speedInput(desiredSpeed, truck.getMovementController().forwardSpeed()),
                DumpTruckAutopilot.steeringInput(angle, truck.getMovementController().steerAngle()), -0.5F);
        noteStuck();
    }

    private void hold(float bed) {
        truck.getMovementController().stopMotion();
        truck.setControlInputs(0.0F, 0.0F, bed);
    }

    private void noteStuck() {
        Vec3 now = truck.position();
        if (horizontal(now, lastProgress) < 0.04D) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastProgress = now;
        }
        if (stuckTicks > 160) {
            clearPath();
            retryTicks = 40;
            pause(Component.translatable("shuttle.pw_groundworks_dump_truck.passage_blocked"));
        }
    }

    private boolean selectApproach(ServerLevel level, Entity screen) {
        float bestYaw = screen.getYRot();
        double best = Double.MAX_VALUE;
        boolean found = false;
        ShuttlePathPlanner.Path bestPath = null;
        Vec3 bestGoal = null;
        for (int step = 0; step < 8; step++) {
            float yaw = Mth.wrapDegrees(screen.getYRot() + step * 45.0F);
            Vec3 standoff = pose(screen.position(), yaw, STANDOFF);
            Vec3 lipPose = pose(screen.position(), yaw, LIP_BEHIND);
            if (!footprintHolds(level, standoff, yaw) || !footprintHolds(level, lipPose, yaw)) {
                continue;
            }
            if (!segmentHolds(level, standoff, lipPose, screen)) {
                continue;
            }
            Vec3 goal = new Vec3(standoff.x, padY, standoff.z);
            for (var candidate : ShuttlePathPlanner.candidates(truck.position(), truck.getYRot(), goal, yaw)) {
                if (candidate.length() >= best) break;
                if (candidate.points().stream().allMatch(p -> footprintHolds(level, p.position(), p.yaw()))) {
                    best = candidate.length();
                    bestYaw = yaw;
                    bestPath = candidate;
                    bestGoal = goal;
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            approachYaw = bestYaw;
            path = bestPath.points();
            pathGoal = bestGoal;
            pathIndex = 0;
        }
        return found;
    }

    private boolean segmentHolds(ServerLevel level, Vec3 from, Vec3 to, Entity screen) {
        double dist = horizontal(from, to);
        int steps = Math.max(1, (int) Math.ceil(dist));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 point = new Vec3(
                    Mth.lerp(t, from.x, to.x),
                    from.y,
                    Mth.lerp(t, from.z, to.z)
            );
            if (!footprintHolds(level, point, approachYawFor(from, to))) {
                return false;
            }
        }
        return true;
    }

    private static float approachYawFor(Vec3 from, Vec3 to) {
        Vec3 delta = from.subtract(to);
        return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    }

    private boolean holdsLevel(ServerLevel level, Vec3 point, Entity screen) {
        double surface = columnSurface(level, point.x, padY, point.z);
        if (Double.isNaN(surface)) {
            return false;
        }
        return surface >= padY - MAX_DROP && surface <= padY + 1.25D;
    }

    private Vec3 probeAhead(boolean reverse) {
        Vec3 forward = truck.forwardVector();
        double sign = reverse ? -2.2D : 2.4D;
        return truck.position().add(forward.scale(sign));
    }

    private Entity resolve(ServerLevel level, int id, Identifier typeId, double radius) {
        if (id >= 0) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && typeId.equals(EntityType.getKey(entity.getType()))) {
                return entity;
            }
        }
        return findNearest(level, truck.position(), typeId, radius);
    }

    private static Entity findNearest(ServerLevel level, Vec3 origin, Identifier typeId, double radius) {
        AABB box = new AABB(origin, origin).inflate(radius, 16.0D, radius);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity) null, box, candidate ->
                candidate.isAlive() && typeId.equals(EntityType.getKey(candidate.getType())))) {
            double dist = entity.position().distanceToSqr(origin);
            if (dist < bestDist) {
                best = entity;
                bestDist = dist;
            }
        }
        return best;
    }

    static Vec3 parkPosition(Vec3 excavator, float yaw) {
        return excavator
                .add(leftVector(yaw).scale(-SIDE_GAP))
                .add(forwardVector(yaw).scale(BED_BEHIND));
    }

    static Vec3 pose(Vec3 screen, float yaw, double distance) {
        return screen.add(forwardVector(yaw).scale(distance));
    }

    static Vec3 forwardVector(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    static Vec3 leftVector(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double groundY(ServerLevel level, double x, double y, double z) {
        double surface = columnSurface(level, x, y, z);
        return Double.isNaN(surface) ? y : surface;
    }

    /**
     * Surface under a point, or NaN when the column is a drop deeper than 6 blocks.
     * {@code sampleGroundHeight} reports the vehicle Y when it finds nothing, which
     * would hide a cliff.
     */
    private static double columnSurface(ServerLevel level, double x, double y, double z) {
        BlockPos start = BlockPos.containing(x, y + 0.8D, z);
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos check = start.below(dy);
            BlockState state = level.getBlockState(check);
            double sampled = GroundworksApi.getSurfaceWorldY(level, check, x, z);
            if (Double.isFinite(sampled)) return sampled;
            var shape = state.getCollisionShape(level, check);
            if (!shape.isEmpty()) {
                double localX = x - check.getX(), localZ = z - check.getZ();
                double highest = Double.NaN;
                for (AABB box : shape.toAabbs()) {
                    if (localX >= box.minX && localX <= box.maxX && localZ >= box.minZ && localZ <= box.maxZ)
                        highest = Double.isNaN(highest) ? box.maxY : Math.max(highest, box.maxY);
                }
                if (Double.isFinite(highest)) return check.getY() + highest;
            }
        }
        return Double.NaN;
    }
}
