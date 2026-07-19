package de.tuberlin.circadian.pipeline.stage;

import de.tuberlin.circadian.analytics.cwt.MorletCwt;
import de.tuberlin.circadian.analytics.nonparam.IsIv;
import de.tuberlin.circadian.analytics.nonparam.IsIvResult;
import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Stage 3b (+ 3c), memory-optimized. Same output as {@link LongWindowStage}, but instead of Flink's {@code SlidingEventTimeWindows} — which replicates every element into the ~{@code windowDays*24/hop}
 * overlapping panes it belongs to — this keeps a <b>single</b> rolling 5-day buffer per key and recomputes on hop-aligned event-time timers. Each 1-min aggregate is stored once, not ~120×.
 */
public final class LongWindowRollingStage extends KeyedProcessFunction<String, AggregateRecord, CircadianMetric> {

    private static final long serialVersionUID = 1L;

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final long MINUTE_MS = 60_000L;

    private final PipelineConfig config;

    private transient MorletCwt cwt;
    private transient int n;
    private transient long hopMs;
    private transient long windowMs;

    private transient MapState<Long, Double> buffer;
    private transient ValueState<Long> nextTimer;

    public LongWindowRollingStage(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public void open(OpenContext openContext) {
        this.n = config.cwtDays * MINUTES_PER_DAY;
        this.hopMs = config.hopHours * 3_600_000L;
        this.windowMs = (long) config.cwtDays * 24L * 3_600_000L;
        this.cwt = new MorletCwt(n, 1.0 / 60.0, config.cwtOmega0, config.cwtMinPeriodHours, config.cwtMaxPeriodHours, config.cwtVoices);
        this.buffer = getRuntimeContext().getMapState(new MapStateDescriptor<>("longWindowBuffer", Types.LONG, Types.DOUBLE));
        this.nextTimer = getRuntimeContext().getState(new ValueStateDescriptor<>("nextHopTimer", Types.LONG));
    }

    @Override
    public void processElement(AggregateRecord r, Context ctx, Collector<CircadianMetric> out) throws Exception {
        double median = r.getMedian();
        if (Double.isNaN(median)) {
            return;
        }
        long ts = r.getWindowStart().toEpochMilli();
        buffer.put(ts, median);

        if (nextTimer.value() == null) {
            long first = Math.floorDiv(ts, hopMs) * hopMs + hopMs;
            ctx.timerService().registerEventTimeTimer(first);
            nextTimer.update(first);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<CircadianMetric> out) throws Exception {
        long windowStartMs = timestamp - windowMs;

        double[] series = new double[n];
        boolean[] present = new boolean[n];
        int presentCount = 0;
        double sum = 0.0;

        Iterator<Map.Entry<Long, Double>> it = buffer.entries().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Double> e = it.next();
            long ts = e.getKey();
            if (ts < windowStartMs) {
                it.remove();
                continue;
            }
            int idx = (int) ((ts - windowStartMs) / MINUTE_MS);
            if (idx < 0 || idx >= n) {
                continue;
            }
            series[idx] = e.getValue();
            present[idx] = true;
            presentCount++;
            sum += e.getValue();
        }

        if (presentCount >= config.cwtMinCoverage * n) {
            double mean = sum / presentCount;
            for (int i = 0; i < n; i++) {
                if (!present[i]) {
                    series[i] = mean;
                }
            }
            Instant windowEnd = Instant.ofEpochMilli(timestamp);
            String[] parts = ctx.getCurrentKey().split("\\|", 2);

            double cri = cwt.circadianRhythmIndexFast(series, config.bandLowHours, config.bandHighHours);
            out.collect(CircadianMetric.cwt(parts[0], parts[1], windowEnd, cri));

            if (config.enableIsIv) {
                IsIvResult isiv = IsIv.compute(series, MINUTES_PER_DAY);
                out.collect(CircadianMetric.isiv(parts[0], parts[1], windowEnd, isiv));
            }
        }

        long next = timestamp + hopMs;
        ctx.timerService().registerEventTimeTimer(next);
        nextTimer.update(next);
    }
}