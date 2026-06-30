package de.tuberlin.circadian.common.model;


// The vital-sign channels carried on the vitals-raw Kafka topic.
public enum SignalType {

    /** Heart rate. */
    HR("bpm", 20.0, 300.0),
    /** Systolic arterial blood pressure. */
    ABP_SYS("mmHg", 30.0, 300.0),
    /** Diastolic arterial blood pressure. */
    ABP_DIA("mmHg", 10.0, 200.0),
    /** Peripheral oxygen saturation. */
    SPO2("%", 50.0, 100.0),
    /** Respiratory rate. */
    RR("breaths/min", 3.0, 60.0);

    private final String unit;
    private final double minPlausible;
    private final double maxPlausible;

    SignalType(String unit, double minPlausible, double maxPlausible) {
        this.unit = unit;
        this.minPlausible = minPlausible;
        this.maxPlausible = maxPlausible;
    }

    // Canonical unit string for this signal (e.g. "bpm"). 
    public String unit() {
        return unit;
    }

    public double minPlausible() {
        return minPlausible;
    }

    public double maxPlausible() {
        return maxPlausible;
    }

    /** @return true if value lies within this signal's physiological range. */
    public boolean isPlausible(double value) {
        return value >= minPlausible && value <= maxPlausible;
    }
}
