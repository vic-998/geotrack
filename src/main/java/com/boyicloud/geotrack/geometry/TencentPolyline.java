package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for the compressed polyline returned by the Tencent Map direction API.
 *
 * <p>The API sends coordinates as a flat {@code [lat, lng, lat, lng, ...]} list where
 * every element except the first coordinate is a forward difference scaled by
 * {@code 1e6}. Decoding accumulates those deltas back into absolute coordinates.</p>
 */
public final class TencentPolyline {

    private TencentPolyline() {
    }

    /**
     * Decodes the compressed coordinate array.
     *
     * @param compressed flat array emitted by Tencent Map ({@code [lat, lng, ...]})
     * @return decoded coordinates as {@code [lat, lng]} pairs
     * @throws IllegalArgumentException when the array is {@code null}, empty or has an odd length
     */
    public static List<double[]> decode(List<Double> compressed) {
        if (compressed == null || compressed.isEmpty()) {
            throw new IllegalArgumentException("Compressed polyline must not be empty");
        }
        if (compressed.size() % 2 != 0) {
            throw new IllegalArgumentException("Compressed polyline length must be even");
        }

        List<double[]> result = new ArrayList<>(compressed.size() / 2);
        double lastLat = 0;
        double lastLng = 0;
        for (int i = 0; i < compressed.size(); i += 2) {
            double lat = compressed.get(i);
            double lng = compressed.get(i + 1);
            if (i >= 2) {
                lat = lastLat + lat / 1000000;
                lng = lastLng + lng / 1000000;
            }
            result.add(new double[]{lat, lng});
            lastLat = lat;
            lastLng = lng;
        }
        return result;
    }

    /**
     * Same as {@link #decode(List)} but returns {@link GeoPoint}s with a 1-based sequence.
     */
    public static List<GeoPoint> decodeToPoints(List<Double> compressed) {
        List<double[]> coordinates = decode(compressed);
        List<GeoPoint> points = new ArrayList<>(coordinates.size());
        for (int i = 0; i < coordinates.size(); i++) {
            double[] coordinate = coordinates.get(i);
            points.add(new GeoPoint(coordinate[0], coordinate[1], 0D, i + 1));
        }
        return points;
    }
}
