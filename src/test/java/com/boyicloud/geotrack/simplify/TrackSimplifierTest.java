package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackSimplifierTest {

    private static List<GeoPoint> straightTrack(int size) {
        List<GeoPoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            points.add(new GeoPoint(39.9 + i * 0.0001, 116.4 + i * 0.0001, 0, i));
        }
        return points;
    }

    @Test
    void returnsInputWhenAlreadyWithinBudget() {
        List<GeoPoint> points = straightTrack(10);
        assertSame(points, TrackSimplifier.simplify(points, 10));
    }

    @Test
    void collinearTrackCollapsesToEndpoints() {
        List<GeoPoint> points = straightTrack(1000);
        List<GeoPoint> simplified = TrackSimplifier.simplify(points, 50);
        assertTrue(simplified.size() <= 50);
        assertEquals(points.get(0), simplified.get(0));
        assertEquals(points.get(points.size() - 1), simplified.get(simplified.size() - 1));
    }

    @Test
    void respectsMaxPointsForZigZagTrack() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            double lat = 39.9 + (i % 2 == 0 ? 0 : 0.001);
            points.add(new GeoPoint(lat, 116.4 + i * 0.0001, 0, i));
        }
        List<GeoPoint> simplified = TrackSimplifier.simplify(points, 20);
        assertTrue(simplified.size() <= 20, "expected <= 20 but was " + simplified.size());
    }

    @Test
    void outputKeepsOriginalOrder() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            points.add(new GeoPoint(39.9 + Math.sin(i / 5.0) * 0.01, 116.4 + i * 0.0001, 0, i));
        }
        List<GeoPoint> simplified = TrackSimplifier.simplify(points, 30);
        for (int i = 1; i < simplified.size(); i++) {
            assertTrue(simplified.get(i - 1).sequence() < simplified.get(i).sequence());
        }
    }

    @Test
    void simplifyByToleranceDropsCollinearPoints() {
        List<GeoPoint> points = straightTrack(200);
        List<GeoPoint> simplified = TrackSimplifier.simplifyByTolerance(points, 1.0);
        assertEquals(2, simplified.size());
    }
}
