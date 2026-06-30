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

import java.time.Duration;

// Reads the synthetic generator's vitals-raw Kafka topic and produces an event-time stream of VitalSample.
public final class SyntheticSource implements VitalsSource {

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
                .withTimestampAssigner((sample, ts) -> sample.getTimestamp().toEpochMilli());

        return env.fromSource(kafka, WatermarkStrategy.noWatermarks(), "kafka-vitals-raw")
                .map(VitalSampleCodec::fromJson)
                .returns(VitalSample.class)
                .name("parse-json")
                .assignTimestampsAndWatermarks(watermarks)
                .name("assign-event-time");
    }
}