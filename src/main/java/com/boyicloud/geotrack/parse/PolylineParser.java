package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for comma separated coordinate strings.
 *
 * <p>Two layouts are supported:</p>
 * <ul>
 *     <li>{@code lat,lng} repeated -&gt; altitude defaults to {@code 0};</li>
 *     <li>{@code lat,lng,alt} repeated -&gt; altitude is kept.</li>
 * </ul>
 *
 * <p>The layout is detected from the element count (a multiple of 3 is treated as
 * the 3-tuple layout). {@code sequence} starts at 1.</p>
 */
public final class PolylineParser {

    private PolylineParser() {
    }

    /**
     * Parses a comma separated coordinate string.
     *
     * @param polyline e.g. {@code "39.9,116.4,50,39.91,116.41,52"}
     * @return parsed points, or an empty list for {@code null}/blank input
     */
    public static List<GeoPoint> parse(String polyline) {
        if (polyline == null || polyline.isBlank()) {
            return Collections.emptyList();
        }
        String[] coordinates = polyline.trim().split(",");
        boolean hasAltitude = coordinates.length % 3 == 0;
        int group = hasAltitude ? 3 : 2;
        if (coordinates.length < group) {
            return Collections.emptyList();
        }

        List<GeoPoint> points = new ArrayList<>(coordinates.length / group);
        for (int i = 0; i + group <= coordinates.length; i += group) {
            double lat = Double.parseDouble(coordinates[i].trim());
            double lng = Double.parseDouble(coordinates[i + 1].trim());
            double altitude = hasAltitude ? Double.parseDouble(coordinates[i + 2].trim()) : 0D;
            points.add(new GeoPoint(lat, lng, altitude, i / group + 1));
        }
        return points;
    }
}
