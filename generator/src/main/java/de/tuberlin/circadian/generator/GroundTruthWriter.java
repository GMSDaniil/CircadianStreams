package de.tuberlin.circadian.generator;

import de.tuberlin.circadian.common.model.SignalType;
import de.tuberlin.circadian.generator.model.DisruptionType;
import de.tuberlin.circadian.generator.model.SignalBaseline;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persists the generator's ground truth to Postgres. Ground truth is the oracle every experiment
 * joins against, so it is written at generation time, before any samples are streamed.
 */
public final class GroundTruthWriter implements AutoCloseable {

    private static final String UPSERT_PARAMS =
            "INSERT INTO ground_truth_params "
                    + "(patient_id, signal_type, mesor, amplitude, acrophase, noise_sd) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (patient_id, signal_type) DO UPDATE SET "
                    + "mesor = EXCLUDED.mesor, amplitude = EXCLUDED.amplitude, "
                    + "acrophase = EXCLUDED.acrophase, noise_sd = EXCLUDED.noise_sd";

    private static final String UPSERT_EVENT =
            "INSERT INTO ground_truth_events "
                    + "(patient_id, signal_type, event_type, start_time, end_time, magnitude, params_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb) "
                    + "ON CONFLICT (patient_id, signal_type, event_type, start_time) DO UPDATE SET "
                    + "end_time = EXCLUDED.end_time, magnitude = EXCLUDED.magnitude, "
                    + "params_json = EXCLUDED.params_json";

    private final Connection connection;

    public GroundTruthWriter(String jdbcUrl, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl, user, password);
        this.connection.setAutoCommit(false);
    }

    public void writeParams(String patientId, SignalBaseline baseline) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_PARAMS)) {
            ps.setString(1, patientId);
            ps.setString(2, baseline.signalType().name());
            ps.setDouble(3, baseline.mesor());
            ps.setDouble(4, baseline.amplitude());
            ps.setDouble(5, baseline.acrophaseRad());
            ps.setDouble(6, baseline.noiseSd());
            ps.executeUpdate();
        }
    }

    // Records one injected disruption as the evaluation oracle for detection experiments. 
    public void writeEvent(String patientId, SignalType signal, DisruptionType type, Instant start, Instant end, double magnitude, String paramsJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_EVENT)) {
            ps.setString(1, patientId);
            ps.setString(2, signal.name());
            ps.setString(3, type.dbName());
            ps.setObject(4, OffsetDateTime.ofInstant(start, ZoneOffset.UTC));
            ps.setObject(5, OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
            ps.setDouble(6, magnitude);
            ps.setString(7, paramsJson);
            ps.executeUpdate();
        }
    }

    public void commit() throws SQLException {
        connection.commit();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}