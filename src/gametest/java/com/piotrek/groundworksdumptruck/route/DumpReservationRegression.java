package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Regression for an active truck below the pad locking the entire unloading queue. */
public final class DumpReservationRegression {
    private DumpReservationRegression() {}

    public static void verify(ServerLevel level) {
        Vec3 bay = new Vec3(0, 81, 10);
        var owner = truck(level, 0);
        var waiting = truck(level, 20);
        try {
            require(DumpStationReservations.acquire(level, owner, bay), "First truck could not reserve bay");
            require(!DumpStationReservations.acquire(level, waiting, bay), "Two owners acquired the same bay");
            owner.setPos(0, 43, 10);
            require(owner.getAutopilot().isActive(), "Regression requires a still-active stranded truck");
            require(DumpStationReservations.acquire(level, waiting, bay), "Fallen truck left a ghost reservation");
            require(!DumpStationReservations.acquire(level, owner, bay), "Fallen truck reacquired the bay");
            DumpStationReservations.release(level, waiting);
            owner.setPos(0, 81, 10);
            require(DumpStationReservations.acquire(level, owner, bay), "Recovered truck could not acquire bay");
        } finally {
            owner.getAutopilot().stop();
            waiting.getAutopilot().stop();
            owner.discard();
            waiting.discard();
        }
    }

    private static GroundworksDumpTruckEntity truck(ServerLevel level, double x) {
        var truck = new GroundworksDumpTruckEntity(GroundworksDumpTruckMod.DUMP_TRUCK, level);
        truck.setPos(x, 81, 0);
        level.addFreshEntity(truck);
        truck.getAutopilot().startPlayback(new DumpTruckRoute("reservation-regression", List.of(
                new RouteWaypoint(x, 81, 0, 0, 0, 0, 0.2F, 0, false),
                new RouteWaypoint(0, 81, 10, 0, 0, 0, 0, 50, true))), false, true);
        return truck;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
