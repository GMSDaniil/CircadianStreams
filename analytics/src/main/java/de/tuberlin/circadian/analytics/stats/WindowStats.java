package de.tuberlin.circadian.analytics.stats;

import java.util.Arrays;

/**
 * Stage 2 window statistics: the robust summary of a tumbling window's samples.
 */
public final class WindowStats {

    private final double median;
    private final double sd;
    private final double completeness;
    private final int count;

    private WindowStats(double median, double sd, double completeness, int count) {
        this.median = median;
        this.sd = sd;
        this.completeness = completeness;
        this.count = count;
    }

    /**
     * @param values        the samples observed in the window (any order)
     * @param expectedCount how many samples a full window would contain (for completeness)
     */
    public static WindowStats of(double[] values, int expectedCount) {
        int n = values.length;
        if (n == 0) {
            return new WindowStats(Double.NaN, Double.NaN, 0.0, 0);
        }

        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double median = (n % 2 == 1) ? sorted[n / 2] : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);

        double mean = 0.0;
        for (double v : sorted) {
            mean += v;
        }
        mean /= n;

        double sd = 0.0;
        if (n > 1) {
            double ss = 0.0;
            for (double v : sorted) {
                double d = v - mean;
                ss += d * d;
            }
            sd = Math.sqrt(ss / (n - 1)); // sample standard deviation
        }

        double completeness = expectedCount > 0 ? Math.min(1.0, (double) n / expectedCount) : 0.0;
        return new WindowStats(median, sd, completeness, n);
    }

    public double median() {
        return median;
    }

    public double sd() {
        return sd;
    }

    public double completeness() {
        return completeness;
    }

    public int count() {
        return count;
    }
}