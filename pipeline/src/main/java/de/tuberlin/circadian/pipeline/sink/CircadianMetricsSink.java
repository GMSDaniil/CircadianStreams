package de.tuberlin.circadian.pipeline.sink;

import de.tuberlin.circadian.pipeline.PipelineConfig;
import de.tuberlin.circadian.pipeline.model.CircadianMetric;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Batched Sink writing Stage 3 circadian metrics to {@code circadian_metrics}: one row per (patient, signal, method, window_end), upserted. Method-specific columns that don't apply are written as SQL NULL.
public final class CircadianMetricsSink extends JdbcSink<CircadianMetric> {

    private static final long serialVersionUID = 2L;

    private static final String UPSERT =
            "INSERT INTO circadian_metrics "
                    + "(patient_id, signal_type, method, window_end, mesor, amplitude, acrophase, r2, "
                    + " cri, is_stability, iv_variab) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (patient_id, signal_type, method, window_end) DO UPDATE SET "
                    + "mesor = EXCLUDED.mesor, amplitude = EXCLUDED.amplitude, "
                    + "acrophase = EXCLUDED.acrophase, r2 = EXCLUDED.r2, cri = EXCLUDED.cri, "
                    + "is_stability = EXCLUDED.is_stability, iv_variab = EXCLUDED.iv_variab";

    public CircadianMetricsSink(PipelineConfig config) {
        super(config);
    }

    @Override
    protected String sql() {
        return UPSERT;
    }

    @Override
    protected void bind(PreparedStatement ps, CircadianMetric m) throws SQLException {
        ps.setString(1, m.getPatientId());
        ps.setString(2, m.getSignalType());
        ps.setString(3, m.getMethod());
        ps.setObject(4, OffsetDateTime.ofInstant(m.getWindowEnd(), ZoneOffset.UTC));
        setNullableDouble(ps, 5, m.getMesor());
        setNullableDouble(ps, 6, m.getAmplitude());
        setNullableDouble(ps, 7, m.getAcrophase());
        setNullableDouble(ps, 8, m.getR2());
        setNullableDouble(ps, 9, m.getCri());
        setNullableDouble(ps, 10, m.getIsStability());
        setNullableDouble(ps, 11, m.getIvVariab());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null || value.isNaN()) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, value);
        }
    }
}