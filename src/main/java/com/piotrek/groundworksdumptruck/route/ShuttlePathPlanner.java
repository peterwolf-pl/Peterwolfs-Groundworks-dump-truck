package com.piotrek.groundworksdumptruck.route;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Forward-only, bounded-curvature pose paths. Does not pivot a wheeled truck in place. */
final class ShuttlePathPlanner {
    static final double RADIUS = 9.0D;
    record Point(Vec3 position, float yaw) {}
    record Path(List<Point> points, double length) {}
    private record Segments(int first, int last, double a, double straight, double b) {}

    private ShuttlePathPlanner() {}

    static List<Path> candidates(Vec3 start, float startYaw, Vec3 goal, float goalYaw) {
        double dx = goal.x - start.x, dz = goal.z - start.z;
        double d = Math.hypot(dx, dz) / RADIUS;
        double bearing = Math.atan2(dz, dx);
        double alpha = wrap(Math.toRadians(startYaw) + Math.PI / 2 - bearing);
        double beta = wrap(Math.toRadians(goalYaw) + Math.PI / 2 - bearing);
        double sa = Math.sin(alpha), sb = Math.sin(beta), ca = Math.cos(alpha), cb = Math.cos(beta);
        double cab = Math.cos(alpha - beta);
        List<Segments> segments = new ArrayList<>();
        double p2 = 2 + d * d - 2 * cab + 2 * d * (sa - sb);
        if (p2 >= 0) {
            double t = Math.atan2(cb - ca, d + sa - sb);
            segments.add(new Segments(1, 1, wrap(-alpha + t), Math.sqrt(p2), wrap(beta - t)));
        }
        p2 = 2 + d * d - 2 * cab + 2 * d * (sb - sa);
        if (p2 >= 0) {
            double t = Math.atan2(ca - cb, d - sa + sb);
            segments.add(new Segments(-1, -1, wrap(alpha - t), Math.sqrt(p2), wrap(-beta + t)));
        }
        p2 = -2 + d * d + 2 * cab + 2 * d * (sa + sb);
        if (p2 >= 0) {
            double p = Math.sqrt(p2);
            double t = Math.atan2(-ca - cb, d + sa + sb) - Math.atan2(-2, p);
            segments.add(new Segments(1, -1, wrap(-alpha + t), p, wrap(-beta + t)));
        }
        p2 = d * d - 2 + 2 * cab - 2 * d * (sa + sb);
        if (p2 >= 0) {
            double p = Math.sqrt(p2);
            double t = Math.atan2(ca + cb, d - sa - sb) - Math.atan2(2, p);
            segments.add(new Segments(-1, 1, wrap(alpha - t), p, wrap(beta - t)));
        }
        List<Path> paths = new ArrayList<>();
        for (Segments s : segments) {
            double length = (s.a + s.straight + s.b) * RADIUS;
            if (length > 250) continue;
            List<Point> points = new ArrayList<>();
            points.add(new Point(start, startYaw));
            append(points, s.a * RADIUS, s.first / RADIUS);
            append(points, s.straight * RADIUS, 0);
            append(points, s.b * RADIUS, s.last / RADIUS);
            Point end = points.getLast();
            if (end.position.subtract(goal).horizontalDistanceSqr() < 0.001D)
                paths.add(new Path(List.copyOf(points), length));
        }
        paths.sort(Comparator.comparingDouble(Path::length));
        return paths;
    }

    private static void append(List<Point> points, double length, double curvature) {
        int count = Math.max(1, (int) Math.ceil(length / 0.6D));
        double step = length / count;
        for (int i = 0; i < count; i++) {
            Point previous = points.getLast();
            double heading = Math.toRadians(previous.yaw) + Math.PI / 2;
            double next = heading + curvature * step;
            double x = curvature == 0 ? Math.cos(heading) * step : (Math.sin(next) - Math.sin(heading)) / curvature;
            double z = curvature == 0 ? Math.sin(heading) * step : (Math.cos(heading) - Math.cos(next)) / curvature;
            points.add(new Point(previous.position.add(x, 0, z), (float) Math.toDegrees(next - Math.PI / 2)));
        }
    }

    private static double wrap(double angle) {
        return angle - Math.floor(angle / (Math.PI * 2)) * Math.PI * 2;
    }
}
