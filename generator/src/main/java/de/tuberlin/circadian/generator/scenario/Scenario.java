package de.tuberlin.circadian.generator.scenario;

import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.generator.model.Disruption;
import de.tuberlin.circadian.generator.model.DisruptionType;

import java.util.ArrayList;
import java.util.List;

/**
 * A replayable generation scenario, loaded from YAML (see scenarios/*.yaml). It
 * fixes the cohort size, duration, sample rate, signal-fidelity knobs, and the schedule of injected
 * disruptions. Combined with --seed, a scenario fully determines the produced stream and the ground-truth log.
 */
public final class Scenario {

    public String name = "adhoc";
    public String description = "";
    public int patients = 3;
    public double simHours = 24.0;
    public double rateHz = 1.0;
    public Fidelity fidelity = new Fidelity();
    public List<DisruptionSpec> disruptions = new ArrayList<>();

    // A clean, disruption-free scenario built from CLI args
    public static Scenario cleanDefault(int patients, double simHours, double rateHz) {
        Scenario s = new Scenario();
        s.name = "adhoc";
        s.description = "Clean baseline generated from CLI arguments (no scenario file).";
        s.patients = patients;
        s.simHours = simHours;
        s.rateHz = rateHz;
        return s;
    }

    // Concrete disruptions that apply to one patient/signal (specs are expanded by match).
    public List<Disruption> disruptionsFor(String patientId, SignalType signal) {
        List<Disruption> out = new ArrayList<>();
        for (DisruptionSpec spec : disruptions) {
            if (spec.matches(patientId, signal)) {
                out.add(spec.toDisruption());
            }
        }
        return out;
    }

    // Signal-fidelity knobs. All default to "clean" so a baseline scenario stays pristine.
    public static final class Fidelity {
        // AR(1) coefficient in [0,1): 0 = white noise, higher = smoother autocorrelated noise.
        public double noiseCorrelation = 0.0;
        // Probability a sample is dropped (a gap).
        public double missingnessRate = 0.0;
        // Probability a sample is an out-of-range artifact (exercises Stage 1 dead-lettering).
        public double artifactRate = 0.0;
        // Std-dev of a slow mean-reverting baseline drift (0 = off).
        public double driftSd = 0.0;
        // Per-patient amplitude scaling is drawn uniformly from [min,max] (1,1 = uniform cohort).
        public double amplitudeScaleMin = 1.0;
        public double amplitudeScaleMax = 1.0;
    }

    // Raw YAML form of a disruption; patients/signals are lists (or ["all"]).
    public static final class DisruptionSpec {
        public List<String> patients = List.of("all");
        public List<String> signals = List.of("all");
        public String type;
        public double startHour;
        public double endHour;
        public double magnitude;
        public double rampHours = 0.0;
        public double boutHours = 0.5;

        public boolean matches(String patientId, SignalType signal) {
            boolean patientMatch = patients.stream().anyMatch(p -> p.equalsIgnoreCase("all") || p.equalsIgnoreCase(patientId));
            boolean signalMatch = signals.stream().anyMatch(s -> s.equalsIgnoreCase("all") || s.equalsIgnoreCase(signal.name()));
            return patientMatch && signalMatch;
        }

        public Disruption toDisruption() {
            return new Disruption(DisruptionType.fromString(type),startHour, endHour, magnitude, rampHours, boutHours);
        }
    }
}