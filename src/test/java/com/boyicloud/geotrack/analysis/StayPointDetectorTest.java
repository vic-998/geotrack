package com.boyicloud.geotrack.analysis;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StayPointDetectorTest {

    @Test
    void detectsTwoStaysSeparatedByTransit() {
        List<GeoPoint> points = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < 40; i++) {
            points.add(new GeoPoint(39.9, 116.4, 0, seq++));
        }
        for (int i = 0; i < 20; i++) {
            points.add(new GeoPoint(39.905, 116.405, 0, seq++));
        }
        for (int i = 0; i < 40; i++) {
            points.add(new GeoPoint(39.91, 116.41, 0, seq++));
        }

        List<StayPointDetector.StayPoint> stays =
                StayPointDetector.detectBySequence(points, 50D, 30D);

        assertEquals(2, stays.size());
        assertEquals(39.9, stays.get(0).lat(), 1e-6);
        assertEquals(39.91, stays.get(1).lat(), 1e-6);
        assertEquals(40, stays.get(0).pointCount());
        assertEquals(39.0, stays.get(0).durationSeconds(), 1e-6);
    }

    @Test
    void noStayForShortStop() {
        List<GeoPoint> points = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < 10; i++) {
            points.add(new GeoPoint(39.9, 116.4, 0, seq++));
        }
        assertEquals(0, StayPointDetector.detectBySequence(points, 50D, 30D).size());
    }
}
