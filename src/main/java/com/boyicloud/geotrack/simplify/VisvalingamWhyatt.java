package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.geometry.GeoMath;
import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Visvalingam-Whyatt line simplification.
 *
 * <p>Unlike Douglas-Peucker (which measures the maximum deviation from a chord),
 * this algorithm scores each vertex by the area of the triangle it forms with its
 * two neighbours and repeatedly removes the vertex with the smallest "effective
 * area". The effective area of a vertex is the maximum between its current
 * triangle area and the effective area of every vertex already removed next to it,
 * which keeps the removal order monotonic and yields a proper hierarchy - a nice
 * property Douglas-Peucker does not have.</p>
 */
public final class VisvalingamWhyatt {

    private VisvalingamWhyatt() {
    }

    /**
     * Simplifies down to {@code maxPoints} vertices (the two endpoints are always
     * kept). Larger areas survive first, so the most "important" corners remain.
     */
    public static List<GeoPoint> simplifyToCount(List<GeoPoint> points, int maxPoints) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (maxPoints < 2 || points.size() <= maxPoints) {
            return new ArrayList<>(points);
        }
        return run(points, maxPoints, Double.NaN);
    }

    /**
     * Removes every vertex whose effective triangle area is below
     * {@code minAreaMetersSquared}, keeping at least the two endpoints.
     */
    public static List<GeoPoint> simplifyByArea(List<GeoPoint> points, double minAreaMetersSquared) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        return run(points, 2, minAreaMetersSquared);
    }

    private static final class Node {
        final int index;
        final double area;

        Node(int index, double area) {
            this.index = index;
            this.area = area;
        }
    }

    private static List<GeoPoint> run(List<GeoPoint> points, int targetCount, double minArea) {
        int n = points.size();
        boolean[] removed = new boolean[n];
        double[] area = new double[n];
        int[] prev = new int[n];
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            prev[i] = i - 1;
            next[i] = i + 1;
        }

        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.area));
        for (int i = 1; i < n - 1; i++) {
            area[i] = GeoMath.triangleAreaMeters2(points.get(i - 1), points.get(i), points.get(i + 1));
            queue.add(new Node(i, area[i]));
        }

        int remaining = n;
        while (remaining > 2 && !queue.isEmpty()) {
            Node node = queue.poll();
            int i = node.index;
            if (removed[i] || Double.compare(node.area, area[i]) != 0) {
                continue;
            }
            if (Double.isNaN(minArea)) {
                if (remaining <= targetCount) {
                    break;
                }
            } else if (area[i] >= minArea) {
                break;
            }

            removed[i] = true;
            remaining--;

            int p = prev[i];
            int nx = next[i];
            if (p >= 0) {
                next[p] = nx;
            }
            if (nx < n) {
                prev[nx] = p;
            }

            int[] neighbours = {p, nx};
            for (int nb : neighbours) {
                if (nb > 0 && nb < n - 1 && !removed[nb]) {
                    int a = prev[nb];
                    int b = next[nb];
                    double newArea = GeoMath.triangleAreaMeters2(points.get(a), points.get(nb), points.get(b));
                    area[nb] = Math.max(newArea, area[i]);
                    queue.add(new Node(nb, area[nb]));
                }
            }
        }

        List<GeoPoint> result = new ArrayList<>(remaining);
        for (int i = 0; i < n; i++) {
            if (!removed[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }
}
