package de.tuberlin.circadian.generator.scenario;

import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.generator.model.Disruption;
import de.tuberlin.circadian.generator.model.DisruptionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioLoaderTest {

    private static final String YAML = """
            name: test
            patients: 5
            simHours: 12
            rateHz: 2.0
            fidelity:
              noiseCorrelation: 0.5
              artifactRate: 0.01
            disruptions:
              - patients: [P0001]
                signals: [HR]
                type: amplitude_drop
                startHour: 2
                endHour: 8
                magnitude: 0.3
                rampHours: 1
            """;

    @Test
    void loadsFieldsAndExpandsDisruptionsByMatch(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("scenario.yaml");
        Files.writeString(file, YAML);

        Scenario s = ScenarioLoader.load(file.toString());

        assertEquals("test", s.name);
        assertEquals(5, s.patients);
        assertEquals(2.0, s.rateHz);
        assertEquals(0.5, s.fidelity.noiseCorrelation);
        assertEquals(0.01, s.fidelity.artifactRate);
        assertEquals(1, s.disruptions.size());

        // matches P0001/HR
        List<Disruption> matched = s.disruptionsFor("P0001", SignalType.HR);
        assertEquals(1, matched.size());
        assertEquals(DisruptionType.AMPLITUDE_DROP, matched.get(0).type());

        // does not match other patient or other signal
        assertTrue(s.disruptionsFor("P0002", SignalType.HR).isEmpty());
        assertTrue(s.disruptionsFor("P0001", SignalType.SPO2).isEmpty());
    }
}