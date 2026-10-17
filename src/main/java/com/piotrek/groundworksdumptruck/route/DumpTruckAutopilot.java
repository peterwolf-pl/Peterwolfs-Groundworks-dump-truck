package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Autonomous route playback, convoy queue management, and bed tipping controller.
 *
 * <p>Implements:
 * 1. Recording haul cycles with tipping events.
 * 2. Route following via pure pursuit path tracking.
 * 3. Excavator long horn ("pełna kipa") trigger for departure from loading station.
 * 4. Convoy queue maintenance: holds 3-4 block distance behind preceding truck,
 *    resumes motion together with it, and halts second truck at the loading station.</p>
 */
public class DumpTruckAutopilot {

    public enum AutopilotState {
        IDLE,
        RECORDING,
        WAITING_FOR_LOAD,
        DRIVING,
        REVERSING,
        WAITING_IN_QUEUE,
        DUMPING,
        PARKED,
        TO_SCREEN,
        RETURNING
    }

    private final GroundworksDumpTruckEntity truck;
    private final DumpTruckShuttle shuttle;

    private AutopilotState state = AutopilotState.IDLE;
    private String routeName = "";
    private DumpTruckRoute activeRoute;
    private int currentWaypointIndex = 0;
    private boolean loopEnabled = true;
    private boolean[] reverseFlags = new boolean[0];

    // Recording buffer
    private final List<RouteWaypoint> recordedWaypoints = new ArrayList<>();
    private String recordingName = "default";
    private int recordTickInterval = 0;

    // Queue & Convoy tracking
    private int queuePrecedingTruckId = -1;
    private AutopilotState queueResumeState = AutopilotState.DRIVING;
    private boolean waitingForDumpStation;
    private Vec3 reservedDumpPosition;
    private boolean returningReverse;
    private int dumpStationWaitTicks = 0;
    private int dumpingStateTicks = 0;

    // Pure pursuit parameters
    private static final double WAYPOINT_REACH_DIST = 0.5D;
    private static final double LOOKAHEAD_DIST = 3.2D;
    private static final double MIN_QUEUE_GAP = 3.5D; // Stop when gap <= 3.5m (within 3-4 blocks)
    private static final double RESUME_QUEUE_GAP = 4.0D; // Resume when gap > 4.0m
    private static final int MAX_DUMP_HOLD_TICKS = 140;

    public DumpTruckAutopilot(GroundworksDumpTruckEntity truck) {
        this.truck = truck;
        this.shuttle = new DumpTruckShuttle(truck);
    }

    public AutopilotState getState() {
        return shuttle.isArmed() ? shuttle.phase() : state;
    }

    public DumpTruckShuttle.Result startShuttle(ServerLevel level) {
        stop();
        DumpTruckShuttle.Result result = shuttle.arm(level);
        if (result != DumpTruckShuttle.Result.NO_EXCAVATOR) {
            this.state = shuttle.phase();
            this.routeName = "shuttle";
        }
        return result;
    }

