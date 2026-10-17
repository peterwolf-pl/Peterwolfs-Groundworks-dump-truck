package com.piotrek.groundworksdumptruck.route;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteDepartureTest {
    @Test
    void advancedTruckJoinsDepartureInsteadOfChasingDuplicatedStartBehindIt() {
        List<RouteWaypoint> points = List.of(point(0), point(0), point(0), point(-1), point(-2), point(-4), point(-5));
        assertEquals(5, DumpTruckAutopilot.departureIndex(points, new Vec3(0, 81, -4.1)));
    }

    @Test
    void truckStillAtLoaderDoesNotSkipDeparture() {
        assertEquals(0, DumpTruckAutopilot.departureIndex(List.of(point(0), point(-1), point(-4)), new Vec3(0, 81, 0)));
    }

    private static RouteWaypoint point(double z) {
        return new RouteWaypoint(0, 81, z, 180, 0, 0, 0.1F, 0, false);
    }
}
