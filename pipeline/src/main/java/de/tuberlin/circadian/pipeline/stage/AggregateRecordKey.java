package de.tuberlin.circadian.pipeline.stage;

import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import org.apache.flink.api.java.functions.KeySelector;

/** Keys the Stage 2 aggregate stream per (patient, signal) for the Stage 3 sliding windows. */
public final class AggregateRecordKey implements KeySelector<AggregateRecord, String> {

    private static final long serialVersionUID = 1L;

    @Override
    public String getKey(AggregateRecord r) {
        return r.getPatientId() + '|' + r.getSignalType();
    }
}
