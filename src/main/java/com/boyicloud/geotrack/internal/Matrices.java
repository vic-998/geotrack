package com.boyicloud.geotrack.internal;

/**
 * Minimal dense matrix operations used by the Kalman smoother.
 *
 * <p>This is an internal helper; it is not part of the public API and may change
 * without notice.</p>
 */
public final class Matrices {

    private Matrices() {
    }

    public static double[][] identity(int n) {
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            m[i][i] = 1D;
        }
        return m;
    }

    public static double[] diagonal(double... values) {
        return values;
    }

    public static double[][] multiply(double[][] a, double[][] b) {
        int rows = a.length;
        int inner = b.length;
        int cols = b[0].length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < inner; k++) {
                double aik = a[i][k];
                if (aik == 0D) {
                    continue;
                }
                double[] brow = b[k];
                double[] rrow = result[i];
                for (int j = 0; j < cols; j++) {
                    rrow[j] += aik * brow[j];
                }
            }
        }
        return result;
    }

    public static double[] multiply(double[][] a, double[] v) {
        int rows = a.length;
        int cols = a[0].length;
        double[] result = new double[rows];
        for (int i = 0; i < rows; i++) {
            double sum = 0D;
            for (int j = 0; j < cols; j++) {
                sum += a[i][j] * v[j];
            }
            result[i] = sum;
        }
        return result;
    }

    public static double[] multiply(double[] v, double[][] a) {
        int cols = a[0].length;
        int rows = a.length;
        double[] result = new double[cols];
        for (int j = 0; j < cols; j++) {
            double sum = 0D;
            for (int i = 0; i < rows; i++) {
                sum += a[i][j] * v[i];
            }
            result[j] = sum;
        }
        return result;
    }

    public static double[][] transpose(double[][] a) {
        int rows = a.length;
        int cols = a[0].length;
        double[][] result = new double[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = a[i][j];
            }
        }
        return result;
    }

    public static double[][] add(double[][] a, double[][] b) {
        int rows = a.length;
        int cols = a[0].length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = a[i][j] + b[i][j];
            }
        }
        return result;
    }

    public static double[][] subtract(double[][] a, double[][] b) {
        int rows = a.length;
        int cols = a[0].length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = a[i][j] - b[i][j];
            }
        }
        return result;
    }

    /**
     * Inverts a square matrix with Gauss-Jordan elimination with partial pivoting.
     *
     * @throws ArithmeticException when the matrix is singular
     */
    public static double[][] inverse(double[][] matrix) {
        int n = matrix.length;
        double[][] m = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, m[i], 0, n);
            m[i][n + i] = 1D;
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;

            double pv = m[col][col];
            if (Math.abs(pv) < 1e-12) {
                throw new ArithmeticException("Singular matrix");
            }
            for (int j = 0; j < 2 * n; j++) {
                m[col][j] /= pv;
            }
            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = m[row][col];
                if (factor == 0D) {
                    continue;
                }
                for (int j = 0; j < 2 * n; j++) {
                    m[row][j] -= factor * m[col][j];
                }
            }
        }

        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(m[i], n, inverse[i], 0, n);
        }
        return inverse;
    }
}
