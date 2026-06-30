package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Writes Stage 3 circadian metrics to the Postgres circadian_metrics table (Flink Sink V2).
 * One row per (patient, signal, method, window_end); upserted so reprocessing is idempotent.
 * Method-specific columns that don't apply are written as SQL NULL.
 */
public final class CircadianMetricsSink implements Sink<CircadianMetric> {

    private static final long serialVersionUID = 1L;

    private static final String UPSERT =
            "INSERT INTO circadian_metrics "
                    + "(patient_id, signal_type, method, window_end, mesor, amplitude, acrophase, r2, "
                    + " cri, is_stability, iv_variab) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (patient_id, signal_type, method, window_end) DO UPDATE SET "
                    + "mesor = EXCLUDED.mesor, amplitude = EXCLUDED.amplitude, "
                    + "acrophase = EXCLUDED.acrophase, r2 = EXCLUDED.r2, cri = EXCLUDED.cri, "
                    + "is_stability = EXCLUDED.is_stability, iv_variab = EXCLUDED.iv_variab";

    private final PipelineConfig config;

    public CircadianMetricsSink(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public SinkWriter<CircadianMetric> createWriter(WriterInitContext context) throws IOException {
        try {
            return new JdbcWriter(config);
        } catch (SQLException e) {
            throw new IOException("Could not open Postgres connection at " + config.jdbcUrl, e);
        }
    }

    private static final class JdbcWriter implements SinkWriter<CircadianMetric> {

        private final Connection connection;
        private final PreparedStatement upsert;

        JdbcWriter(PipelineConfig config) throws SQLException {
            this.connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            this.connection.setAutoCommit(true);
            this.upsert = connection.prepareStatement(UPSERT);
        }

        @Override
        public void write(CircadianMetric m, Context context) throws IOException {
            try {
                upsert.setString(1, m.getPatientId());
                upsert.setString(2, m.getSignalType());
                upsert.setString(3, m.getMethod());
                upsert.setObject(4, OffsetDateTime.ofInstant(m.getWindowEnd(), ZoneOffset.UTC));
                setNullableDouble(5, m.getMesor());
                setNullableDouble(6, m.getAmplitude());
                setNullableDouble(7, m.getAcrophase());
                setNullableDouble(8, m.getR2());
                setNullableDouble(9, m.getCri());
                setNullableDouble(10, m.getIsStability());
                setNullableDouble(11, m.getIvVariab());
                upsert.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("Failed to upsert metric: " + m.getMethod(), e);
            }
        }

        private void setNullableDouble(int idx, Double value) throws SQLException {
            if (value == null || value.isNaN()) {
                upsert.setNull(idx, Types.DOUBLE);
            } else {
                upsert.setDouble(idx, value);
            }
        }

        @Override
        public void flush(boolean endOfInput) { }

        @Override
        public void close() throws Exception {
            try (Connection c = connection; PreparedStatement p = upsert) { }
        }
    }
}