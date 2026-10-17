package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.vehicle.DumpTruckMovementController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteSteeringActuatorTest {
    @Test
    void reducedSpeedTargetActuallyBrakesInBothGears() {
        DumpTruckMovementController movement = new DumpTruckMovementController();
        movement.setForwardSpeed(0.34F);
        for (int i = 0; i < 20; i++) {
            movement.step(DumpTruckAutopilot.speedInput(0.08F, movement.forwardSpeed()), 0.0F);
        }
        assertEquals(0.08F, movement.forwardSpeed(), 0.04F);
        movement.setForwardSpeed(-0.18F);
        for (int i = 0; i < 20; i++) {
            movement.step(DumpTruckAutopilot.speedInput(-0.06F, movement.forwardSpeed()), 0.0F);
        }
        assertEquals(-0.06F, movement.forwardSpeed(), 0.04F);
    }

    @Test
    void tracksSmallRequestedAngleInsteadOfWindingUpToFullLock() {
        DumpTruckMovementController movement = new DumpTruckMovementController();
        for (int i = 0; i < 100; i++) {
            movement.step(0.3F, DumpTruckAutopilot.steeringInput(8.0F, movement.steerAngle()));
            if (i > 4) assertEquals(8.0F, movement.steerAngle(), 0.17F);
        }
    }

    @Test
    void unwindsAndTracksOppositeAngleWithActualIntegratingActuator() {
        DumpTruckMovementController movement = new DumpTruckMovementController();
        for (int i = 0; i < 20; i++) movement.step(0.3F, 1.0F);
        for (int i = 0; i < 30; i++) {
            movement.step(-0.3F, DumpTruckAutopilot.steeringInput(-6.0F, movement.steerAngle()));
        }
        assertEquals(-6.0F, movement.steerAngle(), 0.17F);
        for (int i = 0; i < 10; i++) {
            movement.step(0.0F, DumpTruckAutopilot.steeringInput(0.0F, movement.steerAngle()));
        }
        assertEquals(0.0F, movement.steerAngle(), 0.001F);
    }
}