    public String getRouteName() {
        return activeRoute != null ? activeRoute.name() : routeName;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public boolean isLoopEnabled() {
        return loopEnabled;
    }

    public boolean isActive() {
        return shuttle.isArmed() || state != AutopilotState.IDLE;
    }

    public boolean isRecording() {
        return state == AutopilotState.RECORDING;
    }

    public void startRecording(String name) {
        shuttle.disarm();
        this.state = AutopilotState.RECORDING;
        this.recordingName = (name != null && !name.isBlank()) ? name : "default";
        this.recordedWaypoints.clear();
        this.recordTickInterval = 0;
        this.activeRoute = null;
        this.queuePrecedingTruckId = -1;
        GroundworksDumpTruckMod.LOGGER.info("Truck #{} started recording route '{}'", truck.getId(), recordingName);
    }

    public DumpTruckRoute stopRecording(MinecraftServer server) {
        if (state != AutopilotState.RECORDING) {
            return null;
        }

        DumpTruckRoute route = new DumpTruckRoute(recordingName, recordedWaypoints);
        if (server != null && !route.isEmpty()) {
            DumpTruckRouteManager.saveRoute(server, route);
        }

        this.state = AutopilotState.IDLE;
        this.activeRoute = route;
        this.routeName = route.name();
        GroundworksDumpTruckMod.LOGGER.info("Truck #{} stopped recording: saved route '{}' with {} waypoints",
                truck.getId(), route.name(), route.size());
        return route;
    }

    public void startPlayback(DumpTruckRoute route, boolean loop, boolean forceStart) {
        if (route == null || route.isEmpty()) {
            return;
        }
        if (truck.level() instanceof ServerLevel level) DumpStationReservations.release(level, truck);
        reservedDumpPosition = null;
        waitingForDumpStation = false;
        shuttle.disarm();
        this.activeRoute = route;
        this.routeName = route.name();
        this.loopEnabled = loop;
        this.queuePrecedingTruckId = -1;
        this.dumpingStateTicks = 0;
        this.reverseFlags = buildReverseFlags(route);

        RouteWaypoint startWp = route.get(0);
        double distToStart = startWp != null ? startWp.horizontalDistanceTo(truck.position()) : Double.MAX_VALUE;

        // Queue members converge on the shared station, never redefine it.
        if (distToStart < 25.0D) {
            this.currentWaypointIndex = 0;
            if (!forceStart && !truck.isBedFull()) {
                this.state = getAdjustedWaypoint(0).horizontalDistanceTo(truck.position()) <= WAYPOINT_REACH_DIST
                        ? AutopilotState.WAITING_FOR_LOAD : AutopilotState.RETURNING;
                GroundworksDumpTruckMod.LOGGER.info("Truck #{} armed with route '{}'. Waiting at loading station for full bed.",
                        truck.getId(), route.name());
                return;
            }
        } else {
            this.currentWaypointIndex = findClosestWaypointIndex(truck.position());
        }

        if (distToStart < 25.0D) this.currentWaypointIndex = findDepartureIndex();
        this.state = AutopilotState.DRIVING;
        GroundworksDumpTruckMod.LOGGER.info("Truck #{} starting route '{}' at waypoint #{}",
                truck.getId(), route.name(), currentWaypointIndex);
    }

    public void stop() {
        if (truck.level() instanceof ServerLevel level) DumpStationReservations.release(level, truck);
        reservedDumpPosition = null;
        waitingForDumpStation = false;
        shuttle.disarm();
        this.state = AutopilotState.IDLE;
        this.activeRoute = null;
        this.routeName = "";
        this.currentWaypointIndex = 0;
        this.queuePrecedingTruckId = -1;
        truck.setControlInputs(0.0F, 0.0F, 0.0F);
        truck.getMovementController().stopMotion();
    }

    /**
     * Called when the excavator blows its long horn ("pełna kipa").
     */
    public void onExcavatorHorn(Vec3 hornOrigin) {
        if (shuttle.isArmed()) {
            shuttle.onHorn();
            this.state = shuttle.phase();
            return;
        }
        if (state == AutopilotState.WAITING_FOR_LOAD) {
            GroundworksDumpTruckMod.LOGGER.info("Truck #{} heard excavator long horn! Departing from loading point.", truck.getId());
            this.state = AutopilotState.DRIVING;
            this.currentWaypointIndex = findDepartureIndex();
        }
    }

    /**
     * Called when the dump truck bed becomes 100% full.
     */
    public void onBedFull() {
        if (state == AutopilotState.WAITING_FOR_LOAD) {
            GroundworksDumpTruckMod.LOGGER.info("Truck #{} is full (pełna kipa)! Departing from loading point.", truck.getId());
            this.state = AutopilotState.DRIVING;
            this.currentWaypointIndex = findDepartureIndex();
        }
    }

    public void tick(ServerLevel level) {
        if (shuttle.isArmed()) {
            shuttle.tick(level);
            this.state = shuttle.phase();
            this.routeName = "shuttle";
            return;
        }
        releaseClearedDumpStation(level);
        switch (state) {
            case IDLE -> {
                // Nothing to do
            }
            case RECORDING -> tickRecording(level);
            case WAITING_FOR_LOAD -> tickWaitingForLoad(level);
            case WAITING_IN_QUEUE -> tickWaitingInQueue(level);
            case DRIVING -> tickDriving(level);
            case REVERSING -> tickReversing(level);
            case DUMPING -> tickDumpingState(level);
            case RETURNING -> tickReturningToLoad(level);
            case PARKED, TO_SCREEN -> {
            }
        }
    }

    private void tickRecording(ServerLevel level) {
        Vec3 pos = truck.position();
        float yaw = truck.getYRot();
        float pitch = truck.getXRot();
        float roll = truck.getMovementController().vehicleRoll();
        float speed = truck.getMovementController().forwardSpeed();
        float bedAngle = truck.getBedAngle();
        boolean dumping = bedAngle > GroundworksDumpTruckEntity.DUMP_THRESHOLD_ANGLE || truck.isDumping();
        boolean reverse = speed < -0.005F || truck.getInputThrottle() < -0.05F;
        if (!recordedWaypoints.isEmpty()) {
            RouteWaypoint prev = recordedWaypoints.get(recordedWaypoints.size() - 1);
            double mdx = pos.x - prev.x();
            double mdz = pos.z - prev.z();
            double mlen = Math.hypot(mdx, mdz);
            if (mlen > 0.15D) {
                double yawRad = Math.toRadians(yaw);
                double travelDot = (mdx / mlen) * -Math.sin(yawRad) + (mdz / mlen) * Math.cos(yawRad);
                if (travelDot < -0.35D) {
                    reverse = true;
                }
            }
        }

        recordTickInterval++;

        boolean shouldRecord = false;
        if (recordedWaypoints.isEmpty()) {
            shouldRecord = true;
        } else {
            RouteWaypoint last = recordedWaypoints.get(recordedWaypoints.size() - 1);
            double dist = last.distanceTo(pos);
            float dYaw = Math.abs(Mth.wrapDegrees(yaw - last.yaw()));
            float dBed = Math.abs(bedAngle - last.bedAngle());
            boolean dumpChanged = dumping != last.dumping();
            boolean reverseChanged = reverse != last.isReverse();

            if (dist >= 0.25D || dYaw >= 2.5F || dBed >= 1.0F || dumpChanged || reverseChanged || recordTickInterval >= 10) {
                shouldRecord = true;
            }
        }

        if (shouldRecord) {
            recordTickInterval = 0;
            recordedWaypoints.add(new RouteWaypoint(
                    pos.x, pos.y, pos.z,
                    yaw, pitch, roll,
                    speed, bedAngle, dumping, reverse
            ));
        }
    }

    private void tickWaitingForLoad(ServerLevel level) {
        // Truck stays stopped at loading spot, bed firmly down, waiting for excavator
        truck.setControlInputs(0.0F, 0.0F, -0.5F);
        truck.getMovementController().stopMotion();

        // Check if bed reached capacity ("pełna kipa")
        if (truck.isBedFull()) {
            onBedFull();
        }
    }

    private void tickWaitingInQueue(ServerLevel level) {
        if (waitingForDumpStation) {
            if (reserveDumpStation(level)) {
                waitingForDumpStation = false;
                state = queueResumeState;
            }
            truck.setControlInputs(0.0F, 0.0F, -0.5F);
            truck.getMovementController().stopMotion();
            return;
        }
        // Truck holds position in queue, 3-4 blocks behind preceding truck
        truck.setControlInputs(0.0F, 0.0F, -0.5F);
        truck.getMovementController().stopMotion();

        Entity precedingEntity = level.getEntity(queuePrecedingTruckId);
        if (!(precedingEntity instanceof GroundworksDumpTruckEntity precedingTruck) || precedingTruck.isRemoved()) {
            // Preceding truck left or disappeared -> resume moving
            this.state = queueResumeState;
            this.queuePrecedingTruckId = -1;
            return;
        }

        double gap = truck.calculateBumperGap(precedingTruck);
        // "i wznawiaj ruch razem z poprzedzajaca":
        // When preceding truck drives forward and gap exceeds 4 blocks, resume movement!
        if (gap > RESUME_QUEUE_GAP || findTruckAhead(level) != precedingTruck) {
            this.state = queueResumeState;
            this.queuePrecedingTruckId = -1;
        }
    }

    private void waitBehind(GroundworksDumpTruckEntity preceding, AutopilotState resume) {
        queueResumeState = resume;
        waitingForDumpStation = false;
        queuePrecedingTruckId = preceding.getId();
        state = AutopilotState.WAITING_IN_QUEUE;
        truck.setControlInputs(0, 0, -0.5F);
        truck.getMovementController().stopMotion();
    }

    private Vec3 dumpStationPosition() {
        if (activeRoute == null) return null;
        for (RouteWaypoint wp : activeRoute.waypoints()) {
            if (wp.dumping() || wp.bedAngle() > 18.0F) return wp.pos();
        }
        return null;
    }

    private int lastDumpWaypointIndex() {
        if (activeRoute == null) return -1;
        for (int i = activeRoute.size() - 1; i >= 0; i--) {
            RouteWaypoint wp = activeRoute.get(i);
            if (wp.dumping() || wp.bedAngle() > 18.0F) return i;
        }
        return -1;
    }

    private boolean reserveDumpStation(ServerLevel level) {
        // Residual cargo on the return leg does not grant ownership of the bay.
        if (currentWaypointIndex > lastDumpWaypointIndex() && state != AutopilotState.DUMPING) return true;
        Vec3 station = dumpStationPosition();
        if (station == null) return true;
        if (!DumpStationReservations.acquire(level, truck, station)) return false;
        reservedDumpPosition = station;
        return true;
    }

    private void waitForDumpStation(AutopilotState resume) {
        queueResumeState = resume;
        waitingForDumpStation = true;
        queuePrecedingTruckId = -1;
        state = AutopilotState.WAITING_IN_QUEUE;
        truck.setControlInputs(0, 0, -0.5F);
        truck.getMovementController().stopMotion();
    }

    private void releaseClearedDumpStation(ServerLevel level) {
        if (reservedDumpPosition != null && (Math.abs(truck.getY() - reservedDumpPosition.y) > 4.0D
                || ((truck.storedUnits() == 0 || currentWaypointIndex > lastDumpWaypointIndex())
                && truck.getBedAngle() <= 1.0F
                && reservedDumpPosition.subtract(truck.position()).horizontalDistance() > 12.0D))) {
            DumpStationReservations.release(level, truck);
            reservedDumpPosition = null;
        }
    }

    private void tickReturningToLoad(ServerLevel level) {
        if (activeRoute == null || activeRoute.isEmpty()) { stop(); return; }
        RouteWaypoint station = getAdjustedWaypoint(0);
        Vec3 delta = station.pos().subtract(truck.position());
        returningReverse = delta.dot(truck.forwardVector()) < -0.1D;
        GroundworksDumpTruckEntity preceding = findTruckAhead(level);
        if (preceding != null && truck.calculateBumperGap(preceding) <= MIN_QUEUE_GAP) {
            waitBehind(preceding, AutopilotState.RETURNING);
            return;
        }
        releaseClearedDumpStation(level);
        if (station.horizontalDistanceTo(truck.position()) <= WAYPOINT_REACH_DIST) {
            truck.getMovementController().stopMotion();
            truck.setControlInputs(0, 0, -0.5F);
            currentWaypointIndex = 0;
            state = AutopilotState.WAITING_FOR_LOAD;
            return;
        }
        float desiredSpeed = (returningReverse ? -1 : 1) * 0.07F;
        truck.setControlInputs(speedInput(desiredSpeed, truck.getMovementController().forwardSpeed()),
                steeringInput(targetWheelAngle(station), truck.getMovementController().steerAngle()), -0.5F);
    }

    private void tickDriving(ServerLevel level) {
        if (activeRoute == null || activeRoute.isEmpty()) {
            this.state = AutopilotState.IDLE;
            return;
        }

        // 1. Check for preceding truck ahead in lane (Queue / Convoy Handling)
        GroundworksDumpTruckEntity frontTruck = findTruckAhead(level);
        float speedLimit = 1.0F;

        if (frontTruck != null) {
            double gap = truck.calculateBumperGap(frontTruck);
            // "trzymaj od niej odstep na 3,4 bloki"
            if (gap <= MIN_QUEUE_GAP) {
                // Gap dropped to 3.5m or less -> enter waiting in queue
                waitBehind(frontTruck, AutopilotState.DRIVING);
                truck.setControlInputs(0.0F, 0.0F, -0.5F);
                truck.getMovementController().stopMotion();
                return;
            } else if (gap <= RESUME_QUEUE_GAP + 0.5D) {
                // Smoothly throttle down to match preceding truck's pace
                speedLimit = 0.35F;
            }
        }

        // 2. Check current waypoint
        RouteWaypoint currentWp = getAdjustedWaypoint(currentWaypointIndex);
        if (currentWp == null) {
            this.state = AutopilotState.IDLE;
            return;
        }

        releaseClearedDumpStation(level);
        // Reserve before entering the turning/reversing envelope, not only
        // at the lip: a queue inside that envelope blocks the truck leaving it.
        double approachGate = Math.max(0.0D, activeRoute.getDistanceFromStart(
                Math.min(firstManeuverIndex(), activeRoute.size() - 1)) - 20.0D);
        if (truck.storedUnits() > 0 && dumpStationPosition() != null
                && activeRoute.getDistanceFromStart(currentWaypointIndex) >= approachGate
                && !reserveDumpStation(level)) {
            waitForDumpStation(AutopilotState.DRIVING);
            return;
        }

        // Reverse segment: stop completely, then back into the hopper.
        // Never chase a reverse point forwards — that is what produced circles.
        if (waypointIsReverse(currentWaypointIndex)) {
            float speed = truck.getMovementController().forwardSpeed();
            if (speed > 0.012F) {
                truck.setControlInputs(-1.0F, 0.0F, -0.5F);
                return;
            }
            truck.getMovementController().stopMotion();
            this.state = AutopilotState.REVERSING;
            truck.setControlInputs(0.0F, 0.0F, -0.5F);
            return;
        }

        // "wazne zeby rejestrował kiedy jest wykipowywany":
        // Check if current waypoint is inside a dumping zone and we have cargo to dump
        if ((currentWp.dumping() || currentWp.bedAngle() > 18.0F) && truck.storedUnits() > 0
                && currentWp.horizontalDistanceTo(truck.position()) <= WAYPOINT_REACH_DIST) {
            this.state = AutopilotState.DUMPING;
            this.dumpingStateTicks = 0;
            truck.setControlInputs(0.0F, 0.0F, 1.0F);
            truck.getMovementController().stopMotion();
            return;
        }

        // Catch up with dense samples without skipping gear changes or tipping.
        double horizDist = currentWp.horizontalDistanceTo(truck.position());
        int pointsAdvanced = 0;
        while ((horizDist < WAYPOINT_REACH_DIST || passedCurrentSegment())
                && pointsAdvanced++ < activeRoute.size()) {
            currentWaypointIndex++;

            if (currentWaypointIndex >= activeRoute.size()) {
                // Reached end of recorded route
                if (loopEnabled) {
                    currentWaypointIndex = 0;

                    // "wazne zebys zauwazył kiedy ciezarowka jest pelna i ma jechac a jak ma sie zachowac druga z kolejki"
                    // If returning to loading station and empty, stop under excavator and wait for next load!
                    if (truck.storedUnits() <= 0) {
                        // Finish docking at the shared, possibly advanced station.
                        this.state = AutopilotState.RETURNING;
                        truck.setControlInputs(0.0F, 0.0F, -0.5F);
                        truck.getMovementController().stopMotion();
                        return;
                    }
                } else {
                    stop();
                    return;
                }
            }
            currentWp = getAdjustedWaypoint(currentWaypointIndex);
            if (currentWp == null || waypointIsReverse(currentWaypointIndex)
                    || ((currentWp.dumping() || currentWp.bedAngle() > 18.0F) && truck.storedUnits() > 0)) {
                truck.setControlInputs(0.0F, 0.0F, -0.5F);
                return;
            }
            horizDist = currentWp.horizontalDistanceTo(truck.position());
        }
        applyRecordedDrive(currentWp, false, speedLimit);
    }

    private void tickReversing(ServerLevel level) {
        if (activeRoute == null || activeRoute.isEmpty()) {
            this.state = AutopilotState.IDLE;
            return;
        }

        RouteWaypoint currentWp = getAdjustedWaypoint(currentWaypointIndex);
        if (currentWp == null) {
            this.state = AutopilotState.IDLE;
            return;
        }

        if (truck.storedUnits() > 0 && !reserveDumpStation(level)) {
            waitForDumpStation(AutopilotState.REVERSING);
            return;
        }
        GroundworksDumpTruckEntity preceding = findTruckAhead(level);
        if (preceding != null && truck.calculateBumperGap(preceding) <= MIN_QUEUE_GAP) {
            waitBehind(preceding, AutopilotState.REVERSING);
            return;
        }

        float speed = truck.getMovementController().forwardSpeed();
        if (speed > 0.012F) {
            truck.setControlInputs(-1.0F, 0.0F, -0.5F);
            return;
        }

        if (!waypointIsReverse(currentWaypointIndex)) {
            if (Math.abs(speed) > 0.012F) {
                truck.setControlInputs(0.8F, 0.0F, -0.5F);
                return;
            }
            truck.getMovementController().stopMotion();
            this.state = AutopilotState.DRIVING;
            truck.setControlInputs(0.0F, 0.0F, -0.5F);
            return;
        }

        if ((currentWp.dumping() || currentWp.bedAngle() > 18.0F) && truck.storedUnits() > 0
                && currentWp.horizontalDistanceTo(truck.position()) <= WAYPOINT_REACH_DIST) {
            if (Math.abs(speed) > 0.008F) {
                truck.setControlInputs(0.6F, 0.0F, 0.0F);
                return;
            }
            truck.getMovementController().stopMotion();
            this.state = AutopilotState.DUMPING;
            this.dumpingStateTicks = 0;
            truck.setControlInputs(0.0F, 0.0F, 1.0F);
            return;
        }

        double horizDist = currentWp.horizontalDistanceTo(truck.position());
        if (horizDist < WAYPOINT_REACH_DIST || passedCurrentSegment()) {
            currentWaypointIndex++;
            if (currentWaypointIndex >= activeRoute.size()) {
                if (!loopEnabled) {
                    stop();
                    return;
                }
                currentWaypointIndex = 0;
                this.state = truck.storedUnits() == 0 ? AutopilotState.RETURNING : AutopilotState.DRIVING;
                truck.getMovementController().stopMotion();
                truck.setControlInputs(0.0F, 0.0F, -0.5F);
                return;
            }
            currentWp = getAdjustedWaypoint(currentWaypointIndex);
            if (currentWp == null || !waypointIsReverse(currentWaypointIndex)) {
                truck.setControlInputs(0.0F, 0.0F, -0.5F);
                return;
            }
        }

        applyRecordedDrive(currentWp, true, 1.0F);
    }

    /** Follow a geometric lookahead within the current gear/tipping segment. */
    private void applyRecordedDrive(RouteWaypoint wp, boolean reverse, float speedLimit) {
        RouteWaypoint target = getLookaheadWaypoint(2.0D);
        float yawError = Mth.wrapDegrees(wp.yaw() - truck.getYRot());
        float targetAngle = targetWheelAngle(target);
        float steer = steeringInput(targetAngle, truck.getMovementController().steerAngle());

        float speed = truck.getMovementController().forwardSpeed();
        float desiredSpeed = Math.max(0.04F, Math.abs(wp.speed()));
        desiredSpeed = Math.min(desiredSpeed, reverse ? 0.14F : 0.25F) * speedLimit;
        if (Math.abs(yawError) > 30.0F || (!reverse && nextIsReverseSoon())) {
            desiredSpeed = Math.min(desiredSpeed, 0.08F);
        }
        float throttle = speedInput(reverse ? -desiredSpeed : desiredSpeed, speed);
        truck.setControlInputs(throttle, steer, -0.5F);
        if (truck.tickCount % 100 == 0) {
            GroundworksDumpTruckMod.LOGGER.info(
                    "Route replay truck={} route={} point={}/{} reverse={} distance={} yawError={} wheelAngle={} targetAngle={} speed={}",
                    truck.getId(), getRouteName(), currentWaypointIndex, activeRoute.size(), reverse,
                    wp.horizontalDistanceTo(truck.position()), yawError,
                    truck.getMovementController().steerAngle(), targetAngle, speed);
        }
    }

    private float targetWheelAngle(RouteWaypoint target) {
        double dx = target.x() - truck.getX();
        double dz = target.z() - truck.getZ();
        Vec3 left = truck.rightVector();
        double lateral = dx * left.x + dz * left.z;
        double distanceSquared = Math.max(0.25D, dx * dx + dz * dz);
        return Mth.clamp((float) Math.toDegrees(Math.atan(-2.0D * 3.65D * lateral / distanceSquared)), -32.0F, 32.0F);
    }

    static float speedInput(float targetSpeed, float currentSpeed) {
        if (Math.abs(currentSpeed - targetSpeed) < 0.005F) return 0.0F;
        // Small positive throttle is acceleration, not a lower speed limit.
        if (targetSpeed * currentSpeed < 0.0F
                || Math.abs(currentSpeed) > Math.abs(targetSpeed) + 0.005F) {
            return currentSpeed > 0.0F ? -1.0F : 1.0F;
        }
        return targetSpeed > 0.0F ? 0.5F : -0.5F;
    }

    // step() integrates the input into the wheel angle; it does not accept an angle.
    static float steeringInput(float targetAngle, float currentAngle) {
        float error = targetAngle - currentAngle;
        if (Math.abs(targetAngle) < 0.01F) {
            return 0.0F;
        }
        float input = Mth.clamp(error
                / com.piotrek.groundworksdumptruck.vehicle.DumpTruckMovementController.STEER_SPEED, -1.0F, 1.0F);
        // Neutral recenters the wheels, so hold a nonzero target with tiny corrections.
        return Math.abs(input) <= 0.05F ? Math.copySign(0.0501F, error) : input;
    }

    static float recordedSteer(float yawError, double lateral, boolean reverse) {
        float yawSteer = yawError / 12.0F;
        if (reverse) {
            yawSteer = -yawSteer;
        }
        float lateralSteer = (float) (-lateral / 3.0D);
        return Mth.clamp(yawSteer + lateralSteer, -1.0F, 1.0F);
    }

    private void tickDumpingState(ServerLevel level) {
        if (!reserveDumpStation(level)) {
            waitForDumpStation(AutopilotState.DUMPING);
            return;
        }
        // Vehicle is stopped at dump spot
        truck.getMovementController().stopMotion();
        dumpingStateTicks++;

        float bedAngle = truck.getBedAngle();

        if (truck.storedUnits() > 0) {
            // Keep raising bed and unloading
            truck.setControlInputs(0.0F, 0.0F, 1.0F);
        } else {
            // Never drive away with undelivered cargo merely because a timer expired.
            truck.setControlInputs(0.0F, 0.0F, -1.0F);

            if (bedAngle <= 1.0F) {
                // Bed is safely down -> advance past dumping waypoints and resume driving
                advancePastDumpingWaypoints();
                this.state = AutopilotState.DRIVING;
                truck.setControlInputs(0.0F, 0.0F, 0.0F);
            }
        }
    }

    private void advancePastDumpingWaypoints() {
        if (activeRoute == null) return;
        while (currentWaypointIndex < activeRoute.size() - 1) {
            RouteWaypoint wp = activeRoute.get(currentWaypointIndex);
            RouteWaypoint next = activeRoute.get(currentWaypointIndex + 1);
            // Raised-bed flags also occur while driving away. Preserve that geometry.
            if (wp != null && next != null && (wp.dumping() || wp.bedAngle() > 18.0F)
                    && next.horizontalDistanceTo(truck.position()) < 0.35D) {
                currentWaypointIndex++;
            } else {
                break;
            }
        }
    }

    private RouteWaypoint getLookaheadWaypoint(double lookaheadDist) {
        if (activeRoute == null || activeRoute.isEmpty()) {
            return new RouteWaypoint(truck.getX(), truck.getY(), truck.getZ(), truck.getYRot(), 0, 0, 0, 0, false);
        }

        Vec3 truckPos = truck.position();
        boolean reverseSegment = waypointIsReverse(currentWaypointIndex);
        RouteWaypoint lastInSegment = getAdjustedWaypoint(currentWaypointIndex);
        for (int i = currentWaypointIndex; i < activeRoute.size(); i++) {
            RouteWaypoint wp = getAdjustedWaypoint(i);
            if (wp == null) {
                continue;
            }
            if (reverseSegment && !waypointIsReverse(i)) {
                break;
            }
            if (!reverseSegment && waypointIsReverse(i)) {
                break;
            }
            lastInSegment = wp;
            if (wp.horizontalDistanceTo(truckPos) >= lookaheadDist || wp.dumping() || wp.bedAngle() > 18.0F) {
                return wp;
            }
        }

        // Never aim at the route end beyond a reverse/tipping boundary.
        return lastInSegment;
    }

    private int findDepartureIndex() {
        // Search only the departure, never the nearby return road or a tipping
        // maneuver. Duplicate stopped WP0 samples must not pull us backwards.
        int end = Math.min(firstManeuverIndex(), activeRoute.size());
        List<RouteWaypoint> departure = new ArrayList<>();
        for (int i = 0; i < end && activeRoute.getDistanceFromStart(i) <= 12.0D; i++) {
            departure.add(getAdjustedWaypoint(i));
        }
        return departureIndex(departure, truck.position());
    }

    static int departureIndex(List<RouteWaypoint> departure, Vec3 position) {
        int closest = 0;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < departure.size(); i++) {
            double candidate = departure.get(i).horizontalDistanceTo(position);
            if (candidate < distance) {
                closest = i;
                distance = candidate;
            }
        }
        return closest;
    }

