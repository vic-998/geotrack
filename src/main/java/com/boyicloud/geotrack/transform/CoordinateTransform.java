package com.boyicloud.geotrack.transform;

import com.boyicloud.geotrack.model.GeoPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinate system conversion utilities.
 *
 * <p>Currently supports the WGS84 -&gt; GCJ02 ("Mars coordinates") conversion used
 * by Chinese map providers. Points outside China are returned unchanged.</p>
 *
 * <p>All conversion methods return {@code [lng, lat]} in decimal degrees and round
 * to 6 decimals.</p>
 */
public final class CoordinateTransform {

    private CoordinateTransform() {
    }

    private static final double PI = 3.1415926535897932384626;

    /** Baidu BD09 magic constant. */
    private static final double X_PI = PI * 3000.0 / 180.0;

    /** Semi-major axis of the reference ellipsoid (Krasovsky 1940). */
    private static final double A = 6378245.0;

    /** Square of the first eccentricity. */
    private static final double EE = 0.00669342162296594323;

    private static final int SCALE = 6;

    /**
     * Converts a WGS84 coordinate to GCJ02.
     *
     * @param lng WGS84 longitude
     * @param lat WGS84 latitude
     * @return {@code [lng, lat]} in the GCJ02 coordinate system
     */
    public static double[] transformWGS84ToGCJ02(double lng, double lat) {
        if (Double.isNaN(lng) || Double.isNaN(lat)) {
            return new double[]{lng, lat};
        }
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }

        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);

        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);

        double gcjLat = new BigDecimal(lat + dLat).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
        double gcjLng = new BigDecimal(lng + dLng).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();

        return new double[]{gcjLng, gcjLat};
    }

    /**
     * Converts a WGS84 coordinate given as strings to GCJ02.
     *
     * @throws IllegalArgumentException when the input cannot be parsed as a number
     */
    public static double[] transformWGS84ToGCJ02(String lngStr, String latStr) {
        try {
            BigDecimal lngDecimal = new BigDecimal(lngStr);
            BigDecimal latDecimal = new BigDecimal(latStr);
            return transformWGS84ToGCJ02(lngDecimal.doubleValue(), latDecimal.doubleValue());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid lng/lat: " + lngStr + ", " + latStr, e);
        }
    }

    /**
     * Batch WGS84 -&gt; GCJ02 conversion. Returns a new list; the input is not modified.
     * The {@code altitude} and {@code sequence} of each point are preserved.
     */
    public static List<GeoPoint> transformBatch(List<GeoPoint> points) {
        if (points == null) {
            return List.of();
        }
        List<GeoPoint> result = new ArrayList<>(points.size());
        for (GeoPoint point : points) {
            double[] gcj = transformWGS84ToGCJ02(point.lng(), point.lat());
            result.add(new GeoPoint(gcj[1], gcj[0], point.altitude(), point.sequence()));
        }
        return result;
    }

    /**
     * Returns {@code true} when the coordinate lies outside mainland China and
     * therefore requires no GCJ02 offset.
     */
    public static boolean outOfChina(double lng, double lat) {
        return (lng < 72.004 || lng > 137.8347) || (lat < 0.8293 || lat > 55.8271);
    }

    /**
     * Converts GCJ02 back to WGS84.
     *
     * <p>The GCJ02 offset has no closed-form inverse, so it is solved iteratively:
     * start from the GCJ02 value, apply the forward transform, and correct the
     * estimate by the residual. A handful of iterations converge to sub-centimeter
     * accuracy.</p>
     *
     * @return {@code [lng, lat]} in WGS84
     */
    public static double[] transformGCJ02ToWGS84(double gcjLng, double gcjLat) {
        if (Double.isNaN(gcjLng) || Double.isNaN(gcjLat) || outOfChina(gcjLng, gcjLat)) {
            return new double[]{gcjLng, gcjLat};
        }
        double lng = gcjLng;
        double lat = gcjLat;
        for (int i = 0; i < 10; i++) {
            double[] forward = transformWGS84ToGCJ02(lng, lat);
            lng += gcjLng - forward[0];
            lat += gcjLat - forward[1];
        }
        return new double[]{
                round6(lng),
                round6(lat)};
    }

    /**
     * Converts GCJ02 to BD09 (Baidu).
     *
     * @return {@code [lng, lat]} in BD09
     */
    public static double[] transformGCJ02ToBD09(double gcjLng, double gcjLat) {
        double z = Math.sqrt(gcjLng * gcjLng + gcjLat * gcjLat) + 0.00002 * Math.sin(gcjLat * X_PI);
        double theta = Math.atan2(gcjLat, gcjLng) + 0.000003 * Math.cos(gcjLng * X_PI);
        double bdLng = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{round6(bdLng), round6(bdLat)};
    }

    /**
     * Converts BD09 back to GCJ02.
     *
     * @return {@code [lng, lat]} in GCJ02
     */
    public static double[] transformBD09ToGCJ02(double bdLng, double bdLat) {
        double x = bdLng - 0.0065;
        double y = bdLat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        double gcjLng = z * Math.cos(theta);
        double gcjLat = z * Math.sin(theta);
        return new double[]{round6(gcjLng), round6(gcjLat)};
    }

    /**
     * Converts WGS84 to BD09 (WGS84 -&gt; GCJ02 -&gt; BD09).
     */
    public static double[] transformWGS84ToBD09(double lng, double lat) {
        double[] gcj = transformWGS84ToGCJ02(lng, lat);
        return transformGCJ02ToBD09(gcj[0], gcj[1]);
    }

    /**
     * Converts BD09 to WGS84 (BD09 -&gt; GCJ02 -&gt; WGS84).
     */
    public static double[] transformBD09ToWGS84(double bdLng, double bdLat) {
        double[] gcj = transformBD09ToGCJ02(bdLng, bdLat);
        return transformGCJ02ToWGS84(gcj[0], gcj[1]);
    }

    private static double round6(double value) {
        return new BigDecimal(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
