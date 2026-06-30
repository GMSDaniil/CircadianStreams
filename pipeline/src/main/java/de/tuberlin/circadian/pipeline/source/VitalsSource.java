package de.tuberlin.circadian.pipeline.source;

import de.tuberlin.circadian.common.model.VitalSample;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * The single entry point through which all vital-sign data reaches the pipeline. Implementations
 * differ only in where the bytes come from; everything downstream depends solely on this interface
 * and the VitalSample schema, so swapping sources is a one-line wiring change.
 */
public interface VitalsSource {
    DataStream<VitalSample> create(StreamExecutionEnvironment env);
}