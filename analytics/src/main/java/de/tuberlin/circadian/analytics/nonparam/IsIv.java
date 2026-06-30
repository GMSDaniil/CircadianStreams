package de.tuberlin.circadian.analytics.nonparam;

/**
 * Inter-daily Stability (IS) and Intra-daily Variability (IV), the non-parametric circadian metrics
 * of Witting et al. (Stage 3c). Computed directly on the aggregated series with no model
 * assumption — unlike Cosinor (cosine) or CWT (wavelet).
 */
public final class IsIv {

    private IsIv() { }

    /**
     * @param x          the aggregated series
     * @param binsPerDay number of samples per 24 h
     */
    public static IsIvResult compute(double[] x, int binsPerDay) {
        int n = x.length;
        if (binsPerDay <= 0) {
            throw new IllegalArgumentException("binsPerDay must be > 0");
        }
        if (n < 2 * binsPerDay) {
            throw new IllegalArgumentException("IS/IV needs at least 2 days, got " + n + " points");
        }

        double mean = 0.0;
        for (double v : x) {
            mean += v;
        }
        mean /= n;

        double overallSs = 0.0;
        for (double v : x) {
            double d = v - mean;
            overallSs += d * d;
        }
        if (overallSs == 0.0) {
            return new IsIvResult(Double.NaN, 0.0); // flat signal: IS undefined
        }

        // IS: per-bin averages across days.
        int p = binsPerDay;
        double[] binSum = new double[p];
        int[] binCount = new int[p];
        for (int i = 0; i < n; i++) {
            int b = i % p;
            binSum[b] += x[i];
            binCount[b]++;
        }
        double binSs = 0.0;
        for (int b = 0; b < p; b++) {
            double binMean = binSum[b] / binCount[b];
            double d = binMean - mean;
            binSs += d * d;
        }
        double is = (n * binSs) / (p * overallSs);

        // IV: mean squared first difference.
        double diffSs = 0.0;
        for (int i = 1; i < n; i++) {
            double d = x[i] - x[i - 1];
            diffSs += d * d;
        }
        double iv = (n * diffSs) / ((n - 1) * overallSs);

        return new IsIvResult(is, iv);
    }
}