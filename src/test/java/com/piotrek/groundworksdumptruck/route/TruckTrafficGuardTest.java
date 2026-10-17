package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.vehicle.TruckTrafficGuard;
import com.piotrek.groundworksdumptruck.vehicle.TruckTrafficGuard.Motion;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TruckTrafficGuardTest {
    @Test void rightHandVehicleWinsCrossingSymmetrically() {
        Motion north = motion(0, -8, 0, 1, 0.2, 1);
        Motion east = motion(-8, 0, 1, 0, 0.2, 2);
        assertTrue(TruckTrafficGuard.mustYield(north, east));
        assertFalse(TruckTrafficGuard.mustYield(east, north));
    }
    @Test void followerYieldsButLeaderKeepsMoving() {
        Motion rear = motion(0, 0, 0, 1, 0.25, 1);
        Motion front = motion(0, 10, 0, 1, 0.05, 2);
        assertTrue(TruckTrafficGuard.mustYield(rear, front));
        assertFalse(TruckTrafficGuard.mustYield(front, rear));
    }
    @Test void headOnSingleTrackStopsBothVehicles() {
        Motion a = motion(0, -10, 0, 1, 0.2, 1);
        Motion b = motion(0, 10, 0, -1, 0.2, 2);
        assertTrue(TruckTrafficGuard.mustYield(a, b));
        assertTrue(TruckTrafficGuard.mustYield(b, a));
    }
    @Test void reversingUsesTravelDirectionNotCabHeading() {
        Motion backwards = motion(0, -8, 0, -1, -0.2, 1);
        Motion east = motion(-8, 0, 1, 0, 0.2, 2);
        assertTrue(TruckTrafficGuard.mustYield(backwards, east));
        assertFalse(TruckTrafficGuard.mustYield(east, backwards));
    }
    @Test void SeparateParallelTracksDoNotBlockEachOther() {
        assertFalse(TruckTrafficGuard.mustYield(motion(0, 0, 0, 1, 0.2, 1), motion(5, 0, 0, 1, 0.2, 2)));
    }
    private static Motion motion(double x, double z, double fx, double fz, double speed, int id) {
        return new Motion(new Vec3(x, 81, z), new Vec3(fx, 0, fz), speed, 0, id);
    }
}
