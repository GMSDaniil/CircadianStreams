package de.tuberlin.circadian.analytics.cosinor;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosinorTest {

    @Test
    void recoversKnownParametersFromCleanCosine() {
        double mesor = 70.0;
        double amplitude = 8.0;
        double phi = -2.0 * Math.PI * 15.0 / 24.0; // peak at 15:00
        int n = 48;
        double[] t = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i; // hourly
            y[i] = mesor + amplitude * Math.cos(2.0 * Math.PI * t[i] / 24.0 + phi);
        }

        CosinorResult r = Cosinor.fit(t, y);

        assertEquals(mesor, r.mesor(), 1e-6);
        assertEquals(amplitude, r.amplitude(), 1e-6);
        // compare phase via cos/sin to avoid 2*pi wrap-around
        assertEquals(Math.cos(phi), Math.cos(r.acrophaseRad()), 1e-6);
        assertEquals(Math.sin(phi), Math.sin(r.acrophaseRad()), 1e-6);
        assertEquals(1.0, r.r2(), 1e-6);
    }

    @Test
    void flatSignalHasZeroAmplitude() {
        int n = 48;
        double[] t = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i;
            y[i] = 100.0;
        }

        CosinorResult r = Cosinor.fit(t, y);

        assertEquals(0.0, r.amplitude(), 1e-6);
        assertEquals(100.0, r.mesor(), 1e-6);
    }

    @Test
    void noiseDegradesGracefully() {
        double mesor = 70.0;
        double amplitude = 8.0;
        double phi = -2.0 * Math.PI * 15.0 / 24.0;
        int n = 1440; // one day at 1-min resolution
        Random rng = new Random(1);
        double[] t = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / 60.0;
            y[i] = mesor + amplitude * Math.cos(2.0 * Math.PI * t[i] / 24.0 + phi) + rng.nextGaussian();
        }

        CosinorResult r = Cosinor.fit(t, y);

        assertEquals(amplitude, r.amplitude(), 0.5);
        assertEquals(mesor, r.mesor(), 0.2);
        assertTrue(r.r2() > 0.9, "r2=" + r.r2());
    }
}
