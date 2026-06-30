package de.tuberlin.circadian.pipeline.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Output of Stage 2: one robust summary per (patient, signal, 1-min window) — median, SD and
 * completeness. Also the input series for Stage 3 (Cosinor / CWT / IS-IV).
 */
public class AggregateRecord implements Serializable {

    private static final long serialVersionUID = 2L;

    private String patientId;
    private String signalType;
    private Instant windowStart;
    private long count;
    private double median;
    private double sd;
    private double completeness;

    public AggregateRecord() {
    }

    public AggregateRecord(String patientId, String signalType, Instant windowStart,
                           long count, double median, double sd, double completeness) {
        this.patientId = patientId;
        this.signalType = signalType;
        this.windowStart = windowStart;
        this.count = count;
        this.median = median;
        this.sd = sd;
        this.completeness = completeness;
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

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getMedian() {
        return median;
    }

    public void setMedian(double median) {
        this.median = median;
    }

    public double getSd() {
        return sd;
    }

    public void setSd(double sd) {
        this.sd = sd;
    }

    public double getCompleteness() {
        return completeness;
    }

    public void setCompleteness(double completeness) {
        this.completeness = completeness;
    }

    @Override
    public String toString() {
        return "AggregateRecord{" + patientId + '/' + signalType + " @" + windowStart
                + " count=" + count + " median=" + median + " sd=" + sd
                + " completeness=" + completeness + '}';
    }
}