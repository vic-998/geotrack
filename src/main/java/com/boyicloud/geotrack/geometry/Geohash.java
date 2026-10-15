package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

/**
 * Geohash encoding / decoding (base-32, Gustavo Niemeyer's system).
 *
 * <p>A geohash encodes a coordinate into a short string where a longer shared
 * prefix means a smaller bounding box: prefix matching is a cheap proximity test
 * and a great fit for database indexing / sharding.</p>
 */
public final class Geohash {

    private Geohash() {
    }

    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    /**
     * Encodes a coordinate with the given number of characters (1-12).
     */
    public static String encode(double lat, double lng, int precision) {
        if (precision < 1) {
            throw new IllegalArgumentException("precision must be >= 1");
        }
        double[] latRange = {-90D, 90D};
        double[] lngRange = {-180D, 180D};
        StringBuilder sb = new StringBuilder(precision);
        boolean even = true;
        int bit = 0;
        int ch = 0;
        while (sb.length() < precision) {
            if (even) {
                double mid = (lngRange[0] + lngRange[1]) / 2;
                if (lng >= mid) {
                    ch = (ch << 1) | 1;
                    lngRange[0] = mid;
                } else {
                    ch = ch << 1;
                    lngRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2;
                if (lat >= mid) {
                    ch = (ch << 1) | 1;
                    latRange[0] = mid;
                } else {
                    ch = ch << 1;
                    latRange[1] = mid;
                }
            }
            even = !even;
            if (++bit == 5) {
                sb.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return sb.toString();
    }

    /**
     * Encodes a point.
     */
    public static String encode(GeoPoint point, int precision) {
        return encode(point.lat(), point.lng(), precision);
    }

    /**
     * Decodes a geohash to the center coordinate {@code [lat, lng]}.
     */
    public static double[] decode(String geohash) {
        double[][] bounds = decodeBounds(geohash);
        double lat = (bounds[0][0] + bounds[0][1]) / 2;
        double lng = (bounds[1][0] + bounds[1][1]) / 2;
        return new double[]{lat, lng};
    }

    /**
     * Decodes a geohash to its bounding box
     * {@code [[minLat, maxLat], [minLng, maxLng]]}.
     */
    public static double[][] decodeBounds(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("geohash must not be empty");
        }
        double[] latRange = {-90D, 90D};
        double[] lngRange = {-180D, 180D};
        boolean even = true;
        for (int i = 0; i < geohash.length(); i++) {
            int cd = indexOf(Character.toLowerCase(geohash.charAt(i)));
            if (cd < 0) {
                throw new IllegalArgumentException("Invalid geohash character: " + geohash.charAt(i));
            }
            for (int mask = 16; mask > 0; mask >>= 1) {
                if (even) {
                    refine(lngRange, (cd & mask) != 0);
                } else {
                    refine(latRange, (cd & mask) != 0);
                }
                even = !even;
            }
        }
        return new double[][]{latRange, lngRange};
    }

    private static void refine(double[] range, boolean upper) {
        double mid = (range[0] + range[1]) / 2;
        if (upper) {
            range[0] = mid;
        } else {
            range[1] = mid;
        }
    }

    private static int indexOf(char c) {
        for (int i = 0; i < BASE32.length; i++) {
            if (BASE32[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
