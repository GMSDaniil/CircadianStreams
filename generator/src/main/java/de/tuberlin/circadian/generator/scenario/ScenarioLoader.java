package de.tuberlin.circadian.generator.scenario;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

// Loads a Scenario from a YAML file. Binds by field and ignores unknown keys so scenario files can carry extra annotation without breaking.
public final class ScenarioLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ScenarioLoader() { }

    public static Scenario load(String path) {
        try {
            return YAML.readValue(new File(path), Scenario.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load scenario: " + path, e);
        }
    }
}