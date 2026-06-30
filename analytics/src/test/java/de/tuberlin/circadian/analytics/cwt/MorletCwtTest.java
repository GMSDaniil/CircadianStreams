package de.tuberlin.circadian.analytics.cwt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorletCwtTest {

    private static final double DT = 1.0 / 60.0; // 1-minute samples, in hours

    @Test
    void circadianBandDominatesFor24hRhythmButNotFor12h() {
        int n = 7200; // 5 days
        MorletCwt cwt = new MorletCwt(n, DT, 6.0, 0.5, 48.0, 8);

        double[] s24 = new double[n];
        double[] s12 = new double[n];
        for (int i = 0; i < n; i++) {
            double tH = i * DT;
            s24[i] = Math.cos(2.0 * Math.PI * tH / 24.0);
            s12[i] = Math.cos(2.0 * Math.PI * tH / 12.0);
        }

        double cri24 = cwt.circadianRhythmIndex(s24, 20.0, 28.0);
        double cri12 = cwt.circadianRhythmIndex(s12, 20.0, 28.0);

        assertTrue(cri24 > 0.3, "cri24=" + cri24);
        assertTrue(cri12 < 0.1, "cri12=" + cri12);
        assertTrue(cri24 > 3.0 * cri12, "cri24=" + cri24 + " cri12=" + cri12);
    }

    @Test
    void weakerRhythmGivesLowerCircadianPowerShare() {
        int n = 7200;
        MorletCwt cwt = new MorletCwt(n, DT, 6.0, 0.5, 48.0, 8);
        java.util.Random rng = new java.util.Random(7);

        double[] clean = new double[n];
        double[] noisy = new double[n];
        for (int i = 0; i < n; i++) {
            double tH = i * DT;
            double rhythm = Math.cos(2.0 * Math.PI * tH / 24.0);
            clean[i] = rhythm;
            noisy[i] = 0.3 * rhythm + rng.nextGaussian(); // weak rhythm, strong noise
        }

        double criClean = cwt.circadianRhythmIndex(clean, 20.0, 28.0);
        double criNoisy = cwt.circadianRhythmIndex(noisy, 20.0, 28.0);

        assertTrue(criClean > criNoisy, "criClean=" + criClean + " criNoisy=" + criNoisy);
    }

    @Test
    void flatSignalHasZeroCri() {
        int n = 2880;
        MorletCwt cwt = new MorletCwt(n, DT, 6.0, 0.5, 48.0, 8);
        double[] flat = new double[n];
        Arrays.fill(flat, 97.0);

        assertEquals(0.0, cwt.circadianRhythmIndex(flat, 20.0, 28.0), 1e-12);
    }
}
