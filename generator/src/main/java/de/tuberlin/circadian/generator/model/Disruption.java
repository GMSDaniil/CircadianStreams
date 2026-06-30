package de.tuberlin.circadian.generator.model;

/**
 * One injected disruption applied to a single patient/signal time series, expressed in
 * simulation-relative hours (0 = scenario start). It is a pure, deterministic transform of
 * the baseline rhythm and is the source of the ground_truth_events row that evaluation
 * compares detections against.
 */
public final class Disruption {

    private final DisruptionType type;
    private final double startHour;
    private final double endHour;
    private final double magnitude;   // meaning depends on type (see methods)
    private final double rampHours;
    private final double boutHours;   // fragmentation bout period

    public Disruption(DisruptionType type, double startHour, double endHour, double magnitude, double rampHours, double boutHours) {
        this.type = type;
        this.startHour = startHour;
        this.endHour = endHour;
        this.magnitude = magnitude;
        this.rampHours = rampHours;
        this.boutHours = boutHours <= 0 ? 0.5 : boutHours;
    }

    // Ramped activation level in [0,1] at the given simulation hour (0 outside the window).
    public double progress(double simHour) {
        if (simHour < startHour || simHour >= endHour) {
            return 0.0;
        }
        if (rampHours <= 0) {
            return 1.0;
        }
        double p = 1.0;
        if (simHour < startHour + rampHours) {
            p = (simHour - startHour) / rampHours;            // ramp in
        } else if (simHour > endHour - rampHours) {
            p = (endHour - simHour) / rampHours;              // ramp out
        }
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Multiplicative factor on amplitude. For AMPLITUDE_DROP, magnitude is the target
     * amplitude fraction (0.3 = flatten to 30%). For FRAGMENTATION the coherent amplitude is
     * partly reduced. Other types: 1.0 (no change).
     */
    public double amplitudeFactor(double simHour) {
        double p = progress(simHour);
        if (p == 0.0) {
            return 1.0;
        }
        switch (type) {
            case AMPLITUDE_DROP:
                return 1.0 - p * (1.0 - magnitude);
            case FRAGMENTATION:
                return 1.0 - p * 0.5 * magnitude;
            default:
                return 1.0;
        }
    }

    /**
     * Additive phase shift in radians. For PHASE_SHIFT, magnitude is the shift in
     * hours (a +6 h later peak). Other types: 0.
     */
    public double phaseDeltaRad(double simHour) {
        double p = progress(simHour);
        if (p == 0.0 || type != DisruptionType.PHASE_SHIFT) {
            return 0.0;
        }
        return p * (-2.0 * Math.PI * magnitude / 24.0);
    }

    /**
     * Additive fast term that injects sub-hour fluctuation for FRAGMENTATION, raising
     * intra-daily variability. A square-wave gate of period boutHours scaled by the baseline
     * amplitude and magnitude. Other types: 0.
     */
    public double additive(double simHour, double baseAmplitude) {
        double p = progress(simHour);
        if (p == 0.0 || type != DisruptionType.FRAGMENTATION) {
            return 0.0;
        }
        double gate = Math.signum(Math.sin(2.0 * Math.PI * simHour / boutHours));
        return p * baseAmplitude * magnitude * gate;
    }

    public DisruptionType type() {
        return type;
    }

    public double startHour() {
        return startHour;
    }

    public double endHour() {
        return endHour;
    }

    public double magnitude() {
        return magnitude;
    }

    public double rampHours() {
        return rampHours;
    }

    public double boutHours() {
        return boutHours;
    }
}