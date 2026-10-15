package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SphericalCentroidTest {

    @Test
    void emptyInputReturnsNull() {
        assertNull(SphericalCentroid.centroid(List.of()));
    }

    @Test
    void symmetricPointsCenterAtOrigin() {
        List<GeoPoint> points = List.of(
                new GeoPoint(10, 0),
                new GeoPoint(-10, 0),
                new GeoPoint(0, 10),
                new GeoPoint(0, -10));
        double[] centroid = SphericalCentroid.centroid(points);
        assertNotNull(centroid);
        assertEquals(0.0, centroid[0], 1e-6);
        assertEquals(0.0, centroid[1], 1e-6);
    }

    @Test
    void weightedCentroidIsPulledTowardsHeavyPoint() {
        List<GeoPoint> points = List.of(
                new GeoPoint(0, 0),
                new GeoPoint(0, 10));
        double[] centroid = SphericalCentroid.weightedCentroid(points, new double[]{9, 1});
        assertNotNull(centroid);
        // the spherical mean is not exactly the arithmetic mean, but must stay near 1 degree
        assertTrue(centroid[1] > 0.99 && centroid[1] < 1.01, "was " + centroid[1]);
    }
}
