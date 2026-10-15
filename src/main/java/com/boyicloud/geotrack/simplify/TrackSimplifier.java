package com.boyicloud.geotrack.simplify;

import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * GPS track simplification based on the Douglas-Peucker algorithm.
 *
 * <p>Design notes:</p>
 * <ul>
 *     <li>Iterative (explicit stack) instead of recursive, so very long tracks
 *     do not blow the call stack;</li>
 *     <li>the result keeps the original order (the first and the last point are
 *     always kept) and can be persisted directly;</li>
 *     <li>when a point budget is given, the tolerance is found by binary search
 *     so the result is as detailed as possible while staying under the budget;</li>
 *     <li>fallback: if even the largest tolerance exceeds the budget, the track
 *     degrades to even sampling, guaranteeing the budget is never exceeded.</li>
 * </ul>
 */
public final class TrackSimplifier {

    private TrackSimplifier() {
    }

    /**
     * Upper bound (meters) of the tolerance search. The larger the tolerance the
     * fewer points are kept, so the relation is monotonic and a binary search can
     * find the smallest tolerance that still fits the point budget.
     */
    private static final double MAX_TOLERANCE = 2000D;

    /**
     * Binary search stops once the tolerance interval is smaller than this (meters).
     */
    private static final double MIN_TOLERANCE_STEP = 0.5D;

    /**
     * Hard cap on binary search rounds to bound the worst case cost.
     */
    private static final int MAX_SEARCH_ROUNDS = 18;

    /**
     * Simplifies a track so that it keeps at most {@code maxPoints} points while
     * preserving as much detail as possible.
     *
     * @param points    source track (ordered by {@code sequence})
     * @param maxPoints target upper bound; when below 2 or already satisfied the
     *                  input is returned unchanged
     * @return simplified track in original order, first and last point always kept
     */
    public static List<GeoPoint> simplify(List<GeoPoint> points, int maxPoints) {
        return simplify(points, maxPoints, 0D);
    }

