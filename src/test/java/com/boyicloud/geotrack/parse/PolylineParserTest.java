package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolylineParserTest {

    @Test
    void parsesLatLngAltitudeTriples() {
        List<GeoPoint> points = PolylineParser.parse("39.9,116.4,50,39.91,116.41,52");
        assertEquals(2, points.size());
        assertEquals(39.9, points.get(0).lat(), 1e-9);
        assertEquals(116.4, points.get(0).lng(), 1e-9);
        assertEquals(50, points.get(0).altitude(), 1e-9);
        assertEquals(1, points.get(0).sequence());
        assertEquals(52, points.get(1).altitude(), 1e-9);
        assertEquals(2, points.get(1).sequence());
    }

    @Test
    void parsesLatLngPairs() {
        List<GeoPoint> points = PolylineParser.parse("39.9,116.4,39.91,116.41");
        assertEquals(2, points.size());
        assertEquals(0, points.get(0).altitude(), 1e-9);
    }

    @Test
    void blankInputReturnsEmptyList() {
        assertTrue(PolylineParser.parse(null).isEmpty());
        assertTrue(PolylineParser.parse("   ").isEmpty());
    }
}
