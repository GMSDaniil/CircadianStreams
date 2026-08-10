import os
import psycopg2
import psycopg2.extras

DSN = dict(
    host=os.getenv("PGHOST", "localhost"),
    port=int(os.getenv("PGPORT", "5544")),
    dbname=os.getenv("PGDATABASE", "circadian"),
    user=os.getenv("PGUSER", "circadian"),
    password=os.getenv("PGPASSWORD", "circadian"),
)

def query(sql, params=()):
    conn = psycopg2.connect(**DSN)
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            return cur.fetchall()
    finally:
        conn.close()

def patients():
    """Patients that have data, each with the signals available for them."""
    rows = query(
        "SELECT patient_id, array_agg(DISTINCT signal_type ORDER BY signal_type) AS signals "
        "FROM agg_1min GROUP BY patient_id ORDER BY patient_id"
    )
    return [{"patient_id": r["patient_id"], "signals": r["signals"]} for r in rows]

def median_series(patient, signal):
    return query(
        "SELECT window_start, median, completeness FROM agg_1min "
        "WHERE patient_id=%s AND signal_type=%s ORDER BY window_start",
        (patient, signal),
    )

def metrics(patient, signal, method):
    return query(
        "SELECT window_end, mesor, amplitude, acrophase, r2, cri, is_stability, iv_variab "
        "FROM circadian_metrics WHERE patient_id=%s AND signal_type=%s AND method=%s "
        "ORDER BY window_end",
        (patient, signal, method),
    )

def ground_truth(patient, signal):
    return query(
        "SELECT event_type, start_time, end_time, magnitude FROM ground_truth_events "
        "WHERE patient_id=%s AND signal_type=%s ORDER BY start_time",
        (patient, signal),
    )