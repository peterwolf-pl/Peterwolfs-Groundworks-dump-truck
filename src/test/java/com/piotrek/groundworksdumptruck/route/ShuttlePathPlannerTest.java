package com.piotrek.groundworksdumptruck.route;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShuttlePathPlannerTest {
    @Test
    void straightDriveDoesNotInventALoop() {
        var path = ShuttlePathPlanner.candidates(new Vec3(0, 77, 0), 0, new Vec3(0, 77, 30), 0).getFirst();
        assertEquals(30, path.length(), 0.001);
        assertTrue(path.points().stream().allMatch(p -> Math.abs(p.position().x) < 0.001));
    }

    @Test
    void fractionalTerrainHeightDoesNotDiscardHorizontalPaths() {
        var paths = ShuttlePathPlanner.candidates(new Vec3(0, 77.25, 0), 0, new Vec3(0, 77, 30), 0);
        assertFalse(paths.isEmpty());
        assertEquals(30, paths.getFirst().length(), 0.001);
    }

    @Test
    void arrivesAtPositionAndHeadingInsteadOfTryingToPivotAtDestination() {
        for (float startYaw : new float[]{-179, -90, 0, 45, 179}) {
            for (float goalYaw : new float[]{-170, -45, 0, 90, 170}) {
                Vec3 goal = new Vec3(24, 77, -18);
                var paths = ShuttlePathPlanner.candidates(new Vec3(0, 77, 0), startYaw, goal, goalYaw);
                assertFalse(paths.isEmpty(), "No pose path: " + startYaw + " -> " + goalYaw);
                for (var path : paths) {
                    var end = path.points().getLast();
                    assertEquals(0, end.position().distanceTo(goal), 0.01);
                    double angle = Math.toRadians(end.yaw() - goalYaw);
                    assertEquals(0, Math.atan2(Math.sin(angle), Math.cos(angle)), 0.001);
                    for (int i = 1; i < path.points().size(); i++) {
                        var a = path.points().get(i - 1);
                        var b = path.points().get(i);
                        assertTrue(a.position().distanceTo(b.position()) <= 0.601);
                        double turn = Math.abs(Math.toRadians(b.yaw() - a.yaw()));
                        assertTrue(turn <= 0.6 / ShuttlePathPlanner.RADIUS + 0.0001);
                        assertEquals(77, b.position().y);
                    }
                }
            }
        }
    }
}
