package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.List;

/**
 * Weighted spherical centroid (mean of unit vectors).
 *
 * <p>Unlike a naive arithmetic mean of latitude/longitude, converting to 3D unit
 * vectors before averaging keeps the result correct across the antimeridian and
 * avoids latitude bias.</p>
 */
public final class SphericalCentroid {

    private SphericalCentroid() {
    }

    /**
     * Computes the centroid of equally weighted points.
     *
     * @param points the points
     * @return {@code [lat, lng]} in degrees, or {@code null} when there is no point
     */
    public static double[] centroid(List<GeoPoint> points) {
        return weightedCentroid(points, null);
    }

    /**
     * Computes the weighted centroid.
     *
     * @param points  the points
     * @param weights per-point weights, or {@code null} for equal weights; a shorter
     *                array means the trailing points are ignored
     * @return {@code [lat, lng]} in degrees, or {@code null} when there is no vertex
     * with a positive weight
     */
    public static double[] weightedCentroid(List<GeoPoint> points, double[] weights) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < points.size(); i++) {
            GeoPoint point = points.get(i);
            if (point == null) {
                continue;
            }
            double weight = 1.0;
            if (weights != null) {
                if (i >= weights.length) {
                    break;
                }
                weight = weights[i];
            }
            if (weight == 0.0) {
                continue;
            }

            double latRad = Math.toRadians(point.lat());
            double lonRad = Math.toRadians(point.lng());

            sumX += weight * Math.cos(latRad) * Math.cos(lonRad);
            sumY += weight * Math.cos(latRad) * Math.sin(lonRad);
            sumZ += weight * Math.sin(latRad);
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return null;
        }

        double avgX = sumX / totalWeight;
        double avgY = sumY / totalWeight;
        double avgZ = sumZ / totalWeight;

        double hyp = Math.sqrt(avgX * avgX + avgY * avgY);
        double lat = Math.atan2(avgZ, hyp);
        double lon = Math.atan2(avgY, avgX);

        return new double[]{Math.toDegrees(lat), Math.toDegrees(lon)};
    }
}
