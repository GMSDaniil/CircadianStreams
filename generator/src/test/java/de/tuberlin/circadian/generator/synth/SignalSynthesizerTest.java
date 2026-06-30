package de.tuberlin.circadian.generator.synth;

import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.generator.model.SignalBaseline;
import de.tuberlin.circadian.generator.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalSynthesizerTest {

    private static SignalSynthesizer synth(Scenario.Fidelity fidelity, long seed) {
        return new SignalSynthesizer(SignalBaseline.defaultFor(SignalType.HR), List.of(), fidelity, new Random(seed));
    }

    @Test
    void sameSeedAndConfigProduceIdenticalSequence() {
        Scenario.Fidelity f = new Scenario.Fidelity();
        f.noiseCorrelation = 0.8;
        SignalSynthesizer a = synth(f, 1L);
        SignalSynthesizer b = synth(f, 1L);

        for (int i = 0; i < 200; i++) {
            double h = 480_000.0 + i / 3600.0;
            assertEquals(a.next(h, i / 3600.0).getAsDouble(), b.next(h, i / 3600.0).getAsDouble(), 0.0);
        }
    }

    @Test
    void cleanConfigStaysWithinPhysiologicalRange() {
        SignalSynthesizer s = synth(new Scenario.Fidelity(), 42L);
        for (int i = 0; i < 500; i++) {
            OptionalDouble v = s.next(480_000.0 + i / 3600.0, i / 3600.0);
            assertTrue(v.isPresent());
            assertTrue(SignalType.HR.isPlausible(v.getAsDouble()), "value=" + v.getAsDouble());
        }
    }

    @Test
    void artifactsAreOutOfRange() {
        Scenario.Fidelity f = new Scenario.Fidelity();
        f.artifactRate = 1.0; // every sample is an artifact
        SignalSynthesizer s = synth(f, 7L);

        boolean anyOutOfRange = false;
        for (int i = 0; i < 50; i++) {
            OptionalDouble v = s.next(480_000.0 + i / 3600.0, i / 3600.0);
            if (v.isPresent() && !SignalType.HR.isPlausible(v.getAsDouble())) {
                anyOutOfRange = true;
            }
        }
        assertTrue(anyOutOfRange);
    }

    @Test
    void fullMissingnessProducesGaps() {
        Scenario.Fidelity f = new Scenario.Fidelity();
        f.missingnessRate = 1.0;
        SignalSynthesizer s = synth(f, 3L);

        assertFalse(s.next(480_000.0, 0.0).isPresent());
    }
}
