package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.List;

/**
 * Distance / similarity metrics between two tracks.
 *
 * <p>All metrics are measured in meters:</p>
 * <ul>
 *     <li><b>Discrete Fr&eacute;chet distance</b> - the classic "dog walking"
 *     distance, order-preserving and sensitive to the actual path, the best fit for
 *     comparing trajectories;</li>
 *     <li><b>Hausdorff distance</b> - the worst mismatch between the two point sets
 *     (order-insensitive);</li>
 *     <li><b>Dynamic Time Warping (DTW)</b> - order-preserving but allows time
 *     warping, good when the same route is traversed at different speeds.</li>
 * </ul>
 */
public final class TrackSimilarity {

    private TrackSimilarity() {
    }

    /**
     * Discrete Fr&eacute;chet distance in meters (O(n*m) time, O(min(n,m)) space).
     */
    public static double discreteFrechet(List<GeoPoint> a, List<GeoPoint> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException("Tracks must not be empty");
        }
        int n = a.size();
        int m = b.size();
        double[] prev = new double[m];
        double[] cur = new double[m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double d = GeoMath.distance(a.get(i), b.get(j));
                if (i == 0 && j == 0) {
                    cur[j] = d;
                } else if (i == 0) {
                    cur[j] = Math.max(cur[j - 1], d);
                } else if (j == 0) {
                    cur[j] = Math.max(prev[j], d);
                } else {
                    double min = Math.min(Math.min(prev[j], prev[j - 1]), cur[j - 1]);
                    cur[j] = Math.max(min, d);
                }
            }
            double[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m - 1];
    }

    /**
     * Symmetric Hausdorff distance in meters.
     */
    public static double hausdorff(List<GeoPoint> a, List<GeoPoint> b) {
        return Math.max(directedHausdorff(a, b), directedHausdorff(b, a));
    }

    /**
     * Dynamic Time Warping distance in meters (O(n*m) time, O(min(n,m)) space).
     */
    public static double dtw(List<GeoPoint> a, List<GeoPoint> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException("Tracks must not be empty");
        }
        int n = a.size();
        int m = b.size();
        double[] prev = new double[m + 1];
        double[] cur = new double[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = Double.POSITIVE_INFINITY;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = Double.POSITIVE_INFINITY;
            for (int j = 1; j <= m; j++) {
                double cost = GeoMath.distance(a.get(i - 1), b.get(j - 1));
                double best = Math.min(Math.min(prev[j], prev[j - 1]), cur[j - 1]);
                cur[j] = (best == Double.POSITIVE_INFINITY ? 0D : best) + cost;
            }
            double[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    private static double directedHausdorff(List<GeoPoint> a, List<GeoPoint> b) {
        double worst = 0D;
        for (GeoPoint pa : a) {
            double nearest = Double.POSITIVE_INFINITY;
            for (GeoPoint pb : b) {
                nearest = Math.min(nearest, GeoMath.distance(pa, pb));
            }
            worst = Math.max(worst, nearest);
        }
        return worst;
    }
}
