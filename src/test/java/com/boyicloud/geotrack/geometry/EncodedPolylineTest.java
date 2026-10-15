package com.boyicloud.geotrack.geometry;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodedPolylineTest {

    /**
     * The canonical example from the Google Encoded Polyline documentation.
     */
    @Test
    void encodesGoogleReferenceExample() {
        List<GeoPoint> points = List.of(
                new GeoPoint(38.5, -120.2),
                new GeoPoint(40.7, -120.95),
                new GeoPoint(43.252, -126.453));
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", EncodedPolyline.encode(points, 5));
    }

    @Test
    void roundTripsAtPrecisionFive() {
        List<GeoPoint> points = List.of(
                new GeoPoint(38.5, -120.2),
                new GeoPoint(40.7, -120.95),
                new GeoPoint(43.252, -126.453));
        List<GeoPoint> decoded = EncodedPolyline.decode(EncodedPolyline.encode(points), 5);
        assertEquals(points.size(), decoded.size());
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).lat(), decoded.get(i).lat(), 1e-5);
            assertEquals(points.get(i).lng(), decoded.get(i).lng(), 1e-5);
        }
    }

    @Test
    void roundTripsAtHigherPrecision() {
        List<GeoPoint> points = List.of(new GeoPoint(39.908722, 116.397499));
        List<GeoPoint> decoded = EncodedPolyline.decode(EncodedPolyline.encode(points, 6), 6);
        assertEquals(39.908722, decoded.get(0).lat(), 1e-6);
        assertEquals(116.397499, decoded.get(0).lng(), 1e-6);
    }
}