    private int findClosestWaypointIndex(Vec3 pos) {
        if (activeRoute == null || activeRoute.isEmpty()) {
            return 0;
        }
        int closestIndex = 0;
        double minDistanceSq = Double.MAX_VALUE;

        for (int i = 0; i < activeRoute.size(); i++) {
            RouteWaypoint wp = getAdjustedWaypoint(i);
            if (wp == null) continue;
            double dx = wp.x() - pos.x;
            double dz = wp.z() - pos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private boolean passedCurrentSegment() {
        if (activeRoute == null || currentWaypointIndex <= 0 || currentWaypointIndex >= activeRoute.size() - 1) return false;
        RouteWaypoint target = getAdjustedWaypoint(currentWaypointIndex);
        RouteWaypoint previous = getAdjustedWaypoint(currentWaypointIndex - 1);
        if (target.dumping() || target.bedAngle() > 18.0F
                || waypointIsReverse(currentWaypointIndex) != waypointIsReverse(currentWaypointIndex + 1)) return false;
        double sx = target.x() - previous.x();
        double sz = target.z() - previous.z();
        double length = Math.hypot(sx, sz);
        if (length < 0.01D) return false;
        double dx = truck.getX() - target.x();
        double dz = truck.getZ() - target.z();
        return (dx * sx + dz * sz) / length >= 0.0D
                && Math.abs(dx * sz - dz * sx) / length < 1.5D;
    }

    private boolean waypointIsReverse(int index) {
        return index >= 0 && index < reverseFlags.length && reverseFlags[index];
    }

    private boolean nextIsReverseSoon() {
        if (activeRoute == null) {
            return false;
        }
        double remaining = 0.0D;
        Vec3 cursor = truck.position();
        for (int i = currentWaypointIndex; i < activeRoute.size() && remaining < 5.0D; i++) {
            if (waypointIsReverse(i)) {
                return true;
            }
            RouteWaypoint wp = activeRoute.get(i);
            if (wp == null) {
                break;
            }
            remaining += wp.horizontalDistanceTo(cursor);
            cursor = wp.pos();
        }
        return false;
    }

    private int firstManeuverIndex() {
        for (int i = 0; i < reverseFlags.length; i++) {
            RouteWaypoint wp = activeRoute.get(i);
            if (reverseFlags[i] || (wp != null && (wp.dumping() || wp.bedAngle() > 18.0F))) {
                return i;
            }
        }
        return activeRoute == null ? 0 : activeRoute.size();
    }

    static boolean[] buildReverseFlags(DumpTruckRoute route) {
        boolean[] flags = new boolean[route.size()];
        for (int i = 0; i < route.size(); i++) {
            RouteWaypoint wp = route.get(i);
            if (wp == null) {
                continue;
            }
            // A stopped sample does not change gear. Recorded pauses and bed
            // movement must retain the approach direction until motion resumes.
            boolean reverse = wp.reverse() || (i > 0 && flags[i - 1]);
            if (Math.abs(wp.speed()) > 0.005F) {
                reverse = wp.speed() < 0.0F;
            }
            if (i > 0) {
                RouteWaypoint prev = route.get(i - 1);
                if (prev != null) {
                    double dx = wp.x() - prev.x();
                    double dz = wp.z() - prev.z();
                    double len = Math.hypot(dx, dz);
                    if (len > 0.12D) {
                        double yaw = Math.toRadians(wp.yaw());
                        double dot = (dx / len) * -Math.sin(yaw) + (dz / len) * Math.cos(yaw);
                        if (dot < -0.35D) {
                            reverse = true;
                        }
                    }
                }
            }
            flags[i] = reverse;
        }
        return flags;
    }

    /**
     * Shift only the departure from the excavator. The recorded turn, reverse,
     * tip and return road are not moved.
     */
    public RouteWaypoint getAdjustedWaypoint(int index) {
        if (activeRoute == null || activeRoute.isEmpty()) {
            return null;
        }
        RouteWaypoint raw = activeRoute.get(index);
        if (raw == null) {
            return null;
        }

        // Only the departure from the excavator moves. The turn, the reverse,
        // the tip and the road back stay on the recorded ground.
        if (waypointIsReverse(index) || raw.dumping() || raw.bedAngle() > 18.0F || index >= firstManeuverIndex()) {
            return raw;
        }

        Vec3 shift = DumpTruckRouteManager.getRouteStationShift(getRouteName());
        if (shift == null || shift.lengthSqr() < 0.001D) {
            return raw;
        }

        double blendDist = 12.0D;
        double distFromStart = activeRoute.getDistanceFromStart(index);
        double factor = distFromStart < blendDist ? 1.0D - (distFromStart / blendDist) : 0.0D;

        if (factor <= 0.001D) {
            return raw;
        }

        // Smooth cosine S-curve interpolation
        double smoothFactor = 0.5D * (1.0D - Math.cos(factor * Math.PI));

        double adjX = raw.x() + shift.x * smoothFactor;
        double adjY = raw.y() + shift.y * smoothFactor;
        double adjZ = raw.z() + shift.z * smoothFactor;

        return new RouteWaypoint(
                adjX, adjY, adjZ,
                raw.yaw(), raw.pitch(), raw.roll(),
                raw.speed(), raw.bedAngle(), raw.dumping()
        );
    }

    public void updateStationShift() {
        // Only a truck actually docked under the excavator advances the anchor.
        if (state == AutopilotState.WAITING_FOR_LOAD && activeRoute != null && !activeRoute.isEmpty()) {
            RouteWaypoint startWp = activeRoute.get(0);
            Vec3 shift = truck.position().subtract(startWp.pos());
            DumpTruckRouteManager.setRouteStationShift(getRouteName(), shift);
        }
    }

    /**
     * Search for another dump truck ahead in our lane / travel corridor.
     */
    public GroundworksDumpTruckEntity findTruckAhead(ServerLevel level) {
        Vec3 myPos = truck.position();
        boolean reversing = state == AutopilotState.REVERSING
                || (state == AutopilotState.WAITING_IN_QUEUE && queueResumeState == AutopilotState.REVERSING)
                || (returningReverse && (state == AutopilotState.RETURNING
                    || (state == AutopilotState.WAITING_IN_QUEUE && queueResumeState == AutopilotState.RETURNING)));
        Vec3 forward = truck.forwardVector().scale(reversing ? -1.0D : 1.0D);
        Vec3 right = truck.rightVector();
        AABB searchBox = truck.getBoundingBox().inflate(14.0D);

        GroundworksDumpTruckEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (GroundworksDumpTruckEntity other : level.getEntitiesOfClass(
                GroundworksDumpTruckEntity.class, searchBox,
                e -> e != truck && !e.isRemoved())) {

            Vec3 toOther = other.position().subtract(myPos);
            double fwdDist = toOther.dot(forward);
            double latDist = Math.abs(toOther.dot(right));

            // Must be ahead (fwdDist > 0.5) and within lateral lane clearance (latDist < 3.0)
            if (fwdDist > 0.5D && fwdDist < 14.0D && latDist < 3.0D) {
                double dotForward = forward.dot(other.forwardVector());
                // Avoid oncoming traffic from blocking each other
                if (reversing || dotForward > -0.4D) {
                    if (fwdDist < closestDist) {
                        closestDist = fwdDist;
                        closest = other;
                    }
                }
            }
        }
        return closest;
    }

    public Component getStatusReport() {
        return switch (state) {
            case IDLE -> Component.translatable("autopilot.pw_groundworks_dump_truck.idle");
            case RECORDING -> Component.translatable("autopilot.pw_groundworks_dump_truck.recording",
                    recordingName, recordedWaypoints.size());
            case WAITING_FOR_LOAD -> Component.translatable("autopilot.pw_groundworks_dump_truck.waiting_load",
                    getRouteName(), truck.storedUnits(), truck.capacity());
            case WAITING_IN_QUEUE -> waitingForDumpStation
                    ? Component.translatable("autopilot.pw_groundworks_dump_truck.dump_queue")
                    : Component.translatable("autopilot.pw_groundworks_dump_truck.truck_queue",
                    getRouteName(), queuePrecedingTruckId);
            case DRIVING -> Component.translatable("autopilot.pw_groundworks_dump_truck.driving",
                    getRouteName(), currentWaypointIndex + 1, activeRoute != null ? activeRoute.size() : 0,
                    activeRoute != null && !activeRoute.isEmpty() ? ((float) currentWaypointIndex / activeRoute.size()) * 100.0F : 0.0F);
            case REVERSING -> Component.translatable("autopilot.pw_groundworks_dump_truck.reversing",
                    getRouteName(), currentWaypointIndex + 1, activeRoute != null ? activeRoute.size() : 0);
            case DUMPING -> shuttle.isArmed()
                    ? shuttle.status()
                    : Component.translatable("autopilot.pw_groundworks_dump_truck.dumping",
                    getRouteName(), truck.getBedAngle(), truck.storedUnits());
            case RETURNING -> shuttle.isArmed()
                    ? shuttle.status()
                    : Component.translatable("autopilot.pw_groundworks_dump_truck.returning");
            case PARKED, TO_SCREEN -> shuttle.status();
        };
    }

    public void save(ValueOutput output) {
        output.putString("AutopilotState", state.name());
        output.putString("AutopilotRoute", getRouteName());
        output.putInt("AutopilotWaypoint", currentWaypointIndex);
        output.putBoolean("AutopilotLoop", loopEnabled);
        output.putString("AutopilotQueueResume", queueResumeState.name());
        output.putBoolean("AutopilotDumpQueue", waitingForDumpStation);
    }

    public void load(ValueInput input, MinecraftServer server) {
        String stateStr = input.getStringOr("AutopilotState", "IDLE");
        String rName = input.getStringOr("AutopilotRoute", "");
        int wpIndex = input.getIntOr("AutopilotWaypoint", 0);
        boolean loop = input.getBooleanOr("AutopilotLoop", true);

        try {
            this.state = AutopilotState.valueOf(stateStr);
        } catch (Exception e) {
            this.state = AutopilotState.IDLE;
        }

        this.routeName = rName;
        this.currentWaypointIndex = wpIndex;
        this.loopEnabled = loop;
        try {
            queueResumeState = AutopilotState.valueOf(input.getStringOr("AutopilotQueueResume", "DRIVING"));
        } catch (IllegalArgumentException ignored) {
            queueResumeState = AutopilotState.DRIVING;
        }
        waitingForDumpStation = input.getBooleanOr("AutopilotDumpQueue", false);
        queuePrecedingTruckId = -1;

        if (server != null && !rName.isBlank() && state != AutopilotState.IDLE && state != AutopilotState.RECORDING) {
            this.activeRoute = DumpTruckRouteManager.getRoute(server, rName);
            if (this.activeRoute != null) {
                this.reverseFlags = buildReverseFlags(this.activeRoute);
            }
        }
    }
}
