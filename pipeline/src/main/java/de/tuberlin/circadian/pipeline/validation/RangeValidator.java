package de.tuberlin.circadian.pipeline.validation;

import de.tuberlin.circadian.common.model.VitalSample;
import de.tuberlin.circadian.pipeline.model.DeadLetterRecord;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * Stage 1 validation. Samples within their signal's physiological range pass to the main output;
 * out-of-range samples are routed to the #DEAD_LETTER side output instead of being silently
 * discarded, so they remain available for inspection.
 */
public final class RangeValidator extends ProcessFunction<VitalSample, VitalSample> {

    private static final long serialVersionUID = 1L;

    // Side-output tag for rejected samples. The anonymous subclass captures the generic type. 
    public static final OutputTag<DeadLetterRecord> DEAD_LETTER =
            new OutputTag<DeadLetterRecord>("dead-letter") { };

    @Override
    public void processElement(VitalSample sample, Context ctx, Collector<VitalSample> out) {
        if (sample.getSignalType().isPlausible(sample.getValue())) {
            out.collect(sample);
        } else {
            ctx.output(DEAD_LETTER, DeadLetterRecord.outOfRange(sample));
        }
    }
}