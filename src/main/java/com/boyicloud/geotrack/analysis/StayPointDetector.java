package com.boyicloud.geotrack.analysis;

import com.boyicloud.geotrack.geometry.GeoMath;
import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Stay point detection (Li et al., 2008).
 *
 * <p>A <b>stay point</b> is a cluster of consecutive samples where the moving
 * object stays within {@code distanceThresholdMeters} of the cluster's first point
 * for at least {@code timeThresholdSeconds}. The cluster is summarized by its
 * centroid and arrival/departure timestamps - the building block for inferring
 * places of interest, trip segmentation and stop-and-go analysis.</p>
 */
public final class StayPointDetector {

    private StayPointDetector() {
    }

    /**
     * A detected stay.
     *
     * @param lat             centroid latitude
     * @param lng             centroid longitude
     * @param arrivalMillis   timestamp of the first sample of the stay
     * @param departureMillis timestamp of the last sample of the stay
     * @param pointCount      number of samples in the stay
     */
    public record StayPoint(double lat, double lng, long arrivalMillis, long departureMillis, int pointCount) {

        /**
         * Dwell duration in seconds.
         */
        public double durationSeconds() {
            return (departureMillis - arrivalMillis) / 1000.0;
        }
    }

    /**
     * Detects stays using real timestamps.
     *
     * @param points              the track, ordered by {@code sequence}
     * @param distanceThreshold   radius in meters that keeps a point in the stay
     * @param timeThreshold       minimum dwell time in seconds
     * @param timestampsMillis    per-point epoch timestamps in milliseconds, same
     *                            length as {@code points}
     */
    public static List<StayPoint> detect(List<GeoPoint> points, double distanceThreshold,
                                         double timeThreshold, long[] timestampsMillis) {
        List<StayPoint> stays = new ArrayList<>();
        if (points == null || points.size() < 2 || timestampsMillis == null
                || timestampsMillis.length != points.size()) {
            return stays;
        }
        long timeThresholdMillis = (long) (timeThreshold * 1000);

        int i = 0;
        int n = points.size();
        while (i < n) {
            int j = i + 1;
            while (j < n && GeoMath.distance(points.get(i), points.get(j)) <= distanceThreshold) {
                j++;
            }
            boolean longEnough = j - 1 > i
                    && timestampsMillis[j - 1] - timestampsMillis[i] >= timeThresholdMillis;
            if (longEnough) {
                stays.add(summarize(points, i, j - 1, timestampsMillis));
                i = j;
            } else {
                i++;
            }
        }
        return stays;
    }

    /**
     * Detects stays treating the {@code sequence} of each point as a second-based
     * timeline. Convenient when no timestamps are attached to the track.
     */
    public static List<StayPoint> detectBySequence(List<GeoPoint> points, double distanceThreshold,
                                                   double timeThresholdSeconds) {
        if (points == null) {
            return List.of();
        }
        long[] timestamps = new long[points.size()];
        for (int i = 0; i < points.size(); i++) {
            timestamps[i] = points.get(i).sequence() * 1000L;
        }
        return detect(points, distanceThreshold, timeThresholdSeconds, timestamps);
    }

    private static StayPoint summarize(List<GeoPoint> points, int from, int to, long[] timestamps) {
        double sumLat = 0D;
        double sumLng = 0D;
        for (int k = from; k <= to; k++) {
            sumLat += points.get(k).lat();
            sumLng += points.get(k).lng();
        }
        int count = to - from + 1;
        return new StayPoint(sumLat / count, sumLng / count,
                timestamps[from], timestamps[to], count);
    }
}
