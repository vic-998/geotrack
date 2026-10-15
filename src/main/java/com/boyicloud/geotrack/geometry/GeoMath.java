package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

/**
 * Spherical and local-planar geodesic helpers.
 *
 * <p>Distances use the haversine formula on a sphere of mean Earth radius; the
 * error against a full ellipsoidal model is below 0.5% which is well beneath GPS
 * noise for track processing.</p>
 */
public final class GeoMath {

    private GeoMath() {
    }

    /** Mean Earth radius in meters (IUGG). */
    public static final double EARTH_RADIUS_METERS = 6371008.8D;

    /** Meters per degree of latitude (constant enough for local work). */
    public static final double METERS_PER_DEGREE_LAT = 110540.0D;

    /**
     * Great-circle distance in meters.
     */
    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1D, Math.sqrt(a)));
    }

    /**
     * Great-circle distance in meters between two points.
     */
    public static double distance(GeoPoint a, GeoPoint b) {
        return haversine(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /**
     * Total length of a polyline in meters.
     */
    public static double pathLength(java.util.List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return 0D;
        }
        double total = 0D;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
        }
        return total;
    }

    /**
     * Initial bearing (forward azimuth) from point 1 to point 2 in degrees [0, 360).
     */
    public static double initialBearing(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360D) % 360D;
    }

    /**
     * Meters per degree of longitude at the given latitude.
     */
    public static double metersPerDegreeLng(double lat) {
        return 111320.0D * Math.cos(Math.toRadians(lat));
    }

    /**
     * Projects a point to a local east/north plane (meters) relative to an origin.
     *
     * @return {@code [eastMeters, northMeters]}
     */
    public static double[] toLocalMeters(GeoPoint origin, GeoPoint point) {
        double east = (point.lng() - origin.lng()) * metersPerDegreeLng(origin.lat());
        double north = (point.lat() - origin.lat()) * METERS_PER_DEGREE_LAT;
        return new double[]{east, north};
    }

    /**
     * Converts local east/north meters back to a geographic coordinate.
     */
    public static GeoPoint fromLocalMeters(GeoPoint origin, double east, double north, GeoPoint template) {
        double lat = origin.lat() + north / METERS_PER_DEGREE_LAT;
        double lng = origin.lng() + east / metersPerDegreeLng(origin.lat());
        return new GeoPoint(lat, lng, template.altitude(), template.sequence());
    }

    /**
     * Area of the triangle formed by three points in square meters, using a local
     * equirectangular projection centered on the first point.
     */
    public static double triangleAreaMeters2(GeoPoint a, GeoPoint b, GeoPoint c) {
        double mLng = metersPerDegreeLng(a.lat());
        double bx = (b.lng() - a.lng()) * mLng;
        double by = (b.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        double cx = (c.lng() - a.lng()) * mLng;
        double cy = (c.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        return 0.5D * Math.abs(bx * cy - cx * by);
    }

    /**
     * Point-to-segment distance in meters (used by line simplification).
     */
    public static double pointToSegmentMeters(GeoPoint p, GeoPoint a, GeoPoint b) {
        double mLng = metersPerDegreeLng((a.lat() + b.lat()) / 2);
        double px = (p.lng() - a.lng()) * mLng;
        double py = (p.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        double bx = (b.lng() - a.lng()) * mLng;
        double by = (b.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        double lengthSquared = bx * bx + by * by;
        if (lengthSquared == 0D) {
            return Math.sqrt(px * px + py * py);
        }
        double t = Math.max(0D, Math.min(1D, (px * bx + py * by) / lengthSquared));
        double dx = px - t * bx;
        double dy = py - t * by;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
