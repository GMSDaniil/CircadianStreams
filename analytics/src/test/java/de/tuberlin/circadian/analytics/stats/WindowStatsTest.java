package de.tuberlin.circadian.analytics.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowStatsTest {

    @Test
    void evenCountMedianAndSampleSd() {
        WindowStats s = WindowStats.of(new double[] {1, 2, 3, 4}, 4);
        assertEquals(2.5, s.median(), 1e-12);
        assertEquals(Math.sqrt(5.0 / 3.0), s.sd(), 1e-12); // sample sd (n-1)
        assertEquals(1.0, s.completeness(), 1e-12);
        assertEquals(4, s.count());
    }

    @Test
    void oddCountMedianAndPartialCompleteness() {
        WindowStats s = WindowStats.of(new double[] {5, 1, 3}, 6);
        assertEquals(3.0, s.median(), 1e-12);
        assertEquals(0.5, s.completeness(), 1e-12); // 3 of 6 expected
    }

    @Test
    void emptyWindowIsSafe() {
        WindowStats s = WindowStats.of(new double[] {}, 60);
        assertEquals(0, s.count());
        assertEquals(0.0, s.completeness(), 1e-12);
    }

    @Test
    void completenessCapsAtOne() {
        WindowStats s = WindowStats.of(new double[] {1, 2, 3, 4, 5}, 3);
        assertEquals(1.0, s.completeness(), 1e-12);
    }
}
