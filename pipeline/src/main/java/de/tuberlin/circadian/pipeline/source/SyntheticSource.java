package de.tuberlin.circadian.pipeline.source;

import de.tuberlin.circadian.common.model.VitalSample;
import de.tuberlin.circadian.common.serde.VitalSampleCodec;
import de.tuberlin.circadian.pipeline.PipelineConfig;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

// Reads the synthetic generator's vitals-raw Kafka topic and produces an event-time stream of VitalSample.
public final class SyntheticSource implements VitalsSource {

    private static final Logger LOG = LoggerFactory.getLogger(SyntheticSource.class);

    private final PipelineConfig config;

    public SyntheticSource(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public DataStream<VitalSample> create(StreamExecutionEnvironment env) {
        KafkaSource<String> kafka = KafkaSource.<String>builder().setBootstrapServers(config.bootstrap)
                .setTopics(config.topic)
                .setGroupId(config.groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        WatermarkStrategy<VitalSample> watermarks = WatermarkStrategy.<VitalSample>forBoundedOutOfOrderness(Duration.ofSeconds(config.maxOutOfOrdernessSeconds))
                .withTimestampAssigner((sample, ts) -> sample.getTimestamp().toEpochMilli())
                .withIdleness(Duration.ofSeconds(config.idlenessSeconds));

        return env.fromSource(kafka, WatermarkStrategy.noWatermarks(), "kafka-vitals-raw")
                .flatMap(SyntheticSource::tryParse)
                .returns(VitalSample.class)
                .name("parse-json")
                .assignTimestampsAndWatermarks(watermarks)
                .name("assign-event-time");
    }

    private static void tryParse(String json, Collector<VitalSample> out) {
        try {
            out.collect(VitalSampleCodec.fromJson(json));
        } catch (RuntimeException e) {
            LOG.warn("Dropping unparseable vitals record: {}", e.getMessage());
        }
    }
}