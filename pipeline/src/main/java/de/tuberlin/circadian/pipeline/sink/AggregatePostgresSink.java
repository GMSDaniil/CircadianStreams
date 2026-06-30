package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;
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
 * Writes Stage 2 aggregates to the Postgres agg_1min table using the Flink Sink V2 API
 * (SinkFunction/Sink V1 were removed in Flink 2.0). One JDBC connection per writer; rows
 * are upserted so reprocessing (e.g. a restart reading earliest) is idempotent.
 */
public final class AggregatePostgresSink implements Sink<AggregateRecord> {

    private static final long serialVersionUID = 1L;

    private static final String UPSERT =
            "INSERT INTO agg_1min (patient_id, signal_type, window_start, median, sd, completeness) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (patient_id, signal_type, window_start) DO UPDATE SET "
                    + "median = EXCLUDED.median, sd = EXCLUDED.sd, completeness = EXCLUDED.completeness";

    private final PipelineConfig config;

    public AggregatePostgresSink(PipelineConfig config) {
        this.config = config;
    }

    @Override
    public SinkWriter<AggregateRecord> createWriter(WriterInitContext context) throws IOException {
        try {
            return new JdbcWriter(config);
        } catch (SQLException e) {
            throw new IOException("Could not open Postgres connection at " + config.jdbcUrl, e);
        }
    }

    // Per-subtask writer holding a connection and a prepared upsert.
    private static final class JdbcWriter implements SinkWriter<AggregateRecord> {

        private final Connection connection;
        private final PreparedStatement upsert;

        JdbcWriter(PipelineConfig config) throws SQLException {
            this.connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            this.connection.setAutoCommit(true);
            this.upsert = connection.prepareStatement(UPSERT);
        }

        @Override
        public void write(AggregateRecord r, Context context) throws IOException {
            try {
                upsert.setString(1, r.getPatientId());
                upsert.setString(2, r.getSignalType());
                upsert.setObject(3, OffsetDateTime.ofInstant(r.getWindowStart(), ZoneOffset.UTC));
                upsert.setDouble(4, r.getMedian());
                upsert.setDouble(5, r.getSd());
                upsert.setDouble(6, r.getCompleteness());
                upsert.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("Failed to upsert aggregate row: " + r, e);
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