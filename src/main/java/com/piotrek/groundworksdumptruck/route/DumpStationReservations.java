package com.piotrek.groundworksdumptruck.route;

import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-thread-only, dimension-scoped ownership of physical tipping bays. */
final class DumpStationReservations {
    private static final Map<ServerLevel, Map<UUID, Vec3>> OWNERS = new WeakHashMap<>();

    static boolean acquire(ServerLevel level, GroundworksDumpTruckEntity truck, Vec3 position) {
        Map<UUID, Vec3> owners = OWNERS.computeIfAbsent(level, ignored -> new HashMap<>());
        owners.entrySet().removeIf(entry -> !(level.getEntity(entry.getKey()) instanceof GroundworksDumpTruckEntity owner)
                || owner.isRemoved() || !owner.getAutopilot().isActive()
                || Math.abs(owner.getY() - entry.getValue().y) > 4.0D);
        // A truck stranded below the pad must not reserve a bay it cannot reach.
        if (Math.abs(truck.getY() - position.y) > 4.0D) return false;
        for (var entry : owners.entrySet()) {
            if (!entry.getKey().equals(truck.getUUID()) && entry.getValue().distanceToSqr(position) < 64.0D) {
                return false;
            }
        }
        owners.put(truck.getUUID(), position);
        return true;
    }

    static void release(ServerLevel level, GroundworksDumpTruckEntity truck) {
        Map<UUID, Vec3> owners = OWNERS.get(level);
        if (owners != null) owners.remove(truck.getUUID());
    }
}
