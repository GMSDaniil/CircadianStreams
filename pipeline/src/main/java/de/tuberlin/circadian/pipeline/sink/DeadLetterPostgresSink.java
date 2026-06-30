package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.DeadLetterRecord;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Appends Stage 1 rejects to the Postgres {@code dead_letter} table (Flink Sink V2). Append-only —
 * each rejected sample is one row, kept for inspection.
 */
public final class DeadLetterPostgresSink implements Sink<DeadLetterRecord> {

    private static final long serialVersionUID = 1L;

    private static final String INSERT =
            "INSERT INTO dead_letter (patient_id, signal_type, event_time, value, unit, reason) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private final PipelineConfig config;

    public DeadLetterPostgresSink(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public SinkWriter<DeadLetterRecord> createWriter(WriterInitContext context) throws IOException {
        try {
            return new JdbcWriter(config);
        } catch (SQLException e) {
            throw new IOException("Could not open Postgres connection at " + config.jdbcUrl, e);
        }
    }

    private static final class JdbcWriter implements SinkWriter<DeadLetterRecord> {

        private final Connection connection;
        private final PreparedStatement insert;

        JdbcWriter(PipelineConfig config) throws SQLException {
            this.connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            this.connection.setAutoCommit(true);
            this.insert = connection.prepareStatement(INSERT);
        }

        @Override
        public void write(DeadLetterRecord r, Context context) throws IOException {
            try {
                insert.setString(1, r.getPatientId());
                insert.setString(2, r.getSignalType());
                insert.setObject(3, OffsetDateTime.ofInstant(r.getEventTime(), ZoneOffset.UTC));
                insert.setDouble(4, r.getValue());
                insert.setString(5, r.getUnit());
                insert.setString(6, r.getReason());
                insert.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("Failed to insert dead-letter row: " + r.getReason(), e);
            }
        }

        @Override
        public void flush(boolean endOfInput) { }

        @Override
        public void close() throws Exception {
            try (Connection c = connection; PreparedStatement p = insert) { }
        }
    }
}