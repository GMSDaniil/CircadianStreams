# Real-Time Circadian Analysis Pipeline

Streaming pipeline that consumes high-frequency ICU vital-sign data, computes circadian-rhythm
metrics (Cosinor + CWT) on sliding windows, persists them to PostgreSQL, and serves them to a
clinician dashboard. Implementation behind a B.Sc. thesis (TU Berlin / Charité, Institute of
Medical Informatics). All data is **synthetic with ground truth**, generated for evaluation.

## Architecture (target)

```
SyntheticSource ─▶ Kafka (vitals-raw) ─▶ [ Flink job ] ─▶ PostgreSQL ─▶ REST API ─▶ Grafana
                                              │
                                   Stage 1  Ingestion & Validation
                                   Stage 2  1-min Tumbling Aggregation
                                   Stage 3a Cosinor (24 h sliding)
                                   Stage 3b CWT (5-day sliding) ─▶ CRI
                                   Stage 4  Sink (Postgres)
```

## Modules

| Module      | Responsibility                                              | Phase 0 state |
|-------------|-------------------------------------------------------------|---------------|
| `common`    | Message schema, domain models, JSON serde (no Flink/Kafka)  | implemented   |
| `analytics` | Pure algorithms: Cosinor, CWT, IS/IV (no Flink/Kafka)       | implemented — Cosinor, Morlet CWT (CRI), IS/IV, window stats; unit-tested against known signals |
| `generator` | Synthetic generator → Kafka producer + ground-truth writer  | scenarios + injectable disruptions + fidelity (correlated noise/artifacts/gaps); writes `ground_truth_params` + `ground_truth_events` |
| `pipeline`  | Flink job: stage wiring + Postgres sink                     | Stages 1–3: validation + dead-letter, 1-min median/SD, sliding Cosinor/CWT/IS-IV → `circadian_metrics` |
| `api`       | Thin REST API over Postgres                                 | stub (Phase 3) |

## Prerequisites

- **JDK 17** (Flink 2.2 does not support newer JDKs). Point `JAVA_HOME` at it to build/run; the
  system default JDK is left untouched. (The helper scripts auto-detect a scoop `temurin17-jdk` or
  honour `JAVA17_HOME`.)
- **Maven 3.9+**.
- **Docker** (Kafka + Postgres + Grafana via Compose).

## Quick start

```powershell
# 1. Local infrastructure (Kafka :9092, Postgres :5544, Grafana :3000)
docker compose up -d

# 2. Build everything (requires JDK 17 + Maven 3.9+)
mvn clean package

# 3. Generate from a scenario (ground truth -> Postgres, samples -> Kafka; deterministic via --seed)
java -jar generator/target/circadian-generator.jar --scenario scenarios/amplitude_decay.yaml --seed 42
#    or a quick ad-hoc clean cohort:  java -jar generator/target/circadian-generator.jar --patients 3 --sim-hours 3

# 4. Run the pipeline (embedded Flink MiniCluster; Ctrl+C to stop).
#    On Java 17 it needs Flink's --add-opens / --add-exports JVM args (see Notes) — easiest via the helper script.
java -jar pipeline/target/circadian-pipeline.jar
```

## Dashboard

After `docker compose up -d` and a pipeline run, open **http://localhost:3000** (anonymous viewing
is enabled) → folder **Circadian** → **Patient Detail** (`/d/circadian-detail`). Choose a patient and
signal to see the 1-min median, the Cosinor amplitude (it falls when a disruption is injected), the
CWT CRI, R², IS/IV, and a table of the ground-truth disruptions. Dashboards are provisioned as code
under `grafana/` (datasource + dashboard JSON), so they come up with the stack.

## Scenarios

YAML files in `scenarios/` define deterministic experiment configs (cohort size, duration, rate,
signal fidelity, and a disruption schedule). Pass one with `--scenario`; `--seed` is always honoured.

| Scenario | Purpose |
|---|---|
| `baseline.yaml` | Stable rhythms, no disruptions (Exp A correctness) |
| `amplitude_decay.yaml` | Gradual amplitude flattening (the non-stationary case for CWT, Exp B) |
| `phase_shift.yaml` | Gradual acrophase shift (Exp B) |
| `fragmentation.yaml` | Rhythm breaks into short bouts (Exp B) |
| `cohort.yaml` | Mixed preserved/disrupted cohort (Exp D / RQ3) |
| `smoke.yaml` | Tiny scenario for a fast end-to-end check |

## Configuration

- **Generator:** CLI flags (`--patients`, `--seed`, `--sim-hours`, `--rate-hz`, `--bootstrap`, `--jdbc-url`, …). Run with `--help` for the full list.
- **Pipeline:** environment variables. Infra/Stage 1-2: `KAFKA_BOOTSTRAP`, `KAFKA_TOPIC`,
  `KAFKA_GROUP`, `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `WINDOW_SECONDS`,
  `EXPECTED_SAMPLES_PER_MIN`, `PARALLELISM`, `MAX_OUT_OF_ORDERNESS_SECONDS`. Stage 3 (the
  detection-latency knobs): `COSINOR_HOURS` (24), `CWT_DAYS` (5), `HOP_HOURS` (1), `CWT_OMEGA0` (6),
  `CWT_VOICES` (8), `CWT_MIN_PERIOD_HOURS` (0.5), `CWT_MAX_PERIOD_HOURS` (48), `BAND_LOW_HOURS` (20),
  `BAND_HIGH_HOURS` (28), `COSINOR_MIN_COVERAGE` (0.5), `CWT_MIN_COVERAGE` (0.7), `ENABLE_ISIV` (true).