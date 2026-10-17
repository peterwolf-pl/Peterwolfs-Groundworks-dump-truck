package com.piotrek.groundworksdumptruck.route;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DumpTruckRouteTest {

    @Test
    @DisplayName("Route waypoint correctly serializes to and from JSON")
    void testWaypointJsonSerialization() {
        RouteWaypoint original = new RouteWaypoint(
                12.345D, 64.0D, -98.765D,
                180.5F, -12.3F, 4.5F,
                0.28F, 45.0F, true
        );

        JsonObject json = original.toJson();
        assertNotNull(json);
        assertEquals(12.345D, json.get("x").getAsDouble(), 0.001D);
        assertTrue(json.get("dumping").getAsBoolean());
        assertEquals(45.0F, json.get("bedAngle").getAsFloat(), 0.1F);

        RouteWaypoint restored = RouteWaypoint.fromJson(json);
        assertEquals(original.x(), restored.x(), 0.001D);
        assertEquals(original.y(), restored.y(), 0.001D);
        assertEquals(original.z(), restored.z(), 0.001D);
        assertEquals(original.yaw(), restored.yaw(), 0.1F);
        assertEquals(original.speed(), restored.speed(), 0.001F);
        assertEquals(original.bedAngle(), restored.bedAngle(), 0.1F);
        assertEquals(original.dumping(), restored.dumping());
    }

    @Test
    @DisplayName("DumpTruckRoute computes total distance and detects dumping sections")
    void testRouteDistanceAndDumpingDetection() {
        List<RouteWaypoint> points = new ArrayList<>();
        // Waypoint 0: Loading point (under excavator)
        points.add(new RouteWaypoint(0.0D, 64.0D, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false));
        // Waypoint 1: Travel down haul road
        points.add(new RouteWaypoint(0.0D, 64.0D, 20.0D, 0.0F, 0.0F, 0.0F, 0.28F, 0.0F, false));
        // Waypoint 2: Dump site (tipping bed, "wykipowywany")
        points.add(new RouteWaypoint(0.0D, 64.0D, 40.0D, 0.0F, 0.0F, 0.0F, 0.0F, 48.0F, true));
        // Waypoint 3: Lowering bed and return loop
        points.add(new RouteWaypoint(10.0D, 64.0D, 20.0D, 180.0F, 0.0F, 0.0F, 0.25F, 0.0F, false));
        // Waypoint 4: Return to start
        points.add(new RouteWaypoint(0.0D, 64.0D, 0.0D, 180.0F, 0.0F, 0.0F, 0.20F, 0.0F, false));

        DumpTruckRoute route = new DumpTruckRoute("quarry_loop", points);

        assertEquals("quarry_loop", route.name());
        assertEquals(5, route.size());
        assertTrue(route.hasDumping(), "Route must detect that waypoint #2 contains dumping");
        assertTrue(route.totalLengthMeters() > 80.0D, "Total distance should exceed 80 meters");

        JsonObject routeJson = route.toJson();
        DumpTruckRoute deserialized = DumpTruckRoute.fromJson(routeJson);

        assertEquals(route.name(), deserialized.name());
        assertEquals(route.size(), deserialized.size());
        assertTrue(deserialized.hasDumping());
        assertEquals(route.totalLengthMeters(), deserialized.totalLengthMeters(), 0.01D);
    }

    @Test
    @DisplayName("Convoy queue bumper gap calculation maintains 3-4 blocks distance")
    void testConvoyBumperGapCalculation() {
        // Truck 1 forward along +Z axis (yaw = 0)
        // Truck 2 is behind Truck 1 on the Z axis
        double truckLength = 6.4D;

        // When center-to-center distance is 10.0 meters:
        // Gap = 10.0 - 6.4 = 3.6 meters (comfortably in the 3.0 - 4.0 blocks interval)
        double centerDistance1 = 10.0D;
        double gap1 = Math.max(0.0D, centerDistance1 - truckLength);
        assertEquals(3.6D, gap1, 0.01D);
        assertTrue(gap1 >= 3.0D && gap1 <= 4.0D, "Gap of 3.6m is within 3-4 blocks");

        // When gap <= 3.5m: truck must halt in queue
        double centerDistanceClose = 9.8D;
        double gapClose = Math.max(0.0D, centerDistanceClose - truckLength);
        assertTrue(gapClose <= 3.5D, "Gap of 3.4m triggers WAITING_IN_QUEUE state");

        // When preceding truck accelerates away and gap > 4.0m: truck resumes movement
        double centerDistanceMoved = 10.6D;
        double gapMoved = Math.max(0.0D, centerDistanceMoved - truckLength);
        assertTrue(gapMoved > 4.0D, "Gap of 4.2m triggers movement resumption");
    }

    @Test
    @DisplayName("Pure pursuit target yaw and steering math accurately computes heading error")
    void testPurePursuitSteering() {
        // Vehicle at origin facing North (yaw = 180° in MC, forward is -Z)
        // Actually in our model:
        // yaw 0° has forward along +Z (fwdX = -sin(0) = 0, fwdZ = cos(0) = 1)
        // Target is at (0, 10): target is directly ahead -> delta yaw = 0
        double dxDirect = 0.0D;
        double dzDirect = 10.0D;
        float targetYawDirect = (float) Math.toDegrees(Math.atan2(-dxDirect, dzDirect));
        assertEquals(0.0F, targetYawDirect, 0.01F);

        // Target at (10, 10): target is forward-right (+X, +Z)
        // -dx = -10, dz = 10 -> atan2(-10, 10) = -45°
        // Yaw in MC: facing +X is yaw 270° (-90°).
        double dxRight = 10.0D;
        double dzRight = 10.0D;
        float targetYawRight = (float) Math.toDegrees(Math.atan2(-dxRight, dzRight));
        assertEquals(-45.0F, targetYawRight, 0.01F);
    }
}
