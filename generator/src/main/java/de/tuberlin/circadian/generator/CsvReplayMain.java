package de.tuberlin.circadian.generator;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.common.model.VitalSample;
import de.tuberlin.circadian.common.serde.VitalSampleCodec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;


// Replays a real HDP/DWC CSV export onto the {@code vitals-raw} Kafka topic, mapping each row to the {@link VitalSample} schema. Sibling of {@link GeneratorMain}: same topic + schema, but real data.
@Command(name = "csv-replay", mixinStandardHelpOptions = true, version = "circadian-csv-replay 0.1.0", description = "Replay a real HDP/DWC CSV export to Kafka as VitalSample messages.")
public final class CsvReplayMain implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(CsvReplayMain.class);

    // HDP/DWC columns we read.
    private static final String COL_TS = "c_time_stamp_numeric";
    private static final String COL_LABEL = "c_label";
    private static final String COL_SUBLABEL = "c_sub_label";
    private static final String COL_VALUE = "c_value";
    private static final String COL_UNIT = "c_unit_label";
    private static final String COL_PATIENT = "p_patnr";

    // Real device labels -> our 5 modelled signals. Keys are normalized (uppercase, alphanumerics only).
    private static final Map<String, SignalType> SIGNAL_MAP = new LinkedHashMap<>();
    static {
        SIGNAL_MAP.put("HR", SignalType.HR);
        SIGNAL_MAP.put("HRECG", SignalType.HR);
        SIGNAL_MAP.put("PULSE", SignalType.HR);
        SIGNAL_MAP.put("ABPS", SignalType.ABP_SYS);
        SIGNAL_MAP.put("NBPS", SignalType.ABP_SYS);
        SIGNAL_MAP.put("ARTS", SignalType.ABP_SYS);
        SIGNAL_MAP.put("ABPD", SignalType.ABP_DIA);
        SIGNAL_MAP.put("NBPD", SignalType.ABP_DIA);
        SIGNAL_MAP.put("ARTD", SignalType.ABP_DIA);
        SIGNAL_MAP.put("SPO2", SignalType.SPO2);
        SIGNAL_MAP.put("SAO2", SignalType.SPO2);
        SIGNAL_MAP.put("RR", SignalType.RR);
        SIGNAL_MAP.put("RESP", SignalType.RR);
        SIGNAL_MAP.put("AWRR", SignalType.RR);
        // German (Charité / Dräger / Philips-DE) monitor labels:
        SIGNAL_MAP.put("HF", SignalType.HR);    // Herzfrequenz (heart rate)
        SIGNAL_MAP.put("PULS", SignalType.HR);  // Puls
        SIGNAL_MAP.put("AF", SignalType.RR);    // Atemfrequenz (respiratory rate)
    }

    // e.g. "2025-06-01 00:00:00.000 +02:00" — offset preserved.
    private static final DateTimeFormatter TS_FMT = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().appendPattern(" XXX").toFormatter();

    @Option(names = "--file", required = true, description = "CSV export path.")
    String file;

    @Option(names = "--bootstrap", defaultValue = "localhost:9092", description = "Kafka bootstrap (default: ${DEFAULT-VALUE}).")
    String bootstrap;

    @Option(names = "--topic", defaultValue = "vitals-raw", description = "Kafka topic (default: ${DEFAULT-VALUE}).")
    String topic;

    @Option(names = "--patient", description = "Only replay this p_patnr (default: all patients in the file).")
    String patient;

    @Option(names = "--speed", defaultValue = "0", description = "Replay speed: 0 = as fast as possible; N = N simulated seconds per wall second.")
    double speed;

    @Option(names = "--limit", defaultValue = "0", description = "Max samples to produce (0 = no limit); for quick tests.")
    long limit;

    @Option(names = "--dry-run", defaultValue = "false", description = "Parse + map + summarize only; do NOT produce to Kafka.")
    boolean dryRun;

    @Override
    public Integer call() throws Exception {
        List<VitalSample> samples = new ArrayList<>();
        long read = 0;
        long badValue = 0;
        long badTime = 0;
        long filtered = 0;
        Map<String, Long> unmapped = new TreeMap<>();

        CsvMapper csv = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        ObjectReader reader = csv.readerForMapOf(String.class).with(schema);
        try (MappingIterator<Map<String, String>> it = reader.readValues(new File(file))) {
            while (it.hasNext()) {
                Map<String, String> row = it.next();
                read++;
                String rawLabel = firstNonBlank(row.get(COL_SUBLABEL), row.get(COL_LABEL));
                SignalType sig = SIGNAL_MAP.get(normalize(rawLabel));
                if (sig == null) {
                    unmapped.merge(rawLabel == null ? "(blank)" : rawLabel, 1L, Long::sum);
                    continue;
                }
                String pid = trimOrNull(row.get(COL_PATIENT));
                if (patient != null && !patient.equals(pid)) {
                    filtered++;
                    continue;
                }
                Double value = parseDouble(row.get(COL_VALUE));
                if (value == null) {
                    badValue++;
                    continue;
                }
                Instant ts = parseTimestamp(row.get(COL_TS));
                if (ts == null) {
                    badTime++;
                    continue;
                }
                samples.add(new VitalSample(pid, sig, ts, value, trimOrNull(row.get(COL_UNIT))));
            }
        }

        samples.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        if (limit > 0 && samples.size() > limit) {
            samples = new ArrayList<>(samples.subList(0, (int) limit));
        }

        logSummary(read, samples, unmapped, badValue, badTime, filtered);

        if (samples.isEmpty()) {
            LOG.warn("No mappable samples — check the signal mapping / column names against your export.");
            return 0;
        }
        if (dryRun) {
            LOG.info("Dry run: nothing produced. Re-run without --dry-run to stream to Kafka.");
            return 0;
        }

        long produced = streamToKafka(samples);
        LOG.info("Done. Produced {} samples to topic '{}'.", produced, topic);
        LOG.info("Reminder: submit a FRESH pipeline job so its watermark starts before this data's event-time ({}), and clear the topic first if it holds other runs.", samples.get(0).getTimestamp());
        return 0;
    }

    private long streamToKafka(List<VitalSample> samples) {
        long produced = 0;
        long firstMillis = samples.get(0).getTimestamp().toEpochMilli();
        long wallStartNanos = System.nanoTime();
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (VitalSample s : samples) {
                producer.send(new ProducerRecord<>(topic, s.getPatientId(), VitalSampleCodec.toJson(s)));
                produced++;
                throttle(s.getTimestamp().toEpochMilli() - firstMillis, wallStartNanos);
                if (produced % 100_000 == 0) {
                    LOG.info("  produced {} / {}", produced, samples.size());
                }
            }
            producer.flush();
        }
        return produced;
    }

    // Pace to wall-clock when --speed > 0; no-op when 0 (as fast as possible).
    private void throttle(long simMillisSinceStart, long wallStartNanos) {
        if (speed <= 0) {
            return;
        }
        long targetNanos = wallStartNanos + Math.round(simMillisSinceStart / speed * 1_000_000.0);
        long sleepNanos = targetNanos - System.nanoTime();
        if (sleepNanos > 0) {
            try {
                Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void logSummary(long read, List<VitalSample> samples, Map<String, Long> unmapped, long badValue, long badTime, long filtered) {
        Map<SignalType, Long> bySignal = new TreeMap<>();
        Set<String> patients = new TreeSet<>();
        Instant min = null;
        Instant max = null;
        for (VitalSample s : samples) {
            bySignal.merge(s.getSignalType(), 1L, Long::sum);
            patients.add(s.getPatientId());
            if (min == null || s.getTimestamp().isBefore(min))
                min = s.getTimestamp();
            if (max == null || s.getTimestamp().isAfter(max))
                max = s.getTimestamp();
        }
        LOG.info("Read {} rows -> {} mappable samples.", read, samples.size());
        LOG.info("  by signal: {}", bySignal);
        LOG.info("  patients ({}): {}", patients.size(), patients);
        LOG.info("  event-time span: {} .. {}", min, max);
        if (min != null && max != null) {
            double days = (max.toEpochMilli() - min.toEpochMilli()) / 86_400_000.0;
            LOG.info("  span: {} days (need >=2 for IS/IV, >=5 for the default 5-day CWT window)",
                    String.format(Locale.ROOT, "%.2f", days));
        }
        LOG.info("  dropped: badValue={}, badTime={}, patientFiltered={}", badValue, badTime, filtered);
        if (!unmapped.isEmpty())
            LOG.warn("  unmapped labels (add to SIGNAL_MAP if you need them): {}", unmapped);
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(64 * 1024));
        return props;
    }

    private static String normalize(String label) {
        return label == null ? "" : label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty())
            return a.trim();
        if (b != null && !b.trim().isEmpty())
            return b.trim();

        return null;
    }

    private static String trimOrNull(String s) {
        if (s == null) 
            return null;

        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double parseDouble(String s) {
        if (s == null)
            return null;

        String t = s.trim();

        if (t.isEmpty())
            return null;

        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseTimestamp(String s) {
        if (s == null)
            return null;

        try {
            return OffsetDateTime.parse(s.trim(), TS_FMT).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new CsvReplayMain()).execute(args);
        System.exit(exit);
    }
}