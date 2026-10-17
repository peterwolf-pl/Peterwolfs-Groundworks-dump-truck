package com.piotrek.groundworksdumptruck.route;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

/**
 * A single recorded waypoint along a dump truck haul route.
 *
 * <p>Tracks position, orientation, speed, bed angle, and dumping flag.
 * Authoritative units and microvoxels are handled by Groundworks; this record
 * stores the kinematics and bed actions needed to reproduce the run.</p>
 */
public record RouteWaypoint(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float speed,
        float bedAngle,
        boolean dumping,
        boolean reverse
) {
    public RouteWaypoint(
            double x, double y, double z,
            float yaw, float pitch, float roll,
            float speed, float bedAngle, boolean dumping
    ) {
        this(x, y, z, yaw, pitch, roll, speed, bedAngle, dumping, speed < -0.005F);
    }

    public boolean isReverse() {
        return reverse || speed < -0.005F;
    }
    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public double distanceTo(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double horizontalDistanceTo(Vec3 other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", Math.round(x * 1000.0D) / 1000.0D);
        json.addProperty("y", Math.round(y * 1000.0D) / 1000.0D);
        json.addProperty("z", Math.round(z * 1000.0D) / 1000.0D);
        json.addProperty("yaw", Math.round(yaw * 100.0F) / 100.0F);
        json.addProperty("pitch", Math.round(pitch * 100.0F) / 100.0F);
        json.addProperty("roll", Math.round(roll * 100.0F) / 100.0F);
        json.addProperty("speed", Math.round(speed * 1000.0F) / 1000.0F);
        json.addProperty("bedAngle", Math.round(bedAngle * 10.0F) / 10.0F);
        json.addProperty("dumping", dumping);
        json.addProperty("reverse", isReverse());
        return json;
    }

    public static RouteWaypoint fromJson(JsonObject json) {
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 0.0F;
        boolean reverse = json.has("reverse")
                ? json.get("reverse").getAsBoolean()
                : speed < -0.005F;
        return new RouteWaypoint(
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble(),
                json.get("yaw").getAsFloat(),
                json.has("pitch") ? json.get("pitch").getAsFloat() : 0.0F,
                json.has("roll") ? json.get("roll").getAsFloat() : 0.0F,
                speed,
                json.has("bedAngle") ? json.get("bedAngle").getAsFloat() : 0.0F,
                json.has("dumping") && json.get("dumping").getAsBoolean(),
                reverse
        );
    }
}
