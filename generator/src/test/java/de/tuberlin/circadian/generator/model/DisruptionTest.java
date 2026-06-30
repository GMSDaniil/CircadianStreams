package de.tuberlin.circadian.generator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptionTest {

    @Test
    void amplitudeDropFlattensInsideWindowOnly() {
        Disruption d = new Disruption(DisruptionType.AMPLITUDE_DROP, 10, 20, 0.25, 0, 0.5);

        assertEquals(1.0, d.amplitudeFactor(5), 1e-9);    // before
        assertEquals(0.25, d.amplitudeFactor(15), 1e-9);  // inside (target fraction)
        assertEquals(1.0, d.amplitudeFactor(25), 1e-9);   // after
    }

    @Test
    void rampInterpolatesActivation() {
        // magnitude 0 => flatten fully (factor 0) at full activation
        Disruption d = new Disruption(DisruptionType.AMPLITUDE_DROP, 10, 30, 0.0, 10, 0.5);

        assertEquals(0.5, d.amplitudeFactor(15), 1e-9);   // half-way up the ramp
        assertEquals(0.0, d.amplitudeFactor(20), 1e-9);   // fully active in the hold
    }

    @Test
    void phaseShiftAddsExpectedRadians() {
        Disruption d = new Disruption(DisruptionType.PHASE_SHIFT, 0, 24, 6, 0, 0.5);

        assertEquals(-2.0 * Math.PI * 6 / 24, d.phaseDeltaRad(12), 1e-9);
        assertEquals(0.0, d.phaseDeltaRad(30), 1e-9);     // outside window
        assertEquals(1.0, d.amplitudeFactor(12), 1e-9);   // phase shift leaves amplitude untouched
    }

    @Test
    void fragmentationReducesAmplitudeAndInjectsGatedTerm() {
        Disruption d = new Disruption(DisruptionType.FRAGMENTATION, 0, 24, 0.8, 0, 0.5);

        assertEquals(1.0 - 0.5 * 0.8, d.amplitudeFactor(12), 1e-9);
        assertTrue(Math.abs(d.additive(0.1, 10.0)) > 0.0); // non-zero fast bout term
        assertEquals(0.0, d.additive(30, 10.0), 1e-9);     // none outside the window
    }
}