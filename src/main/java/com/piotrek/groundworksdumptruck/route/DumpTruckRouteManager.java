package com.piotrek.groundworksdumptruck.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side persistent storage and lifecycle manager for Dump Truck routes.
 */
public final class DumpTruckRouteManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, DumpTruckRoute> CACHED_ROUTES = new ConcurrentHashMap<>();
    private static final Map<String, Vec3> ROUTE_STATION_SHIFTS = new ConcurrentHashMap<>();

    private DumpTruckRouteManager() {}

    public static void setRouteStationShift(String routeName, Vec3 shift) {
        if (routeName != null && !routeName.isBlank() && shift != null) {
            ROUTE_STATION_SHIFTS.put(routeName, shift);
        }
    }

    public static Vec3 getRouteStationShift(String routeName) {
        if (routeName == null || routeName.isBlank()) {
            return Vec3.ZERO;
        }
        return ROUTE_STATION_SHIFTS.getOrDefault(routeName, Vec3.ZERO);
    }

    public static Path getRoutesDirectory(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path routesDir = root.resolve("pw_groundworks_routes");
        if (!Files.exists(routesDir)) {
            try {
                Files.createDirectories(routesDir);
            } catch (Exception e) {
                GroundworksDumpTruckMod.LOGGER.error("Failed to create routes directory: {}", routesDir, e);
            }
        }
        return routesDir;
    }

    public static boolean saveRoute(MinecraftServer server, DumpTruckRoute route) {
        if (route == null || route.isEmpty()) {
            return false;
        }
        CACHED_ROUTES.put(route.name(), route);

        Path dir = getRoutesDirectory(server);
        Path file = dir.resolve(route.name() + ".json");
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(route.toJson(), writer);
            GroundworksDumpTruckMod.LOGGER.info("Saved route '{}' ({} waypoints, {:.1f}m) to {}",
                    route.name(), route.size(), route.totalLengthMeters(), file);
            return true;
        } catch (Exception e) {
            GroundworksDumpTruckMod.LOGGER.error("Failed to write route file: {}", file, e);
            return false;
        }
    }

    public static DumpTruckRoute getRoute(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        DumpTruckRoute cached = CACHED_ROUTES.get(name);
        if (cached != null) {
            return cached;
        }

        Path dir = getRoutesDirectory(server);
        Path file = dir.resolve(name + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            DumpTruckRoute loaded = DumpTruckRoute.fromJson(json);
            CACHED_ROUTES.put(loaded.name(), loaded);
            return loaded;
        } catch (Exception e) {
            GroundworksDumpTruckMod.LOGGER.error("Failed to read route file: {}", file, e);
            return null;
        }
    }

    public static List<String> listRouteNames(MinecraftServer server) {
        Set<String> names = new TreeSet<>(CACHED_ROUTES.keySet());
        Path dir = getRoutesDirectory(server);
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            String fileName = p.getFileName().toString();
                            names.add(fileName.substring(0, fileName.length() - 5));
                        });
            } catch (Exception e) {
                GroundworksDumpTruckMod.LOGGER.error("Failed to list route directory: {}", dir, e);
            }
        }
        return new ArrayList<>(names);
    }

    public static boolean deleteRoute(MinecraftServer server, String name) {
        CACHED_ROUTES.remove(name);
        Path dir = getRoutesDirectory(server);
        Path file = dir.resolve(name + ".json");
        if (Files.exists(file)) {
            try {
                Files.delete(file);
                return true;
            } catch (Exception e) {
                GroundworksDumpTruckMod.LOGGER.error("Failed to delete route file: {}", file, e);
                return false;
            }
        }
        return false;
    }

    /**
     * Broadcast an excavator horn signal to all dump trucks in range.
     * When the excavator blows its long horn ("pełna kipa"), nearby trucks that are
     * waiting under the excavator or armed to follow a route will start their playback.
     */
    public static void onExcavatorHorn(ServerLevel level, double x, double y, double z) {
        Vec3 origin = new Vec3(x, y, z);
        double radius = 28.0D;
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);

        List<GroundworksDumpTruckEntity> trucks = level.getEntitiesOfClass(
                GroundworksDumpTruckEntity.class, box, e -> !e.isRemoved()
        );

        for (GroundworksDumpTruckEntity truck : trucks) {
            truck.onExcavatorHornSignal(origin);
        }
    }
}
