package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Base Flink Sink for batched JDBC writes. Buffers records with {@code addBatch} and
 * flushes ({@code executeBatch} + commit) on Flink's checkpoint / end-of-input, and whenever the
 * batch reaches {@code jdbcBatchSize}. Subclasses supply the SQL and bind each record.
 */
public abstract class JdbcSink<T> implements Sink<T> {

    private static final long serialVersionUID = 1L;

    private final PipelineConfig config;

    protected JdbcSink(PipelineConfig config) {
        this.config = config;
    }

    /** The prepared-statement SQL (an INSERT, typically with {@code ON CONFLICT DO UPDATE}). */
    protected abstract String sql();

    /** Bind one record onto the prepared statement. */
    protected abstract void bind(PreparedStatement statement, T record) throws SQLException;

    @Override
    public SinkWriter<T> createWriter(WriterInitContext context) throws IOException {
        try {
            return new BatchWriter();
        } catch (SQLException e) {
            throw new IOException("Could not open Postgres connection at " + config.jdbcUrl, e);
        }
    }

    /** Per-subtask writer: one connection + prepared statement, batched. */
    private final class BatchWriter implements SinkWriter<T> {

        private final Connection connection;
        private final PreparedStatement statement;
        private final int batchSize;
        private int pending = 0;

        BatchWriter() throws SQLException {
            this.connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            this.connection.setAutoCommit(false);
            this.statement = connection.prepareStatement(sql());
            this.batchSize = Math.max(1, config.jdbcBatchSize);
        }

        @Override
        public void write(T record, Context context) throws IOException {
            try {
                bind(statement, record);
                statement.addBatch();
                if (++pending >= batchSize) {
                    flushBatch();
                }
            } catch (SQLException e) {
                throw new IOException("Failed to add row to batch", e);
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            try {
                flushBatch();
            } catch (SQLException e) {
                throw new IOException("Failed to flush JDBC batch", e);
            }
        }

        private void flushBatch() throws SQLException {
            if (pending == 0) {
                return;
            }
            statement.executeBatch();
            connection.commit();
            pending = 0;
        }

        @Override
        public void close() throws Exception {
            try (Connection c = connection; PreparedStatement s = statement) {
                flushBatch();
            } catch (SQLException e) { }
        }
    }
}