    /**
     * Simplifies a track with a baseline tolerance.
     *
     * @param points                  source track (ordered by {@code sequence})
     * @param maxPoints               target upper bound
     * @param baselineToleranceMeters baseline tolerance in meters. GPS noise is
     *                                usually 3~10 meters, so a 5 meter baseline
     *                                first removes jitter that is not trustworthy;
     *                                the binary search only kicks in if the budget
     *                                is still exceeded. Use {@code 0} to keep as
     *                                much detail as the budget allows.
     * @return simplified track in original order, first and last point always kept
     */
    public static List<GeoPoint> simplify(List<GeoPoint> points, int maxPoints,
                                          double baselineToleranceMeters) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        if (maxPoints < 2 || points.size() <= maxPoints) {
            return points;
        }
        List<GeoPoint> ordered = sortBySequence(points);
        try {
            if (baselineToleranceMeters > 0) {
                List<GeoPoint> baseline = douglasPeucker(ordered, baselineToleranceMeters);
                if (baseline.size() <= maxPoints) {
                    return baseline;
                }
            }
            List<GeoPoint> fitted = fitToLimit(ordered, maxPoints);
            return fitted != null ? fitted : sampleEvenly(ordered, maxPoints);
        } catch (Exception e) {
            return sampleEvenly(ordered, maxPoints);
        }
    }

    /**
     * Simplifies using a fixed tolerance without enforcing a point budget.
     * Useful to de-noise a track at the GPS noise level: jitter below the
     * tolerance is a positioning error and removing it loses no information.
     *
     * @param points          source track
     * @param toleranceMeters tolerance in meters, returns the input when &lt;= 0
     */
    public static List<GeoPoint> simplifyByTolerance(List<GeoPoint> points, double toleranceMeters) {
        if (points == null) {
            return Collections.emptyList();
        }
        if (points.size() <= 2 || toleranceMeters <= 0) {
            return points;
        }
        try {
            List<GeoPoint> simplified = douglasPeucker(sortBySequence(points), toleranceMeters);
            return simplified.size() >= 2 ? simplified : points;
        } catch (Exception e) {
            return points;
        }
    }

    /**
     * Binary search for the smallest tolerance that yields at most {@code maxPoints}
     * points, i.e. the densest result that fits the budget.
     */
    private static List<GeoPoint> fitToLimit(List<GeoPoint> ordered, int maxPoints) {
        double low = 0D;
        double high = MAX_TOLERANCE;
        List<GeoPoint> best = null;
        for (int round = 0; round < MAX_SEARCH_ROUNDS && high - low > MIN_TOLERANCE_STEP; round++) {
            double mid = (low + high) / 2;
            List<GeoPoint> candidate = douglasPeucker(ordered, mid);
            if (candidate.size() <= maxPoints) {
                best = candidate;
                high = mid;
            } else {
                low = mid;
            }
        }
        return best;
    }

    /**
     * Iterative Douglas-Peucker: keeps the extreme points whose distance to the
     * segment between the range endpoints exceeds {@code tolerance} meters. The
     * result is emitted in ascending original index order.
     */
    public static List<GeoPoint> douglasPeucker(List<GeoPoint> points, double tolerance) {
        int size = points.size();
        if (size <= 2) {
            return new ArrayList<>(points);
        }
        double[] lat = new double[size];
        double[] lng = new double[size];
        for (int i = 0; i < size; i++) {
            GeoPoint point = points.get(i);
            lat[i] = point.lat();
            lng[i] = point.lng();
        }

        boolean[] keep = new boolean[size];
        keep[0] = true;
        keep[size - 1] = true;

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, size - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int start = range[0];
            int end = range[1];
            if (end - start < 2) {
                continue;
            }
            double maxDistance = -1D;
            int maxIndex = -1;
            for (int i = start + 1; i < end; i++) {
                double distance = pointToSegmentMeters(lat[i], lng[i],
                        lat[start], lng[start], lat[end], lng[end]);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    maxIndex = i;
                }
            }
            if (maxIndex > start && maxDistance > tolerance) {
                keep[maxIndex] = true;
                stack.push(new int[]{start, maxIndex});
                stack.push(new int[]{maxIndex, end});
            }
        }

        List<GeoPoint> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    /**
     * Point-to-segment distance in meters. Longitude/latitude are projected to a
     * local plane using an equirectangular approximation; within a few dozen
     * kilometers the error does not affect the simplification decision.
     */
    private static double pointToSegmentMeters(double pLat, double pLng,
                                               double aLat, double aLng,
                                               double bLat, double bLng) {
        double midLatRad = Math.toRadians((aLat + bLat) / 2);
        double metersPerLng = 111320D * Math.cos(midLatRad);
        double metersPerLat = 110540D;

        double px = (pLng - aLng) * metersPerLng;
        double py = (pLat - aLat) * metersPerLat;
        double bx = (bLng - aLng) * metersPerLng;
        double by = (bLat - aLat) * metersPerLat;

        double lengthSquared = bx * bx + by * by;
        if (lengthSquared == 0D) {
            return Math.sqrt(px * px + py * py);
        }
        double t = (px * bx + py * by) / lengthSquared;
        t = Math.max(0D, Math.min(1D, t));
        double dx = px - t * bx;
        double dy = py - t * by;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Even-sampling fallback: guarantees at most {@code maxPoints} points while
     * keeping the first and the last point.
     */
    private static List<GeoPoint> sampleEvenly(List<GeoPoint> points, int maxPoints) {
        int size = points.size();
        LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
        double step = (double) (size - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int index = (int) Math.round(i * step);
            if (index >= 0 && index < size) {
                indexes.add(index);
            }
        }
        indexes.add(size - 1);

        List<GeoPoint> result = new ArrayList<>(indexes.size());
        for (Integer index : indexes) {
            result.add(points.get(index));
        }
        return result;
    }

    /**
     * Sorts by {@code sequence}; points with an equal sequence keep their input
     * order (stable sort), so raw lists without sequence data are left untouched.
     */
    private static List<GeoPoint> sortBySequence(List<GeoPoint> points) {
        List<GeoPoint> ordered = new ArrayList<>(points);
        ordered.removeIf(java.util.Objects::isNull);
        ordered.sort(Comparator.comparingInt(GeoPoint::sequence));
        return ordered;
    }
}
