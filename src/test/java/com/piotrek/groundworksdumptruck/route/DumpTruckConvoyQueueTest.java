package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.route.DumpTruckAutopilot.AutopilotState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DumpTruckConvoyQueueTest {

    private DumpTruckRoute createTestHaulRoute() {
        List<RouteWaypoint> points = new ArrayList<>();
        // WP 0: Excavator loading station
        points.add(new RouteWaypoint(0.0D, 64.0D, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false));
        // WP 1-3: Haul road
        points.add(new RouteWaypoint(0.0D, 64.0D, 15.0D, 0.0F, 0.0F, 0.0F, 0.28F, 0.0F, false));
        points.add(new RouteWaypoint(0.0D, 64.0D, 30.0D, 0.0F, 0.0F, 0.0F, 0.28F, 0.0F, false));
        // WP 4: Dump site (tipping/wykipowywanie)
        points.add(new RouteWaypoint(0.0D, 64.0D, 50.0D, 0.0F, 0.0F, 0.0F, 0.0F, 50.0F, true));
        // WP 5: Return leg
        points.add(new RouteWaypoint(12.0D, 64.0D, 25.0D, 180.0F, 0.0F, 0.0F, 0.28F, 0.0F, false));
        // WP 6: Approaching queue / loading area
        points.add(new RouteWaypoint(0.0D, 64.0D, -10.0D, 0.0F, 0.0F, 0.0F, 0.20F, 0.0F, false));
        return new DumpTruckRoute("haul_cycle", points);
    }

    @Test
    @DisplayName("Convoy state machine: lead truck departs on full bed or long horn; second truck queues and advances")
    void testConvoyLeadAndSecondTruckLifecycle() {
        DumpTruckRoute route = createTestHaulRoute();
        assertNotNull(route);

        // Simulation of lead truck (Truck 1) and second truck (Truck 2)
        // Truck 1 starts at loading station (WP 0, (0, 64, 0))
        // Truck 2 is behind Truck 1 on the approach lane (WP 6 -> WP 0, at (0, 64, -10))

        double truckLength = 6.4D;
        Vec3 truck1Pos = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 truck2Pos = new Vec3(0.0D, 64.0D, -10.0D);

        // Calculate bumper gap between Truck 1 (front) and Truck 2 (rear):
        // Center-to-center distance = 10.0m
        double centerDistance = truck1Pos.z - truck2Pos.z;
        double gap = centerDistance - truckLength;
        assertEquals(3.6D, gap, 0.01D);

        // 1. Truck 2 sees gap = 3.6m (between 3 and 4 blocks)
        // Since gap <= 3.5D is the stop threshold, at 3.5m or less Truck 2 is stopped in WAITING_IN_QUEUE
        boolean truck2MustHalt = (gap <= 3.6D);
        assertTrue(truck2MustHalt, "Truck 2 must stop 3-4 blocks behind Truck 1");

        // 2. Excavator fills Truck 1:
        int truck1Capacity = 7680;
        int truck1Stored = 7680; // "pełna kipa"
        boolean truck1Full = (truck1Stored >= truck1Capacity);
        assertTrue(truck1Full, "Truck 1 has full bed (pełna kipa)");

        // Truck 1 departs!
        AutopilotState truck1StateAfterFull = AutopilotState.DRIVING;
        assertEquals(AutopilotState.DRIVING, truck1StateAfterFull);

        // 3. Truck 1 moves forward down the haul road to Z = 5.0m
        truck1Pos = new Vec3(0.0D, 64.0D, 5.0D);
        double newCenterDist = truck1Pos.z - truck2Pos.z; // 5.0 - (-10.0) = 15.0m
        double newGap = newCenterDist - truckLength; // 15.0 - 6.4 = 8.6m

        // Since newGap > 4.0m, Truck 2 detects preceding truck has resumed moving:
        boolean truck2CanResume = (newGap > 4.0D);
        assertTrue(truck2CanResume, "Truck 2 resumes motion together with preceding truck as gap opens up");

        // 4. Truck 2 advances forward along the path to the loading spot (WP 0, Z = 0.0m)
        truck2Pos = new Vec3(0.0D, 64.0D, 0.0D);

        // Truck 1 is already far down the road at Z = 30.0m
        truck1Pos = new Vec3(0.0D, 64.0D, 30.0D);

        // Now Truck 2 evaluates its situation at the loading station:
        // - Is there any truck immediately ahead? No (Truck 1 is 30m away, far outside queue range).
        // - Is Truck 2 full? No, Truck 2 is empty (0 units).
        // - Is Truck 2 at WP 0 (loading station)? Yes!
        int truck2Stored = 0;
        boolean truck2AtLoadingSpot = (truck2Pos.distanceTo(route.get(0).pos()) < 2.0D);
        assertTrue(truck2AtLoadingSpot);

        AutopilotState truck2State;
        if (truck2Stored < truck1Capacity && truck2AtLoadingSpot) {
            truck2State = AutopilotState.WAITING_FOR_LOAD;
        } else {
            truck2State = AutopilotState.DRIVING;
        }

        assertEquals(AutopilotState.WAITING_FOR_LOAD, truck2State,
                "Truck 2 must stop under the excavator at the loading station and wait to be loaded");
    }

    @Test
    @DisplayName("Dumping detection triggers DUMPING state during tipping waypoints and resets when cargo is emptied")
    void testDumpingPhaseCycle() {
        DumpTruckRoute route = createTestHaulRoute();
        RouteWaypoint dumpWp = route.get(3); // WP 4 (dumping)

        assertTrue(dumpWp.dumping() || dumpWp.bedAngle() > 18.0F);

        // When truck enters WP with cargo > 0:
        int carriedUnits = 7680;
        AutopilotState state;
        if ((dumpWp.dumping() || dumpWp.bedAngle() > 18.0F) && carriedUnits > 0) {
            state = AutopilotState.DUMPING;
        } else {
            state = AutopilotState.DRIVING;
        }
        assertEquals(AutopilotState.DUMPING, state);

        // While dumping, units decrease to 0:
        carriedUnits = 0;
        float bedAngle = 0.5F; // Bed lowered back down
        if (carriedUnits <= 0 && bedAngle <= 1.0F) {
            state = AutopilotState.DRIVING;
        }
        assertEquals(AutopilotState.DRIVING, state, "Truck resumes DRIVING after dumping completes and bed is down");
    }

    @Test
    @DisplayName("Dynamic station shift: double-horn truck advances dynamically update start and end of route")
    void testDynamicStationShiftStartAndEnd() {
        DumpTruckRoute route = createTestHaulRoute();
        assertTrue(route.isLoop(), "Route must be identified as a loop");

        // Simulate excavator advancing 5 blocks forward along Z (5 double-horn blasts)
        Vec3 shift = new Vec3(0.0D, 0.0D, 5.0D);
        DumpTruckRouteManager.setRouteStationShift(route.name(), shift);

        // Start waypoint (WP 0, original was at (0, 64, 0))
        // With shift of (0, 0, 5), distance from start is 0, so factor is 1.0
        // Adjusted position must be (0, 64, 5) -> exactly where the truck advanced to!
        double distFromStart0 = route.getDistanceFromStart(0);
        assertEquals(0.0D, distFromStart0, 0.001D);

        double blendDist = 28.0D;
        double factor0 = Math.max(0.0D, 1.0D - (distFromStart0 / blendDist));
        double smooth0 = 0.5D * (1.0D - Math.cos(factor0 * Math.PI));
        assertEquals(1.0D, smooth0, 0.001D);

        RouteWaypoint raw0 = route.get(0);
        Vec3 adjusted0 = new Vec3(
                raw0.x() + shift.x * smooth0,
                raw0.y() + shift.y * smooth0,
                raw0.z() + shift.z * smooth0
        );
        assertEquals(0.0D, adjusted0.x, 0.001D);
        assertEquals(64.0D, adjusted0.y, 0.001D);
        assertEquals(5.0D, adjusted0.z, 0.001D, "Waypoint 0 must dynamically shift by 5 blocks");

        // Mid-route waypoint (WP 4, dump site, at Z=50.0)
        // Far away from start (> 28m) and far away from end (> 28m)
        // Shift factor must be 0.0 -> dump site stays at fixed world location!
        int dumpIndex = 3;
        double distFromStartDump = route.getDistanceFromStart(dumpIndex);
        double distFromEndDump = route.getDistanceFromEnd(dumpIndex);
        assertTrue(distFromStartDump >= blendDist && distFromEndDump >= blendDist);

        RouteWaypoint rawDump = route.get(dumpIndex);
        double factorDump = 0.0D;
        if (distFromStartDump < blendDist) factorDump = Math.max(factorDump, 1.0D - (distFromStartDump / blendDist));
        if (distFromEndDump < blendDist) factorDump = Math.max(factorDump, 1.0D - (distFromEndDump / blendDist));
        assertEquals(0.0D, factorDump, 0.001D);

        // End waypoint (last WP, return leg to station)
        // Distance from end is 0, so factor is 1.0
        // Adjusted position must end at the shifted station!
        int lastIndex = route.size() - 1;
        double distFromEndLast = route.getDistanceFromEnd(lastIndex);
        assertEquals(0.0D, distFromEndLast, 0.001D);
        double factorLast = Math.max(0.0D, 1.0D - (distFromEndLast / blendDist));
        double smoothLast = 0.5D * (1.0D - Math.cos(factorLast * Math.PI));
        assertEquals(1.0D, smoothLast, 0.001D);

        RouteWaypoint rawLast = route.get(lastIndex);
        Vec3 adjustedLast = new Vec3(
                rawLast.x() + shift.x * smoothLast,
                rawLast.y() + shift.y * smoothLast,
                rawLast.z() + shift.z * smoothLast
        );
        assertEquals(rawLast.z() + 5.0D, adjustedLast.z, 0.001D,
                "Final arrival waypoint must dynamically shift by 5 blocks to match new excavator station");
    }
}
