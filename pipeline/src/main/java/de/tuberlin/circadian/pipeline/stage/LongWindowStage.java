package de.tuberlin.circadian.pipeline.stage;

import de.tuberlin.circadian.analytics.cwt.MorletCwt;
import de.tuberlin.circadian.analytics.nonparam.IsIv;
import de.tuberlin.circadian.analytics.nonparam.IsIvResult;
import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Instant;

/**
 * Stage 3b (+ 3c): the multi-day window. Reconstructs the 1-min median series on a uniform grid for
 * the window (gaps filled with the window mean), then emits the CWT-based CRI and, if enabled, the non-parametric IS/IV.
 */
public final class LongWindowStage extends ProcessWindowFunction<AggregateRecord, CircadianMetric, String, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final PipelineConfig config;
    private transient MorletCwt cwt;
    private transient int n;

    public LongWindowStage(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public void open(OpenContext openContext) {
        this.n = config.cwtDays * MINUTES_PER_DAY;
        this.cwt = new MorletCwt(n, 1.0 / 60.0, config.cwtOmega0, config.cwtMinPeriodHours, config.cwtMaxPeriodHours, config.cwtVoices);
    }

    @Override
    public void process(String key, Context ctx, Iterable<AggregateRecord> elements, Collector<CircadianMetric> out) {
        long startMs = ctx.window().getStart();
        double[] series = new double[n];
        boolean[] present = new boolean[n];
        int presentCount = 0;
        double sum = 0.0;

        for (AggregateRecord r : elements) {
            double median = r.getMedian();
            if (Double.isNaN(median)) {
                continue;
            }
            int idx = (int) ((r.getWindowStart().toEpochMilli() - startMs) / 60_000L);
            if (idx < 0 || idx >= n) {
                continue;
            }
            series[idx] = median;
            present[idx] = true;
            presentCount++;
            sum += median;
        }

        if (presentCount < config.cwtMinCoverage * n) {
            return;
        }

        double mean = sum / presentCount;
        for (int i = 0; i < n; i++) {
            if (!present[i]) {
                series[i] = mean; // gap fill (zero after demeaning)
            }
        }

        Instant windowEnd = Instant.ofEpochMilli(ctx.window().getEnd());
        String[] parts = key.split("\\|", 2);

        double cri = cwt.circadianRhythmIndex(series, config.bandLowHours, config.bandHighHours);
        out.collect(CircadianMetric.cwt(parts[0], parts[1], windowEnd, cri));

        if (config.enableIsIv) {
            IsIvResult isiv = IsIv.compute(series, MINUTES_PER_DAY);
            out.collect(CircadianMetric.isiv(parts[0], parts[1], windowEnd, isiv));
        }
    }
}