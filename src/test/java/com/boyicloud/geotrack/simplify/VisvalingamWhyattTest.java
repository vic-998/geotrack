package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisvalingamWhyattTest {

    private static List<GeoPoint> straightTrack(int size) {
        List<GeoPoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            points.add(new GeoPoint(39.9 + i * 0.0001, 116.4 + i * 0.0001, 0, i));
        }
        return points;
    }

    @Test
    void collinearTrackCollapsesToEndpointsByArea() {
        List<GeoPoint> points = straightTrack(100);
        // collinear vertices have zero triangle area, so any positive threshold removes them all
        List<GeoPoint> simplified = VisvalingamWhyatt.simplifyByArea(points, 1e-6);
        assertEquals(2, simplified.size());
    }

    @Test
    void countBudgetIsExact() {
        List<GeoPoint> points = straightTrack(100);
        assertEquals(10, VisvalingamWhyatt.simplifyToCount(points, 10).size());
    }

    @Test
    void respectsPointBudget() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            points.add(new GeoPoint(39.9 + Math.sin(i / 4.0) * 0.01, 116.4 + i * 0.0001, 0, i));
        }
        List<GeoPoint> simplified = VisvalingamWhyatt.simplifyToCount(points, 40);
        assertTrue(simplified.size() <= 40);
        assertEquals(points.get(0), simplified.get(0));
        assertEquals(points.get(points.size() - 1), simplified.get(simplified.size() - 1));
    }

    @Test
    void simplifyByAreaKeepsSharpCorners() {
        List<GeoPoint> points = List.of(
                new GeoPoint(0, 0, 0, 0),
                new GeoPoint(0, 0.000001, 0, 1),
                new GeoPoint(0, 0.01, 0, 2),
                new GeoPoint(0, 0.010001, 0, 3));
        List<GeoPoint> simplified = VisvalingamWhyatt.simplifyByArea(points, 1.0);
        assertEquals(2, simplified.size());
    }
}
