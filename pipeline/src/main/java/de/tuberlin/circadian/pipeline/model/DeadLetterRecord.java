package de.tuberlin.circadian.pipeline.model;

import de.tuberlin.circadian.common.model.VitalSample;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;

// A sample rejected by Stage 1 validation, kept for inspection in the dead_letter table. Carries the original sample plus the reason it failed.
public class DeadLetterRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String patientId;
    private String signalType;
    private Instant eventTime;
    private double value;
    private String unit;
    private String reason;

    public DeadLetterRecord() {
    }

    public DeadLetterRecord(String patientId, String signalType, Instant eventTime, double value, String unit, String reason) {
        this.patientId = patientId;
        this.signalType = signalType;
        this.eventTime = eventTime;
        this.value = value;
        this.unit = unit;
        this.reason = reason;
    }

    // Builds a record for a sample whose value is outside its signal's physiological range.
    public static DeadLetterRecord outOfRange(VitalSample s) {
        String reason = String.format(Locale.ROOT, "out_of_range[%.1f,%.1f]", s.getSignalType().minPlausible(), s.getSignalType().maxPlausible());
        return new DeadLetterRecord(s.getPatientId(), s.getSignalType().name(), s.getTimestamp(), s.getValue(), s.getUnit(), reason);
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}