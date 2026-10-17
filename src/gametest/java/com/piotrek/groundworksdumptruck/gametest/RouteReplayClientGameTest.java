package com.piotrek.groundworksdumptruck.gametest;

import com.google.gson.JsonParser;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.route.DumpTruckAutopilot;
import com.piotrek.groundworksdumptruck.route.DumpTruckRoute;
import com.piotrek.groundworksdumptruck.route.DumpTruckRouteManager;
import com.piotrek.groundworksdumptruck.route.RouteWaypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the real server entity tick and client renderer, not a bicycle approximation. */
public final class RouteReplayClientGameTest implements FabricClientGameTest {
    private static volatile Replay active;
    private static volatile Convoy convoy;
    private static volatile Crossing crossing;
    private static boolean registered;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!registered) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                Replay replay = active;
                if (replay != null && !replay.done) replay.sample(server);
                Convoy current = convoy;
                if (current != null && !current.done) current.sample();
                Crossing junction = crossing;
                if (junction != null && !junction.done) junction.sample();
            });
            registered = true;
        }
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tick rate 100");
            Replay replay = world.getServer().computeOnServer(server -> prepare(server));
            active = replay;
            context.waitFor(client -> replay.done, 20000);
            context.takeScreenshot(replay.failure == null ? "route-l3-finish" : "route-l3-failure");
            replay.writeTrace();
            if (replay.failure != null) throw new AssertionError(replay.failure);
            if (!replay.sawReverse || !replay.sawDump || !replay.emptied) {
                throw new AssertionError("Missing reverse/dump/empty-bed phase: " + replay.describe());
            }
            if (replay.truck.storedUnits() != 0 || replay.truck.getBedAngle() > 1.0F) {
                throw new AssertionError("Route ended with cargo or raised bed: " + replay.describe());
            }
        } finally {
            active = null;
        }
        // Reproduce departure after the excavator has advanced the truck past WP0.
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tick rate 100");
            Replay departure = world.getServer().computeOnServer(server -> {
                Replay setup = prepare(server);
                Vec3 advanced = setup.truck.position().add(setup.truck.forwardVector().scale(4.0D));
                setup.truck.setPos(advanced.x, advanced.y, advanced.z);
                setup.truck.getMovementController().stopMotion();
                setup.truck.getAutopilot().startPlayback(setup.route, false, true);
                setup.startupOnly = true;
                return setup;
            });
            active = departure;
            context.waitFor(client -> departure.done, 3000);
            context.takeScreenshot(departure.failure == null ? "departure-ahead-finish" : "departure-ahead-failure");
            departure.writeTrace();
            if (departure.failure != null) throw new AssertionError(departure.failure);
        } finally {
            active = null;
        }
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tick rate 100");
            Convoy scenario = world.getServer().computeOnServer(server -> {
                Replay setup = prepare(server);
                setup.truck.getAutopilot().startPlayback(setup.route, true, false);
                setup.truck.onExcavatorHornSignal(setup.truck.position());
                ServerLevel level = server.overworld();
                GroundworksDumpTruckEntity second = new GroundworksDumpTruckEntity(GroundworksDumpTruckMod.DUMP_TRUCK, level);
                Vec3 behind = setup.truck.position().subtract(setup.truck.forwardVector().scale(12));
                second.setPos(behind.x, behind.y, behind.z);
                second.setYRot(setup.truck.getYRot());
                level.addFreshEntity(second);
                second.getAutopilot().startPlayback(setup.route, true, false);
                return new Convoy(setup.truck, second, setup.route);
            });
            convoy = scenario;
            context.waitFor(client -> scenario.done, 30000);
            context.takeScreenshot(scenario.failure == null ? "convoy-finish" : "convoy-failure");
            Files.write(Path.of("route-convoy-trace.csv"), scenario.trace);
            if (scenario.failure != null) throw new AssertionError(scenario.failure);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            convoy = null;
        }
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tick rate 100");
            Crossing junction = world.getServer().computeOnServer(server -> {
                Replay pad = prepare(server);
                com.piotrek.groundworksdumptruck.route.DumpReservationRegression.verify(server.overworld());
                pad.truck.getAutopilot().stop();
                pad.truck.discard();
                ServerLevel level = server.overworld();
                return new Crossing(crossingTruck(level, 0, -12, 0, 0, 12),
                        crossingTruck(level, -12, 0, -90, 12, 0));
            });
            crossing = junction;
            context.waitFor(client -> junction.done, 6000);
            context.takeScreenshot(junction.failure == null ? "right-priority-finish" : "right-priority-failure");
            if (junction.failure != null) throw new AssertionError(junction.failure);
        } finally {
            crossing = null;
        }
    }

    private static GroundworksDumpTruckEntity crossingTruck(ServerLevel level, double x, double z,
                                                          float yaw, double endX, double endZ) {
        var truck = new GroundworksDumpTruckEntity(GroundworksDumpTruckMod.DUMP_TRUCK, level);
        truck.setPos(x, 81, z);
        truck.setYRot(yaw);
        level.addFreshEntity(truck);
        var route = new DumpTruckRoute("crossing-" + truck.getId(), List.of(
                new RouteWaypoint(x, 81, z, yaw, 0, 0, 0.2F, 0, false),
                new RouteWaypoint(endX, 81, endZ, yaw, 0, 0, 0.2F, 0, false)));
        truck.getAutopilot().startPlayback(route, false, true);
        return truck;
    }

    private static final class Crossing {
        final GroundworksDumpTruckEntity yielding, priority;
        volatile boolean done;
        String failure;
        int ticks;
        boolean sawYield, priorityPassed;
        Crossing(GroundworksDumpTruckEntity yielding, GroundworksDumpTruckEntity priority) {
            this.yielding = yielding; this.priority = priority;
        }
        void sample() {
            ticks++;
            sawYield |= yielding.isTrafficYielding();
            priorityPassed |= priority.getX() > 4.0D;
            if (Convoy.bodiesOverlap(yielding, priority)) fail("Crossing vehicle bodies overlapped");
            if (yielding.getZ() > -3.5D && !priorityPassed) fail("Vehicle failed to yield to traffic from right");
            if (!yielding.getAutopilot().isActive() && !priority.getAutopilot().isActive()) {
                if (!sawYield || !priorityPassed) fail("Right-hand priority was not exercised");
                done = true;
            }
            if (ticks > 600) fail("Crossing traffic deadlocked");
        }
        void fail(String message) {
            failure = message + " tick=" + ticks + " yielding=" + yielding.position() + " priority=" + priority.position();
            done = true;
        }
    }

    private static final class Convoy {
        final GroundworksDumpTruckEntity first, second;
        final DumpTruckRoute route;
        final List<String> trace = new ArrayList<>();
        volatile boolean done;
        String failure;
        int ticks;
        boolean secondLoaded, firstDumped, secondDumped, sawQueue;
        GroundworksDumpTruckEntity queuedAfterReturn;
        int queuedReturnIndex;
        Convoy(GroundworksDumpTruckEntity first, GroundworksDumpTruckEntity second, DumpTruckRoute route) {
            this.first = first; this.second = second; this.route = route;
        }
        void sample() {
            ticks++;
            var a = first.getAutopilot(); var b = second.getAutopilot();
            trace.add(ticks + "," + a.getState() + "," + a.getCurrentWaypointIndex() + "," + first.position()
                    + "," + b.getState() + "," + b.getCurrentWaypointIndex() + "," + second.position());
            if (!secondLoaded && b.getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD) {
                second.acceptMaterial(GranularMaterialRegistry.DIRT, 512);
                second.onExcavatorHornSignal(second.position());
                secondLoaded = true;
            }
            if (ticks == 500) {
                // Excavator advances its loading point while both trucks are away.
                Vec3 direction = route.get(0).pos().subtract(route.get(1).pos()).normalize().scale(-2);
                DumpTruckRouteManager.setRouteStationShift(route.name(), direction);
            }
            firstDumped |= a.getState() == DumpTruckAutopilot.AutopilotState.DUMPING;
            secondDumped |= b.getState() == DumpTruckAutopilot.AutopilotState.DUMPING;
            sawQueue |= a.getState() == DumpTruckAutopilot.AutopilotState.WAITING_IN_QUEUE
                    || b.getState() == DumpTruckAutopilot.AutopilotState.WAITING_IN_QUEUE;
            if (a.getState() == DumpTruckAutopilot.AutopilotState.DUMPING
                    && b.getState() == DumpTruckAutopilot.AutopilotState.DUMPING) fail("Two trucks tipping simultaneously");
            if (bodiesOverlap(first, second)) fail("Truck bodies overlapped");
            if (firstDumped && secondDumped && first.storedUnits() == 0 && second.storedUnits() == 0
                    && (a.getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD
                        || b.getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD)
                    && (a.getState() == DumpTruckAutopilot.AutopilotState.WAITING_IN_QUEUE
                        || b.getState() == DumpTruckAutopilot.AutopilotState.WAITING_IN_QUEUE)) {
                GroundworksDumpTruckEntity docked = a.getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD ? first : second;
                if (docked.getAutopilot().getAdjustedWaypoint(0).horizontalDistanceTo(docked.position()) > 0.5D)
                    fail("Returned truck did not reach advanced loading station");
                if (!sawQueue) fail("Queue never exercised");
                if (queuedAfterReturn == null) {
                    queuedAfterReturn = docked == first ? second : first;
                    queuedReturnIndex = queuedAfterReturn.getAutopilot().getCurrentWaypointIndex();
                    docked.acceptMaterial(GranularMaterialRegistry.DIRT, 512);
                    docked.onExcavatorHornSignal(docked.position());
                }
            }
            if (queuedAfterReturn != null) {
                var follower = queuedAfterReturn.getAutopilot();
                // It may still finish the recorded return road before docking.
                if (follower.getState() == DumpTruckAutopilot.AutopilotState.DRIVING
                        && follower.getCurrentWaypointIndex() < queuedReturnIndex) {
                    fail("Returning queue member departed instead of docking for load");
                }
                if (follower.getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD) {
                    if (follower.getAdjustedWaypoint(0).horizontalDistanceTo(queuedAfterReturn.position()) > 0.5D)
                        fail("Next loading queue member stopped short of excavator");
                    done = true;
                }
            }
            if (ticks > 4500) fail("Convoy stalled or failed to return to loading queue");
        }
        private static boolean bodiesOverlap(GroundworksDumpTruckEntity a, GroundworksDumpTruckEntity b) {
            Vec3 delta = b.position().subtract(a.position());
            for (Vec3 axis : List.of(a.forwardVector(), a.rightVector(), b.forwardVector(), b.rightVector())) {
                double radiusA = 3.2D * Math.abs(axis.dot(a.forwardVector())) + 1.4D * Math.abs(axis.dot(a.rightVector()));
                double radiusB = 3.2D * Math.abs(axis.dot(b.forwardVector())) + 1.4D * Math.abs(axis.dot(b.rightVector()));
                if (Math.abs(delta.dot(axis)) >= radiusA + radiusB) return false;
            }
            return true;
        }
        void fail(String message) {
            failure = message + " at tick " + ticks + " first=" + first.getAutopilot().getState()
                    + "/" + first.getAutopilot().getCurrentWaypointIndex() + " second=" + second.getAutopilot().getState()
                    + "/" + second.getAutopilot().getCurrentWaypointIndex();
            done = true;
        }
    }

    private static Replay prepare(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // Dedicated disposable world, never the user's save. Concrete avoids soil settling.
        for (int x = -40; x <= 40; x++) {
            for (int z = -90; z <= 20; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
                for (int y = 81; y <= 86; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        var player = server.getPlayerList().getPlayers().getFirst();
        player.setPos(0, 84, -20);
        verifyFillCommand(server, level, player);
        player.setPos(0, 84, -20);
        DumpTruckRoute route = loadRoute();
        DumpTruckRouteManager.setRouteStationShift(route.name(), Vec3.ZERO);
        RouteWaypoint start = route.get(0);
        GroundworksDumpTruckEntity truck = new GroundworksDumpTruckEntity(GroundworksDumpTruckMod.DUMP_TRUCK, level);
        truck.setPos(start.x(), start.y(), start.z());
        truck.setYRot(start.yaw());
        truck.setYHeadRot(start.yaw());
        truck.setYBodyRot(start.yaw());
        level.addFreshEntity(truck);
        truck.acceptMaterial(GranularMaterialRegistry.DIRT, 512);
        truck.getAutopilot().startPlayback(route, false, false);
        if (truck.getAutopilot().getState() != DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD) {
            throw new AssertionError("Route must wait for excavator horn");
        }
        truck.onExcavatorHornSignal(truck.position());
        return new Replay(truck, route);
    }

    private static void verifyFillCommand(MinecraftServer server, ServerLevel level,
                                          net.minecraft.server.level.ServerPlayer player) {
        var truck = new GroundworksDumpTruckEntity(GroundworksDumpTruckMod.DUMP_TRUCK, level);
        truck.setPos(30, 81, 10);
        level.addFreshEntity(truck);
        if (!player.startRiding(truck)) throw new AssertionError("Cannot mount fill-command test truck");
        var source = server.createCommandSourceStack().withEntity(player);
        server.getCommands().performPrefixedCommand(source, "dumptruck fill gold_gravel");
        if (truck.storedUnits() != truck.capacity()
                || truck.storedComposition().unitsOf(GranularMaterialRegistry.GRAVEL.id()) != truck.capacity())
            throw new AssertionError("Fill command did not create a full gravel load");
        server.getCommands().performPrefixedCommand(source, "dumptruck fill gold_gravel");
        if (truck.storedUnits() != truck.capacity()) throw new AssertionError("Repeated fill exceeded capacity");
        truck.extractComposition(truck.capacity());
        truck.acceptMaterial(GranularMaterialRegistry.DIRT, 128);
        server.getCommands().performPrefixedCommand(source, "dumptruck fill gold_gravel");
        if (truck.storedUnits() != 128 || truck.storedComposition().unitsOf(GranularMaterialRegistry.DIRT.id()) != 128)
            throw new AssertionError("Fill command replaced or mixed existing non-gravel cargo");
        player.stopRiding();
        truck.discard();
        server.getCommands().performPrefixedCommand(source, "dumptruck fill gold_gravel");
    }

    private static DumpTruckRoute loadRoute() {
        try (var stream = RouteReplayClientGameTest.class.getResourceAsStream("/routes/l3.json")) {
            if (stream == null) throw new IllegalStateException("Missing route fixture l3.json");
            DumpTruckRoute original = DumpTruckRoute.fromJson(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            Vec3 origin = original.get(0).pos();
            List<RouteWaypoint> points = original.waypoints().stream().map(wp -> new RouteWaypoint(
                    wp.x() - origin.x, 81, wp.z() - origin.z,
                    wp.yaw(), wp.pitch(), wp.roll(), wp.speed(), wp.bedAngle(), wp.dumping(), wp.reverse())).toList();
            return new DumpTruckRoute("l3-regression", points);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load recorded route fixture", e);
        }
    }

    private static final class Replay {
        final GroundworksDumpTruckEntity truck;
        final DumpTruckRoute route;
        final List<String> trace = new ArrayList<>();
        volatile boolean done;
        String failure;
        boolean sawReverse;
        boolean sawDump;
        boolean emptied;
        boolean startupOnly;
        int ticks;
        int stalledTicks;
        int lastIndex = -1;

        Replay(GroundworksDumpTruckEntity truck, DumpTruckRoute route) {
            this.truck = truck;
            this.route = route;
            trace.add("tick,index,state,x,y,z,yaw,speed,wheel,bed,cargo,distance");
        }

        void sample(MinecraftServer server) {
            ticks++;
            var ap = truck.getAutopilot();
            int index = ap.getCurrentWaypointIndex();
            RouteWaypoint wp = route.get(index);
            double distance = wp.horizontalDistanceTo(truck.position());
            sawReverse |= ap.getState() == DumpTruckAutopilot.AutopilotState.REVERSING;
            sawDump |= ap.getState() == DumpTruckAutopilot.AutopilotState.DUMPING;
            emptied |= truck.storedUnits() == 0;
            if (sawDump && ap.getState() != DumpTruckAutopilot.AutopilotState.DUMPING
                    && (truck.storedUnits() != 0 || truck.getBedAngle() > 1.0F)) {
                fail("Left tipping phase with cargo or raised bed");
            }
            trace.add(String.format(java.util.Locale.ROOT, "%d,%d,%s,%.3f,%.3f,%.3f,%.3f,%.4f,%.3f,%.3f,%d,%.3f",
                    ticks, index, ap.getState(), truck.getX(), truck.getY(), truck.getZ(), truck.getYRot(),
                    truck.getMovementController().forwardSpeed(), truck.getMovementController().steerAngle(),
                    truck.getBedAngle(), truck.storedUnits(), distance));
            if (!ap.isActive()) {
                RouteWaypoint end = route.get(route.size() - 1);
                if (end.horizontalDistanceTo(truck.position()) > 1.0D) fail("Stopped away from route endpoint");
                done = true;
                return;
            }
            if (distance > 3.0D) fail("Replay diverged >3 blocks from current recorded point");
            if (truck.getY() < 80.5D) fail("Truck fell below working pad");
            if (index == lastIndex && ap.getState() != DumpTruckAutopilot.AutopilotState.DUMPING) stalledTicks++;
            else stalledTicks = 0;
            lastIndex = index;
            if (stalledTicks > 300) fail("No waypoint progress for 300 actual server ticks");
            if (startupOnly) {
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(truck.getYRot() - route.get(0).yaw())) > 25.0F)
                    fail("Departure circled around the excavator");
                if (ticks >= 80) done = true;
            }
            if (ticks > 4000) fail("Whole route exceeded 4000 server ticks");
        }

        void fail(String message) {
            failure = message + ": " + describe();
            done = true;
        }

        String describe() {
            return "tick=" + ticks + " point=" + truck.getAutopilot().getCurrentWaypointIndex()
                    + " state=" + truck.getAutopilot().getState() + " position=" + truck.position()
                    + " yaw=" + truck.getYRot() + " cargo=" + truck.storedUnits();
        }

        void writeTrace() {
            try {
                Path target = Path.of(startupOnly ? "route-departure-trace.csv" : "route-replay-trace.csv");
                Files.write(target, trace);
                GroundworksDumpTruckMod.LOGGER.info("Route replay trace: {} {}", target.toAbsolutePath(), describe());
            } catch (Exception e) {
                throw new IllegalStateException("Cannot write route replay trace", e);
            }
        }
    }
}
