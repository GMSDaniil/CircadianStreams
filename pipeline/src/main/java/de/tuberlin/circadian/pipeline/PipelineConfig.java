package de.tuberlin.circadian.pipeline;

import java.io.Serializable;

/**
 * Pipeline configuration, resolved from environment variables with local-dev defaults. Window sizes,
 * hop, the CWT scale set and the circadian band are all knobs here (not hard-coded in operators) —
 * they are the evaluation variables, especially for detection latency.
 */
public final class PipelineConfig implements Serializable {

    private static final long serialVersionUID = 2L;

    // --- Kafka / Postgres ---
    public final String bootstrap;
    public final String topic;
    public final String groupId;
    public final String jdbcUrl;
    public final String jdbcUser;
    public final String jdbcPassword;

    // --- Stage 1 / 2 ---
    public final int windowSeconds;
    public final int expectedSamplesPerMin;
    public final int maxOutOfOrdernessSeconds;
    public final int parallelism;

    // --- Stage 3 windows / hop (hours unless noted) ---
    public final int cosinorHours;
    public final int cwtDays;
    public final int hopHours;

    // --- CWT scale set / band ---
    public final double cwtOmega0;
    public final int cwtVoices;
    public final double cwtMinPeriodHours;
    public final double cwtMaxPeriodHours;
    public final double bandLowHours;
    public final double bandHighHours;

    // --- Stage 3 quality gates ---
    public final double cosinorMinCoverage; // emit only if this fraction of the window has data
    public final double cwtMinCoverage;
    public final boolean enableIsIv;

    private PipelineConfig(String bootstrap, String topic, String groupId, String jdbcUrl, String jdbcUser, String jdbcPassword, int windowSeconds, int expectedSamplesPerMin, int maxOutOfOrdernessSeconds, int parallelism, int cosinorHours, int cwtDays, int hopHours, double cwtOmega0, int cwtVoices, double cwtMinPeriodHours, double cwtMaxPeriodHours, double bandLowHours, double bandHighHours, double cosinorMinCoverage, double cwtMinCoverage, boolean enableIsIv) {
        this.bootstrap = bootstrap;
        this.topic = topic;
        this.groupId = groupId;
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
        this.windowSeconds = windowSeconds;
        this.expectedSamplesPerMin = expectedSamplesPerMin;
        this.maxOutOfOrdernessSeconds = maxOutOfOrdernessSeconds;
        this.parallelism = parallelism;
        this.cosinorHours = cosinorHours;
        this.cwtDays = cwtDays;
        this.hopHours = hopHours;
        this.cwtOmega0 = cwtOmega0;
        this.cwtVoices = cwtVoices;
        this.cwtMinPeriodHours = cwtMinPeriodHours;
        this.cwtMaxPeriodHours = cwtMaxPeriodHours;
        this.bandLowHours = bandLowHours;
        this.bandHighHours = bandHighHours;
        this.cosinorMinCoverage = cosinorMinCoverage;
        this.cwtMinCoverage = cwtMinCoverage;
        this.enableIsIv = enableIsIv;
    }

    public static PipelineConfig fromEnv() {
        return new PipelineConfig(
                env("KAFKA_BOOTSTRAP", "localhost:9092"),
                env("KAFKA_TOPIC", "vitals-raw"),
                env("KAFKA_GROUP", "circadian-pipeline"),
                env("JDBC_URL", "jdbc:postgresql://localhost:5544/circadian"),
                env("JDBC_USER", "circadian"),
                env("JDBC_PASSWORD", "circadian"),
                envInt("WINDOW_SECONDS", 60),
                envInt("EXPECTED_SAMPLES_PER_MIN", 60),
                envInt("MAX_OUT_OF_ORDERNESS_SECONDS", 5),
                envInt("PARALLELISM", 1),
                envInt("COSINOR_HOURS", 24),
                envInt("CWT_DAYS", 5),
                envInt("HOP_HOURS", 1),
                envDouble("CWT_OMEGA0", 6.0),
                envInt("CWT_VOICES", 8),
                envDouble("CWT_MIN_PERIOD_HOURS", 0.5),
                envDouble("CWT_MAX_PERIOD_HOURS", 48.0),
                envDouble("BAND_LOW_HOURS", 20.0),
                envDouble("BAND_HIGH_HOURS", 28.0),
                envDouble("COSINOR_MIN_COVERAGE", 0.5),
                envDouble("CWT_MIN_COVERAGE", 0.7),
                envBool("ENABLE_ISIV", true));
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int envInt(String name, int def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
    }

    private static double envDouble(String name, double def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : Double.parseDouble(v.trim());
    }

    private static boolean envBool(String name, boolean def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : Boolean.parseBoolean(v.trim());
    }

    @Override
    public String toString() {
        return "PipelineConfig{topic=" + topic + ", jdbcUrl=" + jdbcUrl
                + ", windowSeconds=" + windowSeconds + ", cosinorHours=" + cosinorHours
                + ", cwtDays=" + cwtDays + ", hopHours=" + hopHours + ", band=[" + bandLowHours
                + "," + bandHighHours + "], enableIsIv=" + enableIsIv + ", parallelism=" + parallelism + '}';
    }
}