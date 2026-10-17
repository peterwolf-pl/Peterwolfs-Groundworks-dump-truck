package com.piotrek.groundworksdumptruck.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable sequence of waypoints representing an entire recorded haul cycle.
 */
public class DumpTruckRoute {
    private final String name;
    private final List<RouteWaypoint> waypoints;
    private final double[] cumulativeDistances;
    private final double totalLengthMeters;
    private final boolean hasDumping;
    private final boolean isLoop;

    public DumpTruckRoute(String name, List<RouteWaypoint> waypoints) {
        this.name = name;
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));

        int n = this.waypoints.size();
        this.cumulativeDistances = new double[n];

        double length = 0.0D;
        boolean dumping = false;
        for (int i = 0; i < n; i++) {
            RouteWaypoint current = this.waypoints.get(i);
            if (current.dumping() || current.bedAngle() > 18.0F) {
                dumping = true;
            }
            if (i > 0) {
                RouteWaypoint prev = this.waypoints.get(i - 1);
                length += prev.distanceTo(current.pos());
            }
            this.cumulativeDistances[i] = length;
        }
        this.totalLengthMeters = length;
        this.hasDumping = dumping;
        this.isLoop = n > 2 && this.waypoints.get(0).horizontalDistanceTo(this.waypoints.get(n - 1).pos()) < 18.0D;
    }

    public String name() {
        return name;
    }

    public List<RouteWaypoint> waypoints() {
        return waypoints;
    }

    public int size() {
        return waypoints.size();
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public double totalLengthMeters() {
        return totalLengthMeters;
    }

    public boolean hasDumping() {
        return hasDumping;
    }

    public boolean isLoop() {
        return isLoop;
    }

    public double getDistanceFromStart(int index) {
        if (cumulativeDistances.length == 0) return 0.0D;
        int clamped = Math.clamp(index, 0, cumulativeDistances.length - 1);
        return cumulativeDistances[clamped];
    }

    public double getDistanceFromEnd(int index) {
        if (cumulativeDistances.length == 0) return 0.0D;
        int clamped = Math.clamp(index, 0, cumulativeDistances.length - 1);
        return Math.max(0.0D, totalLengthMeters - cumulativeDistances[clamped]);
    }

    public RouteWaypoint get(int index) {
        if (waypoints.isEmpty()) {
            return null;
        }
        int clamped = Math.clamp(index, 0, waypoints.size() - 1);
        return waypoints.get(clamped);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("totalLength", Math.round(totalLengthMeters * 100.0D) / 100.0D);
        json.addProperty("hasDumping", hasDumping);
        JsonArray array = new JsonArray();
        for (RouteWaypoint wp : waypoints) {
            array.add(wp.toJson());
        }
        json.add("waypoints", array);
        return json;
    }

    public static DumpTruckRoute fromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        JsonArray array = json.getAsJsonArray("waypoints");
        List<RouteWaypoint> wps = new ArrayList<>(array != null ? array.size() : 0);
        if (array != null) {
            for (JsonElement elem : array) {
                if (elem.isJsonObject()) {
                    wps.add(RouteWaypoint.fromJson(elem.getAsJsonObject()));
                }
            }
        }
        return new DumpTruckRoute(name, wps);
    }
}
