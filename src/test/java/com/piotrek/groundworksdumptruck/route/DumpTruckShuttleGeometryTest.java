package com.piotrek.groundworksdumptruck.route;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DumpTruckShuttleGeometryTest {

    @Test
    void recordedSteerTurnsTowardRecordedYawWithoutFlippingReverse() {
        assertTrue(DumpTruckAutopilot.recordedSteer(24.0F, 0.0D, false) > 0.0F);
        assertTrue(DumpTruckAutopilot.recordedSteer(24.0F, 0.0D, true) < 0.0F);
        assertTrue(DumpTruckAutopilot.recordedSteer(0.0F, 3.0D, false) < 0.0F);
        assertTrue(DumpTruckAutopilot.recordedSteer(0.0F, 3.0D, true) < 0.0F);
    }

    @Test
    void parkSlotPutsExcavatorOnTheLeftOfTheBed() {
        Vec3 excavator = new Vec3(10.0D, 64.0D, 20.0D);
        Vec3 park = DumpTruckShuttle.parkPosition(excavator, 0.0F);

        Vec3 left = DumpTruckShuttle.leftVector(0.0F);
        Vec3 bed = park.add(DumpTruckShuttle.forwardVector(0.0F).scale(-DumpTruckShuttle.BED_BEHIND));
        Vec3 toExcavator = excavator.subtract(bed);

        assertEquals(0.0D, toExcavator.dot(DumpTruckShuttle.forwardVector(0.0F)), 0.001D);
        assertTrue(toExcavator.dot(left) > 3.5D);
        assertEquals(64.0D, park.y, 0.001D);
    }

    @Test
    void onlyOneApproachingTruckOwnsTheLoadingSlot() {
        assertTrue(DumpTruckShuttle.ownsLoadingSlot(4, 2, 4, 1, false));
        assertTrue(!DumpTruckShuttle.ownsLoadingSlot(4, 1, 4, 2, false));
        assertTrue(DumpTruckShuttle.ownsLoadingSlot(8, 1, 2, 2, false));
        assertTrue(!DumpTruckShuttle.ownsLoadingSlot(2, 2, 8, 1, false));
        assertTrue(DumpTruckShuttle.ownsLoadingSlot(2, 1, 4, 2, true));
    }

    @Test
    void reverseCorrectionSteersTheTailTowardTheTarget() {
        assertTrue(DumpTruckShuttle.wheelAngle(new Vec3(2, 0, -8), 0) < 0);
        assertTrue(DumpTruckShuttle.wheelAngle(new Vec3(-2, 0, -8), 0) > 0);
        assertEquals(0, DumpTruckShuttle.wheelAngle(new Vec3(0, 0, -8), 0), 0.001);
    }

    @Test
    void reversePosePutsTheTailOnTheHopper() {
        Vec3 screen = new Vec3(0.0D, 70.0D, 0.0D);
        float yaw = 90.0F;
        Vec3 pose = DumpTruckShuttle.pose(screen, yaw, DumpTruckShuttle.LIP_BEHIND);
        Vec3 lip = pose.add(DumpTruckShuttle.forwardVector(yaw).scale(-DumpTruckShuttle.LIP_BEHIND));

        assertEquals(screen.x, lip.x, 0.001D);
        assertEquals(screen.z, lip.z, 0.001D);
    }
}
