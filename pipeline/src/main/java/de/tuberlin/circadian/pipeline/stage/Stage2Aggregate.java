package de.tuberlin.circadian.pipeline.stage;

import de.tuberlin.circadian.analytics.stats.WindowStats;
import de.tuberlin.circadian.common.model.VitalSample;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Stage 2: collapse a 1-min tumbling window of validated samples into a robust summary (median / SD / completeness) per (patient, signal).
public final class Stage2Aggregate
        extends ProcessWindowFunction<VitalSample, AggregateRecord, String, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final int expectedSamplesPerMin;

    public Stage2Aggregate(int expectedSamplesPerMin) {
        this.expectedSamplesPerMin = expectedSamplesPerMin;
    }

    @Override
    public void process(String key, Context ctx, Iterable<VitalSample> elements, Collector<AggregateRecord> out) {
        List<Double> values = new ArrayList<>();
        for (VitalSample s : elements) {
            values.add(s.getValue());
        }
        double[] arr = new double[values.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = values.get(i);
        }

        WindowStats stats = WindowStats.of(arr, expectedSamplesPerMin);
        String[] parts = key.split("\\|", 2);
        Instant windowStart = Instant.ofEpochMilli(ctx.window().getStart());

        out.collect(new AggregateRecord(parts[0], parts[1], windowStart, stats.count(), stats.median(), stats.sd(), stats.completeness()));
    }
}