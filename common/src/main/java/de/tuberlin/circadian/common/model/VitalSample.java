package de.tuberlin.circadian.common.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One vital-sign measurement on the vitals-raw Kafka topic. This is the single message schema
 * every data source (synthetic or real HDP) must emit; nothing downstream depends on which source
 * produced it.
 */
public class VitalSample implements Serializable {

    private static final long serialVersionUID = 1L;

    private String patientId;
    private SignalType signalType;
    private Instant timestamp;
    private double value;
    private String unit;

    public VitalSample() { }

    public VitalSample(String patientId, SignalType signalType, Instant timestamp, double value, String unit) {
        this.patientId = patientId;
        this.signalType = signalType;
        this.timestamp = timestamp;
        this.value = value;
        this.unit = unit;
    }

    public static VitalSample of(String patientId, SignalType signalType, Instant timestamp, double value) {
        return new VitalSample(patientId, signalType, timestamp, value, signalType.unit());
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VitalSample)) {
            return false;
        }
        VitalSample that = (VitalSample) o;
        return Double.compare(that.value, value) == 0
                && Objects.equals(patientId, that.patientId)
                && signalType == that.signalType
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId, signalType, timestamp, value, unit);
    }

    @Override
    public String toString() {
        return "VitalSample{patientId='" + patientId + '\''
                + ", signalType=" + signalType
                + ", timestamp=" + timestamp
                + ", value=" + value
                + ", unit='" + unit + '\'' + '}';
    }
}