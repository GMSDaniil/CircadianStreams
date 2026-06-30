package de.tuberlin.circadian.generator.synth;

import de.tuberlin.circadian.generator.model.Disruption;
import de.tuberlin.circadian.generator.model.SignalBaseline;
import de.tuberlin.circadian.generator.scenario.Scenario;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Generates one patient/signal time series, sample by sample. This is the stateful core of the
 * generator: it layers the circadian baseline, any active disruptions, correlated noise, slow drift, out-of-range artifacts and gaps. 
 */
public final class SignalSynthesizer {

    private final SignalBaseline baseline;
    private final List<Disruption> disruptions;
    private final Scenario.Fidelity fidelity;
    private final Random rng;

    private final double phi;          // AR(1) coefficient
    private final double noiseScale;   // sqrt(1 - phi^2): keeps AR(1) at unit variance
    private double prevNoise = 0.0;
    private double drift = 0.0;

    public SignalSynthesizer(SignalBaseline baseline, List<Disruption> disruptions, Scenario.Fidelity fidelity, Random rng) {
        this.baseline = baseline;
        this.disruptions = disruptions;
        this.fidelity = fidelity;
        this.rng = rng;
        this.phi = Math.max(0.0, Math.min(0.999, fidelity.noiseCorrelation));
        this.noiseScale = Math.sqrt(1.0 - phi * phi);
    }

    /**
     * The next sample value, or empty if this sample is a gap (missing data).
     *
     * @param hoursSinceEpoch absolute time in hours
     * @param simHour         time since scenario start in hours (drives disruption windows)
     */
    public OptionalDouble next(double hoursSinceEpoch, double simHour) {
        // 1. Correlated noise: AR(1) update (always one Gaussian draw).
        prevNoise = phi * prevNoise + noiseScale * rng.nextGaussian();

        // 2. Optional slow mean-reverting baseline drift.
        if (fidelity.driftSd > 0.0) {
            drift = 0.999 * drift + fidelity.driftSd * rng.nextGaussian();
        }

        // 3. Effective rhythm parameters after all active disruptions.
        double effAmplitude = baseline.amplitude();
        double effPhase = baseline.acrophaseRad();
        double additive = 0.0;
        for (Disruption d : disruptions) {
            effAmplitude *= d.amplitudeFactor(simHour);
            effPhase += d.phaseDeltaRad(simHour);
            additive += d.additive(simHour, baseline.amplitude());
        }

        double rhythm = baseline.mesor() + effAmplitude * Math.cos(2.0 * Math.PI * hoursSinceEpoch / 24.0 + effPhase) + additive + drift;
        double value = rhythm + baseline.noiseSd() * prevNoise;

        // Normal values are clamped to the physiological range so noise tails don't masquerade as artifacts
        value = Math.max(baseline.clampMin(), Math.min(baseline.clampMax(), value));

        // 4. Out-of-range artifact (sensor lead-off / spike) -> will be dead-lettered by Stage 1.
        if (fidelity.artifactRate > 0.0 && rng.nextDouble() < fidelity.artifactRate) {
            value = artifactValue();
        }

        // 5. Missing sample (gap).
        if (fidelity.missingnessRate > 0.0 && rng.nextDouble() < fidelity.missingnessRate) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    private double artifactValue() {
        // Half lead-off (0, below every signal's min), half an over-range spike.
        return rng.nextDouble() < 0.5 ? 0.0 : baseline.clampMax() * 1.5;
    }
}
