package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.AggregateRecord;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Batched Sink writing Stage 2 aggregates to {@code agg_1min}. Upsert, so reprocessing (e.g. a restart reading {@code earliest}) is idempotent. Batching + checkpoint-flush come from {@link JdbcSink}.
public final class AggregatePostgresSink extends JdbcSink<AggregateRecord> {

    private static final long serialVersionUID = 2L;

    private static final String UPSERT =
            "INSERT INTO agg_1min (patient_id, signal_type, window_start, median, sd, completeness) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (patient_id, signal_type, window_start) DO UPDATE SET "
                    + "median = EXCLUDED.median, sd = EXCLUDED.sd, completeness = EXCLUDED.completeness";

    public AggregatePostgresSink(PipelineConfig config) {
        super(config);
    }

    @Override
    protected String sql() {
        return UPSERT;
    }

    @Override
    protected void bind(PreparedStatement ps, AggregateRecord r) throws SQLException {
        ps.setString(1, r.getPatientId());
        ps.setString(2, r.getSignalType());
        ps.setObject(3, OffsetDateTime.ofInstant(r.getWindowStart(), ZoneOffset.UTC));
        ps.setDouble(4, r.getMedian());
        ps.setDouble(5, r.getSd());
        ps.setDouble(6, r.getCompleteness());
    }
}