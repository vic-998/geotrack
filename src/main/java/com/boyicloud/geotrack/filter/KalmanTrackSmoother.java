package com.boyicloud.geotrack.filter;

import com.boyicloud.geotrack.geometry.GeoMath;
import com.boyicloud.geotrack.internal.Matrices;
import com.boyicloud.geotrack.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * GPS track denoising with a constant-velocity Kalman filter, optionally followed
 * by a Rauch-Tung-Striebel (RTS) backward smoother.
 *
 * <p>The filter runs in a local east/north plane (meters) anchored at the first
 * point, with a 4-state vector {@code [east, north, vEast, vNorth]}. The process
 * noise follows the continuous white-noise-acceleration model and the measurement
 * noise is the GPS accuracy in meters.</p>
 *
 * <p>The forward Kalman filter is causal and can still lag behind sharp turns; the
 * RTS smoother adds a backward pass and produces a minimum-variance estimate using
 * the whole track. Use {@link #smooth} for offline processing and {@link #filter}
 * for streaming/online processing.</p>
 */
public final class KalmanTrackSmoother {

    private KalmanTrackSmoother() {
    }

    /** Default acceleration standard deviation, m/s^2 (typical pedestrian motion). */
    public static final double DEFAULT_ACCEL_STD = 0.5D;

    /** Default GPS measurement noise, meters (1-sigma). */
    public static final double DEFAULT_MEASUREMENT_NOISE = 10.0D;

    /** Default time step between consecutive samples, seconds. */
    public static final double DEFAULT_TIME_STEP = 1.0D;

    /**
     * Causal Kalman filter with default tuning.
     */
    public static List<GeoPoint> filter(List<GeoPoint> points) {
        return filter(points, DEFAULT_ACCEL_STD, DEFAULT_MEASUREMENT_NOISE, DEFAULT_TIME_STEP);
    }

    /**
     * Kalman filter followed by an RTS backward smoother with default tuning.
     */
    public static List<GeoPoint> smooth(List<GeoPoint> points) {
        return smooth(points, DEFAULT_ACCEL_STD, DEFAULT_MEASUREMENT_NOISE, DEFAULT_TIME_STEP);
    }

    /**
     * Causal Kalman filter.
     *
     * @param points             the track
     * @param accelStd           process acceleration standard deviation, m/s^2
     * @param measurementNoise   GPS measurement standard deviation, meters
     * @param timeStep           time step between samples, seconds
     */
    public static List<GeoPoint> filter(List<GeoPoint> points, double accelStd,
                                        double measurementNoise, double timeStep) {
        return run(points, accelStd, measurementNoise, timeStep, false);
    }

    /**
     * Kalman filter + RTS smoother (minimum-variance estimate over the whole track).
     */
    public static List<GeoPoint> smooth(List<GeoPoint> points, double accelStd,
                                        double measurementNoise, double timeStep) {
        return run(points, accelStd, measurementNoise, timeStep, true);
    }

    private static List<GeoPoint> run(List<GeoPoint> points, double accelStd,
                                      double measurementNoise, double timeStep, boolean smoother) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        if (points.size() == 1) {
            return new ArrayList<>(points);
        }

        List<GeoPoint> ordered = new ArrayList<>(points);
        ordered.removeIf(Objects::isNull);
        ordered.sort(Comparator.comparingInt(GeoPoint::sequence));
        int n = ordered.size();

        GeoPoint origin = ordered.get(0);
        double mLng = GeoMath.metersPerDegreeLng(origin.lat());

        double[] zE = new double[n];
        double[] zN = new double[n];
        for (int i = 0; i < n; i++) {
            GeoPoint p = ordered.get(i);
            zE[i] = (p.lng() - origin.lng()) * mLng;
            zN[i] = (p.lat() - origin.lat()) * GeoMath.METERS_PER_DEGREE_LAT;
        }

        double dt = timeStep;
        double q = accelStd * accelStd;
        double r = measurementNoise * measurementNoise;

        double[][] f = {
                {1, 0, dt, 0},
                {0, 1, 0, dt},
                {0, 0, 1, 0},
                {0, 0, 0, 1}};
        double dt2 = dt * dt;
        double dt3 = dt2 * dt;
        double dt4 = dt2 * dt2;
        double[][] qm = {
                {q * dt4 / 4, 0, q * dt3 / 2, 0},
                {0, q * dt4 / 4, 0, q * dt3 / 2},
                {q * dt3 / 2, 0, q * dt2, 0},
                {0, q * dt3 / 2, 0, q * dt2}};
        double[][] h = {
                {1, 0, 0, 0},
                {0, 1, 0, 0}};
        double[][] rm = {
                {r, 0},
                {0, r}};
        double[][] identity = Matrices.identity(4);

        double[][] predX = new double[n][];
        double[][][] predP = new double[n][][];
        double[][] filtX = new double[n][];
        double[][][] filtP = new double[n][][];

        double[] x = {zE[0], zN[0], 0, 0};
        double[][] p = {
                {r, 0, 0, 0},
                {0, r, 0, 0},
                {0, 0, r, 0},
                {0, 0, 0, r}};

        for (int k = 0; k < n; k++) {
            if (k == 0) {
                predX[k] = x.clone();
                predP[k] = p;
                filtX[k] = x.clone();
                filtP[k] = p;
                continue;
            }

            double[] xPred = Matrices.multiply(f, x);
            double[][] pPred = Matrices.add(Matrices.multiply(Matrices.multiply(f, p), Matrices.transpose(f)), qm);

            double[] z = {zE[k], zN[k]};
            double[] y = {z[0] - xPred[0], z[1] - xPred[1]};
            double[][] s = Matrices.add(Matrices.multiply(Matrices.multiply(h, pPred), Matrices.transpose(h)), rm);
            double[][] kGain = Matrices.multiply(Matrices.multiply(pPred, Matrices.transpose(h)), Matrices.inverse(s));
            double[] kY = Matrices.multiply(kGain, y);
            double[] xFilt = {xPred[0] + kY[0], xPred[1] + kY[1], xPred[2] + kY[2], xPred[3] + kY[3]};
            double[][] pFilt = Matrices.multiply(Matrices.subtract(identity, Matrices.multiply(kGain, h)), pPred);

            predX[k] = xPred;
            predP[k] = pPred;
            filtX[k] = xFilt;
            filtP[k] = pFilt;
            x = xFilt;
            p = pFilt;
        }

        double[][] out = filtX;
        if (smoother) {
            double[][] smoothX = new double[n][];
            smoothX[n - 1] = filtX[n - 1].clone();
            for (int k = n - 2; k >= 0; k--) {
                double[][] c = Matrices.multiply(Matrices.multiply(filtP[k], Matrices.transpose(f)),
                        Matrices.inverse(predP[k + 1]));
                double[] diff = {
                        smoothX[k + 1][0] - predX[k + 1][0],
                        smoothX[k + 1][1] - predX[k + 1][1],
                        smoothX[k + 1][2] - predX[k + 1][2],
                        smoothX[k + 1][3] - predX[k + 1][3]};
                double[] cDiff = Matrices.multiply(c, diff);
                smoothX[k] = new double[]{
                        filtX[k][0] + cDiff[0],
                        filtX[k][1] + cDiff[1],
                        filtX[k][2] + cDiff[2],
                        filtX[k][3] + cDiff[3]};
            }
            out = smoothX;
        }

        List<GeoPoint> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double lat = origin.lat() + out[i][1] / GeoMath.METERS_PER_DEGREE_LAT;
            double lng = origin.lng() + out[i][0] / mLng;
            GeoPoint template = ordered.get(i);
            result.add(new GeoPoint(lat, lng, template.altitude(), template.sequence()));
        }
        return result;
    }
}
