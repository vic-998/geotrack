package com.boyicloud.geotrack.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeohashTest {

    @Test
    void encodesKnownReferenceValue() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11));
    }

    @Test
    void decodeReturnsCenterNearOriginal() {
        double[] center = Geohash.decode("u4pruydqqvj");
        assertTrue(Math.abs(center[0] - 57.64911) < 0.001);
        assertTrue(Math.abs(center[1] - 10.40744) < 0.001);
    }

    @Test
    void longerPrefixNarrowsTheBox() {
        double[][] coarse = Geohash.decodeBounds("u4p");
        double[][] fine = Geohash.decodeBounds("u4pru");
        assertTrue((fine[0][1] - fine[0][0]) < (coarse[0][1] - coarse[0][0]));
    }
}
