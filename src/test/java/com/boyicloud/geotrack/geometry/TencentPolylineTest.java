package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TencentPolylineTest {

    @Test
    void decodesForwardDifferences() {
        List<double[]> decoded = TencentPolyline.decode(List.of(
                39.9, 116.4,
                10000.0, 10000.0));
        assertEquals(2, decoded.size());
        assertEquals(39.9, decoded.get(0)[0], 1e-9);
        assertEquals(116.4, decoded.get(0)[1], 1e-9);
        assertEquals(39.9 + 0.01, decoded.get(1)[0], 1e-9);
        assertEquals(116.4 + 0.01, decoded.get(1)[1], 1e-9);
    }

    @Test
    void decodeToPointsAssignsSequence() {
        List<GeoPoint> points = TencentPolyline.decodeToPoints(List.of(39.9, 116.4));
        assertEquals(1, points.size());
        assertEquals(1, points.get(0).sequence());
    }

    @Test
    void rejectsEmptyAndOddInput() {
        assertThrows(IllegalArgumentException.class, () -> TencentPolyline.decode(List.of()));
        assertThrows(IllegalArgumentException.class, () -> TencentPolyline.decode(List.of(1.0)));
    }
}
