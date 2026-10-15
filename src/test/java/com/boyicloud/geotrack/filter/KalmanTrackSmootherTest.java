package com.boyicloud.geotrack.filter;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KalmanTrackSmootherTest {

    private static List<GeoPoint> noisyStraightTrack(int size, double noiseDegrees, long seed) {
        Random random = new Random(seed);
        List<GeoPoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double lat = 39.9 + i * 0.0002 + (random.nextGaussian() * noiseDegrees);
            double lng = 116.4 + i * 0.0002 + (random.nextGaussian() * noiseDegrees);
            points.add(new GeoPoint(lat, lng, 0, i));
        }
        return points;
    }

    private static double meanDeviation(List<GeoPoint> points, double baseLat, double baseLng) {
        double sum = 0D;
        for (GeoPoint p : points) {
            sum += Math.hypot(p.lat() - baseLat, p.lng() - baseLng);
        }
        return sum / points.size();
    }

    @Test
    void smoothingReducesNoise() {
        int size = 200;
        double noise = 0.0001;
        List<GeoPoint> noisy = noisyStraightTrack(size, noise, 42L);

        List<GeoPoint> smoothed = KalmanTrackSmoother.smooth(noisy);
        assertEquals(size, smoothed.size());

        double noisyDeviation = 0D;
        double smoothedDeviation = 0D;
        for (int i = 0; i < size; i++) {
            double trueLat = 39.9 + i * 0.0002;
            double trueLng = 116.4 + i * 0.0002;
            noisyDeviation += Math.hypot(noisy.get(i).lat() - trueLat, noisy.get(i).lng() - trueLng);
            smoothedDeviation += Math.hypot(smoothed.get(i).lat() - trueLat, smoothed.get(i).lng() - trueLng);
        }
        assertTrue(smoothedDeviation < noisyDeviation * 0.7,
                "smoothing should cut the deviation, noisy=" + noisyDeviation + " smoothed=" + smoothedDeviation);
    }

    @Test
    void preservesSequenceAndAltitude() {
        List<GeoPoint> noisy = noisyStraightTrack(20, 0.0001, 7L);
        List<GeoPoint> smoothed = KalmanTrackSmoother.filter(noisy);
        for (int i = 0; i < noisy.size(); i++) {
            assertEquals(noisy.get(i).sequence(), smoothed.get(i).sequence());
            assertEquals(noisy.get(i).altitude(), smoothed.get(i).altitude());
        }
    }

    @Test
    void singlePointReturnedUnchanged() {
        List<GeoPoint> single = List.of(new GeoPoint(39.9, 116.4, 5, 0));
        assertEquals(single, KalmanTrackSmoother.smooth(single));
    }
}
