package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackSimilarityTest {

    private static List<GeoPoint> track(double latOffset) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            points.add(new GeoPoint(39.9 + latOffset, 116.4 + i * 0.001, 0, i));
        }
        return points;
    }

    @Test
    void identicalTracksHaveZeroDistance() {
        List<GeoPoint> a = track(0);
        assertEquals(0D, TrackSimilarity.discreteFrechet(a, a), 1e-6);
        assertEquals(0D, TrackSimilarity.hausdorff(a, a), 1e-6);
        assertEquals(0D, TrackSimilarity.dtw(a, a), 1e-6);
    }

    @Test
    void shiftedTrackHasPositiveDistance() {
        List<GeoPoint> a = track(0);
        List<GeoPoint> b = track(0.001);
        assertTrue(TrackSimilarity.discreteFrechet(a, b) > 100D);
        assertTrue(TrackSimilarity.hausdorff(a, b) > 100D);
        assertTrue(TrackSimilarity.dtw(a, b) > 100D);
    }

    @Test
    void frechetIsSymmetric() {
        List<GeoPoint> a = track(0);
        List<GeoPoint> b = track(0.001);
        assertEquals(TrackSimilarity.discreteFrechet(a, b), TrackSimilarity.discreteFrechet(b, a), 1e-6);
    }
}
