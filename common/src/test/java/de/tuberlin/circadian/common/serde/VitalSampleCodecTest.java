package de.tuberlin.circadian.common.serde;

import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.common.model.VitalSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitalSampleCodecTest {

    @Test
    void roundTripsThroughJson() {
        VitalSample original = VitalSample.of("P0007", SignalType.HR, Instant.parse("2026-06-01T12:00:00Z"), 78.0);

        VitalSample restored = VitalSampleCodec.fromJson(VitalSampleCodec.toJson(original));

        assertEquals(original, restored);
    }

    @Test
    void serializesTimestampAsIsoStringAndEnumAsName() {
        VitalSample sample = VitalSample.of("P0001", SignalType.SPO2, Instant.parse("2026-06-01T00:00:00Z"), 97.5);

        String json = VitalSampleCodec.toJson(sample);

        assertTrue(json.contains("\"signalType\":\"SPO2\""), json);
        assertTrue(json.contains("\"timestamp\":\"2026-06-01T00:00:00Z\""), json);
        assertTrue(json.contains("\"unit\":\"%\""), json);
    }

    @Test
    void ignoresUnknownProperties() {
        String json = "{\"patientId\":\"P0002\",\"signalType\":\"RR\",\"timestamp\":\"2026-06-01T01:00:00Z\",\"value\":16.0,\"unit\":\"breaths/min\",\"futureField\":\"ignored\"}";

        VitalSample sample = VitalSampleCodec.fromJson(json);

        assertEquals(SignalType.RR, sample.getSignalType());
        assertEquals(16.0, sample.getValue());
    }
}