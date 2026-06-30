-- Circadian pipeline schema. Applied automatically by the postgres container on first start (mounted into /docker-entrypoint-initdb.d). Mirrors docs/ARCHITECTURE.md.
--
-- Two families of tables:
--   ground_truth_*   written by the GENERATOR at generation time (the evaluation oracle)
--   agg_1min / circadian_metrics   written by the PIPELINE (Flink) as it processes the stream
--   experiment_runs  bookkeeping that ties every figure back to (seed, scenario, git_tag)

-- ---------------------------------------------------------------------------
-- Pipeline output: Stage 2 aggregates (1-min tumbling window per patient+signal)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agg_1min (
    patient_id   TEXT             NOT NULL,
    signal_type  TEXT             NOT NULL,
    window_start TIMESTAMPTZ      NOT NULL,
    median       DOUBLE PRECISION,           -- robust central value (Phase 0 skeleton: mean placeholder)
    sd           DOUBLE PRECISION,           -- dispersion within the window
    completeness REAL,                       -- fraction of expected samples present [0,1]
    PRIMARY KEY (patient_id, signal_type, window_start)
);
CREATE INDEX IF NOT EXISTS idx_agg_1min_window ON agg_1min (window_start);

-- ---------------------------------------------------------------------------
-- Pipeline output: circadian metrics (one row per method per hop)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS circadian_metrics (
    patient_id   TEXT             NOT NULL,
    signal_type  TEXT             NOT NULL,
    method       TEXT             NOT NULL,  -- 'cosinor' | 'cwt' | 'isiv'
    window_end   TIMESTAMPTZ      NOT NULL,
    mesor        DOUBLE PRECISION,           -- cosinor
    amplitude    DOUBLE PRECISION,           -- cosinor
    acrophase    DOUBLE PRECISION,           -- cosinor (radians)
    r2           DOUBLE PRECISION,           -- cosinor goodness-of-fit
    cri          DOUBLE PRECISION,           -- cwt circadian rhythm index
    is_stability DOUBLE PRECISION,           -- isiv inter-daily stability
    iv_variab    DOUBLE PRECISION,           -- isiv intra-daily variability
    PRIMARY KEY (patient_id, signal_type, method, window_end)
);
CREATE INDEX IF NOT EXISTS idx_metrics_window ON circadian_metrics (window_end);

-- ---------------------------------------------------------------------------
-- Ground truth: every injected disruption (written by the generator)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ground_truth_events (
    patient_id   TEXT             NOT NULL,
    signal_type  TEXT             NOT NULL,
    event_type   TEXT             NOT NULL,  -- 'amplitude_drop' | 'phase_shift' | 'fragmentation'
    start_time   TIMESTAMPTZ      NOT NULL,
    end_time     TIMESTAMPTZ,
    magnitude    DOUBLE PRECISION,
    params_json  JSONB,
    PRIMARY KEY (patient_id, signal_type, event_type, start_time)
);

-- ---------------------------------------------------------------------------
-- Ground truth: per-patient true baseline circadian parameters (pre-disruption)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ground_truth_params (
    patient_id   TEXT             NOT NULL,
    signal_type  TEXT             NOT NULL,
    mesor        DOUBLE PRECISION,
    amplitude    DOUBLE PRECISION,
    acrophase    DOUBLE PRECISION,           -- radians
    noise_sd     DOUBLE PRECISION,
    PRIMARY KEY (patient_id, signal_type)
);

-- ---------------------------------------------------------------------------
-- Experiment bookkeeping: one row per run, ties results to a build + seed
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS experiment_runs (
    run_id       TEXT PRIMARY KEY,
    experiment   TEXT,                       -- 'A' | 'B' | 'C' | 'D'
    scenario     TEXT,
    seed         BIGINT,
    git_tag      TEXT,
    started_at   TIMESTAMPTZ,
    notes        TEXT
);

-- ---------------------------------------------------------------------------
-- Pipeline output: Stage 1 rejects (out-of-range samples), kept for inspection
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dead_letter (
    id           BIGSERIAL PRIMARY KEY,
    patient_id   TEXT,
    signal_type  TEXT,
    event_time   TIMESTAMPTZ,
    value        DOUBLE PRECISION,
    unit         TEXT,
    reason       TEXT,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dead_letter_time ON dead_letter (event_time);
