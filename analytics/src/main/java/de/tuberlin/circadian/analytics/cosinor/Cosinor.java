package de.tuberlin.circadian.analytics.cosinor;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

// Single-component Cosinor analysis with a fixed 24-hour period (Stage 3a).
public final class Cosinor {

    private static final double PERIOD_HOURS = 24.0;

    private Cosinor() {
    }

    /**
     * Fit the 24 h cosine to {@code (tHours, values)}. 
     *
     * @param tHours timestamps in hours
     * @param values the aggregated signal (e.g. the 1-min median series)
     */
    public static CosinorResult fit(double[] tHours, double[] values) {
        if (tHours.length != values.length) {
            throw new IllegalArgumentException("tHours and values must have equal length");
        }
        int n = values.length;
        if (n < 3) {
            throw new IllegalArgumentException("Cosinor needs at least 3 points, got " + n);
        }

        double[][] basis = new double[n][2];
        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * tHours[i] / PERIOD_HOURS;
            basis[i][0] = Math.cos(angle);
            basis[i][1] = Math.sin(angle);
        }

        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        ols.newSampleData(values, basis);
        double[] beta = ols.estimateRegressionParameters();

        double mesor = beta[0];
        double b1 = beta[1];
        double b2 = beta[2];
        double amplitude = Math.hypot(b1, b2);
        double acrophase = Math.atan2(-b2, b1);
        double r2 = ols.calculateRSquared();

        return new CosinorResult(mesor, amplitude, acrophase, r2);
    }
}