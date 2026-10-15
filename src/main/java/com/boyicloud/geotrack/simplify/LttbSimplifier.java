package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.geometry.GeoMath;
import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Largest-Triangle-Three-Buckets (LTTB) downsampling.
 *
 * <p>LTTB splits the track into buckets and, for every bucket, keeps the point that
 * forms the largest triangle with the previously selected point and the average of
 * the next bucket. It is O(n) and, compared to Douglas-Peucker, it preserves the
 * visual shape and the temporal distribution much better (no "clumping"), which is
 * why it is a popular choice for plotting large tracks.</p>
 *
 * <p>The classic algorithm works on a 2D series. Here the trajectory is projected
 * to a local east/north plane and the triangle area is computed in 3D including the
 * point index, so both the geographic shape and the time/index distribution are
 * preserved.</p>
 */
public final class LttbSimplifier {

    private LttbSimplifier() {
    }

    /**
     * Downsamples a track to at most {@code threshold} points, always keeping the
     * first and the last point.
     *
     * @param points    the track
     * @param threshold target number of points; when below 3 or above the input
     *                  size the input is returned unchanged
     */
    public static List<GeoPoint> downsample(List<GeoPoint> points, int threshold) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        int n = points.size();
        if (threshold >= n || threshold < 3) {
            return new ArrayList<>(points);
        }

        GeoPoint origin = points.get(0);
        double mLng = GeoMath.metersPerDegreeLng(origin.lat());
        double mLat = GeoMath.METERS_PER_DEGREE_LAT;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            GeoPoint p = points.get(i);
            x[i] = (p.lng() - origin.lng()) * mLng;
            y[i] = (p.lat() - origin.lat()) * mLat;
        }

        List<GeoPoint> sampled = new ArrayList<>(threshold);
        sampled.add(points.get(0));

        double every = (double) (n - 2) / (threshold - 2);
        int a = 0;
        for (int i = 0; i < threshold - 2; i++) {
            int currentStart = (int) Math.floor(i * every) + 1;
            int currentEnd = (int) Math.floor((i + 1) * every) + 1;
            int avgStart = currentEnd;
            int avgEnd = Math.min((int) Math.floor((i + 2) * every) + 1, n);

            double avgX = 0D;
            double avgY = 0D;
            int count = 0;
            for (int j = avgStart; j < avgEnd; j++) {
                avgX += x[j];
                avgY += y[j];
                count++;
            }
            if (count == 0) {
                avgX = x[n - 1];
                avgY = y[n - 1];
            } else {
                avgX /= count;
                avgY /= count;
            }

            int best = -1;
            double maxArea = -1D;
            for (int j = Math.min(currentStart, n - 2); j < Math.min(currentEnd, n - 1); j++) {
                double area = Math.abs((x[a] - avgX) * (y[j] - y[a]) - (x[a] - x[j]) * (avgY - y[a]));
                if (area > maxArea) {
                    maxArea = area;
                    best = j;
                }
            }
            if (best < 0) {
                best = Math.min(currentStart, n - 2);
            }
            sampled.add(points.get(best));
            a = best;
        }

        sampled.add(points.get(n - 1));
        return sampled;
    }
}
