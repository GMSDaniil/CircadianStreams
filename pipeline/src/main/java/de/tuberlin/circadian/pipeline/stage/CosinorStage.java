package de.tuberlin.circadian.pipeline.stage;

import de.tuberlin.circadian.analytics.cosinor.Cosinor;
import de.tuberlin.circadian.analytics.cosinor.CosinorResult;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 3a: fit a 24 h Cosinor over each sliding window of the 1-min median series, emitting mesor,
 * amplitude, acrophase and R&sup2;. Time is hours-since-epoch so the recovered acrophase is directly
 * comparable to ground_truth_params.acrophase.
 */
public final class CosinorStage extends ProcessWindowFunction<AggregateRecord, CircadianMetric, String, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final int cosinorHours;
    private final double minCoverage;

    public CosinorStage(int cosinorHours, double minCoverage) {
        this.cosinorHours = cosinorHours;
        this.minCoverage = minCoverage;
    }

    @Override
    public void process(String key, Context ctx, Iterable<AggregateRecord> elements, Collector<CircadianMetric> out) {
        List<Double> tHours = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (AggregateRecord r : elements) {
            double median = r.getMedian();
            if (!Double.isNaN(median)) {
                tHours.add(r.getWindowStart().toEpochMilli() / 3_600_000.0);
                values.add(median);
            }
        }

        int expected = cosinorHours * 60;
        if (values.size() < 3 || values.size() < minCoverage * expected) {
            return;
        }

        double[] t = new double[values.size()];
        double[] y = new double[values.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = tHours.get(i);
            y[i] = values.get(i);
        }

        CosinorResult result = Cosinor.fit(t, y);
        Instant windowEnd = Instant.ofEpochMilli(ctx.window().getEnd());
        String[] parts = key.split("\\|", 2);
        out.collect(CircadianMetric.cosinor(parts[0], parts[1], windowEnd, result));
    }
}