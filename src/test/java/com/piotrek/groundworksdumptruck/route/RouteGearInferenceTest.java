package com.piotrek.groundworksdumptruck.route;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RouteGearInferenceTest {
    @Test
    void stoppedLegacySamplesKeepReverseThroughTippingUntilForwardMotion() {
        DumpTruckRoute route = new DumpTruckRoute("reverse-pause-tip", List.of(
                point(0, 0.1F, 0), point(-1, -0.1F, 0),
                point(-1.1, 0, 0), point(-1.1, 0, 20), point(-1.1, 0, 48),
                point(-1.1, 0, 0), point(-0.9, 0.1F, 0)));
        assertArrayEquals(new boolean[]{false, true, true, true, true, true, false},
                DumpTruckAutopilot.buildReverseFlags(route));
    }

    @Test
    void shortForwardManeuverBetweenReverseSegmentsIsNotSmoothedAway() {
        DumpTruckRoute route = new DumpTruckRoute("three-point", List.of(
                point(0, -0.1F, 0), point(-1, -0.1F, 0),
                point(-0.8, 0.1F, 0), point(-1, -0.1F, 0)));
        assertArrayEquals(new boolean[]{true, true, false, true},
                DumpTruckAutopilot.buildReverseFlags(route));
    }

    private static RouteWaypoint point(double z, float speed, float bedAngle) {
        return new RouteWaypoint(0, 81, z, 0, 0, 0, speed, bedAngle, bedAngle > 40);
    }
}
