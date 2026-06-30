package de.tuberlin.circadian.pipeline.model;

import de.tuberlin.circadian.analytics.cosinor.CosinorResult;
import de.tuberlin.circadian.analytics.nonparam.IsIvResult;

import java.io.Serializable;
import java.time.Instant;

/**
 * One row of circadian_metrics: a metric from one method for one (patient, signal) at one
 * window end. Method-specific fields are left null (boxed Double) and written as SQL NULL.
 */
public class CircadianMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    private String patientId;
    private String signalType;
    private String method;        // 'cosinor' | 'cwt' | 'isiv'
    private Instant windowEnd;

    private Double mesor;          // cosinor
    private Double amplitude;      // cosinor
    private Double acrophase;      // cosinor (radians)
    private Double r2;             // cosinor
    private Double cri;            // cwt
    private Double isStability;    // isiv
    private Double ivVariab;       // isiv

    public CircadianMetric() {
    }

    public static CircadianMetric cosinor(String patientId, String signalType, Instant windowEnd, CosinorResult r) {
        CircadianMetric m = base(patientId, signalType, "cosinor", windowEnd);
        m.mesor = r.mesor();
        m.amplitude = r.amplitude();
        m.acrophase = r.acrophaseRad();
        m.r2 = r.r2();
        return m;
    }

    public static CircadianMetric cwt(String patientId, String signalType, Instant windowEnd, double cri) {
        CircadianMetric m = base(patientId, signalType, "cwt", windowEnd);
        m.cri = cri;
        return m;
    }

    public static CircadianMetric isiv(String patientId, String signalType, Instant windowEnd, IsIvResult r) {
        CircadianMetric m = base(patientId, signalType, "isiv", windowEnd);
        m.isStability = r.interdailyStability();
        m.ivVariab = r.intradailyVariability();
        return m;
    }

    private static CircadianMetric base(String patientId, String signalType, String method, Instant windowEnd) {
        CircadianMetric m = new CircadianMetric();
        m.patientId = patientId;
        m.signalType = signalType;
        m.method = method;
        m.windowEnd = windowEnd;
        return m;
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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Double getMesor() {
        return mesor;
    }

    public void setMesor(Double mesor) {
        this.mesor = mesor;
    }

    public Double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(Double amplitude) {
        this.amplitude = amplitude;
    }

    public Double getAcrophase() {
        return acrophase;
    }

    public void setAcrophase(Double acrophase) {
        this.acrophase = acrophase;
    }

    public Double getR2() {
        return r2;
    }

    public void setR2(Double r2) {
        this.r2 = r2;
    }

    public Double getCri() {
        return cri;
    }

    public void setCri(Double cri) {
        this.cri = cri;
    }

    public Double getIsStability() {
        return isStability;
    }

    public void setIsStability(Double isStability) {
        this.isStability = isStability;
    }

    public Double getIvVariab() {
        return ivVariab;
    }

    public void setIvVariab(Double ivVariab) {
        this.ivVariab = ivVariab;
    }
}