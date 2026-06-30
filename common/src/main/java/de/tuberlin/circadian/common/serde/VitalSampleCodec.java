package de.tuberlin.circadian.common.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tuberlin.circadian.common.model.VitalSample;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON (de)serialization for VitalSample, shared by the generator (produce) and the
 * pipeline (consume). The ObjectMapper is configured once and is thread-safe for
 * read/write, so a single static instance is reused.
 */
public final class VitalSampleCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private VitalSampleCodec() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(VitalSample sample) {
        try {
            return MAPPER.writeValueAsString(sample);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize VitalSample: " + sample, e);
        }
    }

    public static byte[] toJsonBytes(VitalSample sample) {
        return toJson(sample).getBytes(StandardCharsets.UTF_8);
    }

    public static VitalSample fromJson(String json) {
        try {
            return MAPPER.readValue(json, VitalSample.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize VitalSample from: " + json, e);
        }
    }

    public static VitalSample fromJson(byte[] json) {
        try {
            return MAPPER.readValue(json, VitalSample.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize VitalSample from bytes", e);
        }
    }
}