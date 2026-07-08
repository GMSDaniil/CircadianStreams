package de.tuberlin.circadian.pipeline;

import de.tuberlin.circadian.common.model.VitalSample;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;
import de.tuberlin.circadian.pipeline.sink.AggregatePostgresSink;
import de.tuberlin.circadian.pipeline.sink.CircadianMetricsSink;
import de.tuberlin.circadian.pipeline.sink.DeadLetterPostgresSink;
import de.tuberlin.circadian.pipeline.source.SyntheticSource;
import de.tuberlin.circadian.pipeline.source.VitalsSource;
import de.tuberlin.circadian.pipeline.stage.AggregateRecordKey;
import de.tuberlin.circadian.pipeline.stage.CosinorStage;
import de.tuberlin.circadian.pipeline.stage.LongWindowStage;
import de.tuberlin.circadian.pipeline.stage.Stage2Aggregate;
import de.tuberlin.circadian.pipeline.validation.RangeValidator;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

// The circadian pipeline (Phases 1-2).
public final class PipelineJob {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineJob.class);

    public static void main(String[] args) throws Exception {
        PipelineConfig config = PipelineConfig.fromEnv();
        LOG.info("Starting circadian pipeline with {}", config);

        Configuration flinkConf = new Configuration();
        flinkConf.set(StateBackendOptions.STATE_BACKEND, config.stateBackend);
        flinkConf.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
        flinkConf.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, config.checkpointDir);
        flinkConf.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        flinkConf.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, config.restartAttempts);
        flinkConf.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY,
                Duration.ofSeconds(config.restartDelaySeconds));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(flinkConf);
        env.setParallelism(config.parallelism);
        if (config.checkpointIntervalMs > 0) {
            env.enableCheckpointing(config.checkpointIntervalMs);
        }

        VitalsSource source = new SyntheticSource(config);
        DataStream<VitalSample> ingested = source.create(env);

        // Stage 1: validation; out-of-range samples to the dead-letter side output.
        SingleOutputStreamOperator<VitalSample> validated = ingested.process(new RangeValidator()).name("stage1-validate");
        validated.getSideOutput(RangeValidator.DEAD_LETTER).sinkTo(new DeadLetterPostgresSink(config)).name("dead-letter-sink");

        // Stage 2: 1-min tumbling aggregation -> agg_1min (and the input series for Stage 3).
        DataStream<AggregateRecord> agg = validated.keyBy(new PatientSignalKey()).window(TumblingEventTimeWindows.of(Duration.ofSeconds(config.windowSeconds))).process(new Stage2Aggregate(config.expectedSamplesPerMin)).name("stage2-agg");
        agg.sinkTo(new AggregatePostgresSink(config)).name("agg-1min-sink");

        // Stage 3a: sliding 24h/1h Cosinor.
        DataStream<CircadianMetric> cosinor = agg.keyBy(new AggregateRecordKey()).window(SlidingEventTimeWindows.of(Duration.ofHours(config.cosinorHours), Duration.ofHours(config.hopHours))).process(new CosinorStage(config.cosinorHours, config.cosinorMinCoverage)).name("stage3a-cosinor");

        // Stage 3b/c: sliding 5d/1h CWT (CRI) + IS/IV.
        DataStream<CircadianMetric> longMetrics = agg.keyBy(new AggregateRecordKey()).window(SlidingEventTimeWindows.of(Duration.ofHours(config.cwtDays * 24L), Duration.ofHours(config.hopHours))).process(new LongWindowStage(config)).name("stage3b-cwt-isiv");

        cosinor.union(longMetrics).sinkTo(new CircadianMetricsSink(config)).name("circadian-metrics-sink");

        env.execute("circadian-pipeline");
    }

    // Key per patient + signal for the raw stream (Stage 2).
    public static final class PatientSignalKey implements KeySelector<VitalSample, String> {
        @Override
        public String getKey(VitalSample s) {
            return s.getPatientId() + '|' + s.getSignalType().name();
        }
    }

    private PipelineJob() { }
}