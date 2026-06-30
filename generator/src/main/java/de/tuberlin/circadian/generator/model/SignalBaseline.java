package de.tuberlin.circadian.generator.model;

import de.tuberlin.circadian.common.model.SignalType;

import java.util.Random;

// The true baseline circadian parameters for one patient/signal, i.e. the ground truth the pipeline's Cosinor/CWT stages are expected to recover.
public final class SignalBaseline {

    private final SignalType signalType;
    private final double mesor;
    private final double amplitude;
    private final double acrophaseRad;
    private final double noiseSd;
    private final double clampMin;
    private final double clampMax;

    public SignalBaseline(SignalType signalType, double mesor, double amplitude, double acrophaseRad, double noiseSd, double clampMin, double clampMax) {
        this.signalType = signalType;
        this.mesor = mesor;
        this.amplitude = amplitude;
        this.acrophaseRad = acrophaseRad;
        this.noiseSd = noiseSd;
        this.clampMin = clampMin;
        this.clampMax = clampMax;
    }

    // Population-level default profile for a signal, with a physiologically sensible peak hour.
    public static SignalBaseline defaultFor(SignalType s) {
        switch (s) {
            case HR:      return fromPeakHour(s, 70.0,  8.0, 15.0, 2.5);
            case ABP_SYS: return fromPeakHour(s, 120.0, 10.0, 18.0, 4.0);
            case ABP_DIA: return fromPeakHour(s, 75.0,  6.0, 18.0, 3.0);
            case SPO2:    return fromPeakHour(s, 97.0,  0.8, 14.0, 0.4);
            case RR:      return fromPeakHour(s, 16.0,  2.0, 15.0, 1.0);
            default:      throw new IllegalArgumentException("No default baseline for " + s);
        }
    }

    /**
     * Per-patient profile: the population default with small seeded variation in mesor, amplitude
     * and phase so patients are not identical. Uses rng for reproducibility.
     */
    public static SignalBaseline randomizedFor(SignalType s, Random rng) {
        SignalBaseline base = defaultFor(s);
        double mesor = base.mesor * (1.0 + 0.05 * rng.nextGaussian());
        double amplitude = Math.max(0.0, base.amplitude * (1.0 + 0.15 * rng.nextGaussian()));
        double phase = base.acrophaseRad + 0.2 * rng.nextGaussian();
        return new SignalBaseline(s, mesor, amplitude, phase, base.noiseSd, base.clampMin, base.clampMax);
    }

    // Builds a baseline from a desired peak hour-of-day, converting it to the model's phase term.
    private static SignalBaseline fromPeakHour(SignalType s, double mesor, double amplitude, double peakHour, double noiseSd) {
        // cos(2*pi*t/24 + phi) peaks where the argument is 0 -> t = -phi*24/(2*pi);
        // so for a peak at peakHour: phi = -2*pi*peakHour/24.
        double phi = -2.0 * Math.PI * peakHour / 24.0;
        return new SignalBaseline(s, mesor, amplitude, phi, noiseSd, s.minPlausible(), s.maxPlausible());
    }

    /**
     * Sample value at event time hoursSinceEpoch, given a standard-normal draw for noise.
     * Drawing the Gaussian outside keeps the noise RNG owned per (patient, signal) for determinism.
     */
    public double valueAt(double hoursSinceEpoch, double standardNormal) {
        double v = mesor + amplitude * Math.cos(2.0 * Math.PI * hoursSinceEpoch / 24.0 + acrophaseRad) + noiseSd * standardNormal;
        return Math.max(clampMin, Math.min(clampMax, v));
    }

    public SignalType signalType() {
        return signalType;
    }

    public double mesor() {
        return mesor;
    }

    public double amplitude() {
        return amplitude;
    }

    public double acrophaseRad() {
        return acrophaseRad;
    }

    public double noiseSd() {
        return noiseSd;
    }

    public double clampMin() {
        return clampMin;
    }

    public double clampMax() {
        return clampMax;
    }

    /**
     * A copy with the amplitude multiplied by {@code factor} (and everything else unchanged). Used to
     * give a cohort variable rhythm strength: a low factor models a blunted/disrupted rhythm. The
     * scaled amplitude is what gets written to {@code ground_truth_params}, so it stays the truth.
     */
    public SignalBaseline withAmplitudeScaled(double factor) {
        return new SignalBaseline(signalType, mesor, Math.max(0.0, amplitude * factor), acrophaseRad, noiseSd, clampMin, clampMax);
    }
}
