package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.DeadLetterRecord;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Batched Sink appending Stage 1 rejects to the Postgres {@code dead_letter} table (append-only, one row per rejected sample). Batching + checkpoint-flush come from {@link JdbcSink}.
public final class DeadLetterPostgresSink extends JdbcSink<DeadLetterRecord> {

    private static final long serialVersionUID = 2L;

    private static final String INSERT =
            "INSERT INTO dead_letter (patient_id, signal_type, event_time, value, unit, reason) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    public DeadLetterPostgresSink(PipelineConfig config) {
        super(config);
    }

    @Override
    protected String sql() {
        return INSERT;
    }

    @Override
    protected void bind(PreparedStatement ps, DeadLetterRecord r) throws SQLException {
        ps.setString(1, r.getPatientId());
        ps.setString(2, r.getSignalType());
        ps.setObject(3, OffsetDateTime.ofInstant(r.getEventTime(), ZoneOffset.UTC));
        ps.setDouble(4, r.getValue());
        ps.setString(5, r.getUnit());
        ps.setString(6, r.getReason());
    }
}