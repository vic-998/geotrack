package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LttbSimplifierTest {

    @Test
    void returnsInputWhenThresholdTooLarge() {
        List<GeoPoint> points = List.of(
                new GeoPoint(0, 0), new GeoPoint(0.001, 0.001), new GeoPoint(0.002, 0.002));
        assertEquals(points, LttbSimplifier.downsample(points, 10));
    }

    @Test
    void producesExactNumberOfPointsAndKeepsEndpoints() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            points.add(new GeoPoint(39.9 + Math.sin(i / 20.0) * 0.01, 116.4 + i * 0.00005, 0, i));
        }
        List<GeoPoint> sampled = LttbSimplifier.downsample(points, 100);
        assertEquals(100, sampled.size());
        assertEquals(points.get(0), sampled.get(0));
        assertEquals(points.get(points.size() - 1), sampled.get(sampled.size() - 1));
    }

    @Test
    void keepsSignificantSpike() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double lat = i == 50 ? 40.0 : 39.9;
            points.add(new GeoPoint(lat, 116.4 + i * 0.0001, 0, i));
        }
        List<GeoPoint> sampled = LttbSimplifier.downsample(points, 10);
        assertTrue(sampled.stream().anyMatch(p -> p.lat() == 40.0),
                "the spike should survive downsampling");
    }
}
