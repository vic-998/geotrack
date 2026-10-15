package com.boyicloud.geotrack.transform;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateTransformTest {

    @Test
    void wgs84ToGcj02OffsetsInsideChina() {
        double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
        assertTrue(Math.abs(gcj[0] - 116.397428) > 1e-4);
        assertTrue(Math.abs(gcj[1] - 39.90923) > 1e-4);
        assertEquals(116.403622, gcj[0], 1e-3);
        assertEquals(39.910522, gcj[1], 1e-3);
    }

    @Test
    void coordinatesOutsideChinaAreUnchanged() {
        double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(0.0, 0.0);
        assertArrayEquals(new double[]{0.0, 0.0}, gcj);
    }

    @Test
    void outOfChinaBoundaries() {
        assertTrue(CoordinateTransform.outOfChina(0, 0));
        assertFalse(CoordinateTransform.outOfChina(116.4, 39.9));
    }

    @Test
    void gcj02InverseRecoversWgs84() {
        double lng = 116.397428;
        double lat = 39.90923;
        double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(lng, lat);
        double[] back = CoordinateTransform.transformGCJ02ToWGS84(gcj[0], gcj[1]);
        assertEquals(lng, back[0], 1e-5);
        assertEquals(lat, back[1], 1e-5);
    }

    @Test
    void bd09RoundTripRecoversGcj02() {
        double[] bd = CoordinateTransform.transformGCJ02ToBD09(116.404, 39.915);
        double[] gcj = CoordinateTransform.transformBD09ToGCJ02(bd[0], bd[1]);
        assertEquals(116.404, gcj[0], 1e-4);
        assertEquals(39.915, gcj[1], 1e-4);
    }

    @Test
    void wgs84ToBd09CombinesTwoHops() {
        double[] bd = CoordinateTransform.transformWGS84ToBD09(116.397428, 39.90923);
        assertTrue(bd[0] > 116.397428 && bd[1] > 39.90923);
    }

    @Test
    void batchConversionPreservesAltitudeAndSequence() {
        List<GeoPoint> source = List.of(
                new GeoPoint(39.90923, 116.397428, 50, 7),
                new GeoPoint(31.2304, 121.4737, 8, 9));
        List<GeoPoint> result = CoordinateTransform.transformBatch(source);
        assertEquals(50, result.get(0).altitude());
        assertEquals(7, result.get(0).sequence());
        assertEquals(9, result.get(1).sequence());
        assertTrue(Math.abs(result.get(0).lng() - 116.397428) > 1e-4);
    }
}
