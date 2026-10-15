package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Google "Encoded Polyline Algorithm Format" codec.
 *
 * <p>Coordinates are delta encoded, scaled by {@code 10^precision}, and written as
 * variable-length 5-bit chunks offset by 63 into printable ASCII. Precision 5
 * (about 1.1 m) is the Google default; precision 6 (about 11 cm) is common for
 * high-resolution tracks.</p>
 *
 * @see <a href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">Google Encoded Polyline</a>
 */
public final class EncodedPolyline {

    private EncodedPolyline() {
    }

    public static final int DEFAULT_PRECISION = 5;

    /**
     * Encodes a track with {@link #DEFAULT_PRECISION} (5).
     */
    public static String encode(List<GeoPoint> points) {
        return encode(points, DEFAULT_PRECISION);
    }

    /**
     * Encodes a track.
     *
     * @param points    the track
     * @param precision number of decimal digits to keep (usually 5 or 6)
     */
    public static String encode(List<GeoPoint> points, int precision) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        long factor = (long) Math.pow(10, precision);
        StringBuilder sb = new StringBuilder(points.size() * 6);
        long lastLat = 0;
        long lastLng = 0;
        for (GeoPoint p : points) {
            long lat = Math.round(p.lat() * factor);
            long lng = Math.round(p.lng() * factor);
            encodeValue(lat - lastLat, sb);
            encodeValue(lng - lastLng, sb);
            lastLat = lat;
            lastLng = lng;
        }
        return sb.toString();
    }

    /**
     * Decodes with {@link #DEFAULT_PRECISION} (5).
     */
    public static List<GeoPoint> decode(String encoded) {
        return decode(encoded, DEFAULT_PRECISION);
    }

    /**
     * Decodes an encoded polyline into points with a 1-based sequence.
     */
    public static List<GeoPoint> decode(String encoded, int precision) {
        List<GeoPoint> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        double factor = Math.pow(10, precision);
        int index = 0;
        int length = encoded.length();
        long lat = 0;
        long lng = 0;
        while (index < length) {
            int[] shift = {0};
            long result = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (long) (b & 0x1f) << shift[0];
                shift[0] += 5;
            } while (b >= 0x20);
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            shift[0] = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (long) (b & 0x1f) << shift[0];
                shift[0] += 5;
            } while (b >= 0x20);
            lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            points.add(new GeoPoint(lat / factor, lng / factor, 0D, points.size() + 1));
        }
        return points;
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
