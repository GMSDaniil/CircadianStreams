package de.tuberlin.circadian.analytics.nonparam;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsIvTest {

    private static final int BINS = 24; // hourly bins for a fast test

    @Test
    void cleanRhythmHasHighIsLowIv() {
        int days = 7;
        int n = days * BINS;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = Math.cos(2.0 * Math.PI * i / BINS); // identical every day
        }

        IsIvResult r = IsIv.compute(x, BINS);

        assertEquals(1.0, r.interdailyStability(), 1e-6); // perfectly reproducible
        assertTrue(r.intradailyVariability() < 0.2, "iv=" + r.intradailyVariability());
    }

    @Test
    void noisyFragmentedSignalHasLowIsHighIv() {
        int days = 7;
        int n = days * BINS;
        Random rng = new Random(11);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rng.nextGaussian(); // no day-to-day structure, jumps every step
        }

        IsIvResult r = IsIv.compute(x, BINS);

        assertTrue(r.interdailyStability() < 0.5, "is=" + r.interdailyStability());
        assertTrue(r.intradailyVariability() > 1.0, "iv=" + r.intradailyVariability());
    }
}
