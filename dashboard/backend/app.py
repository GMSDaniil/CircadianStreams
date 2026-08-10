from pathlib import Path
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
import cwt as cwtmod
import db
import dlt

app = FastAPI(title="Circadian Patient Overview")

@app.middleware("http")
async def no_store(request, call_next):
    resp = await call_next(request)
    resp.headers["Cache-Control"] = "no-store"
    return resp

@app.get("/api/patients")
def api_patients():
    return db.patients()

@app.get("/api/overview")
def api_overview(patient: str, signal: str):
    med = db.median_series(patient, signal)
    cos = db.metrics(patient, signal, "cosinor")
    cri = db.metrics(patient, signal, "cwt")
    isiv = db.metrics(patient, signal, "isiv")
    gt = db.ground_truth(patient, signal)
    return {
        "patient": patient,
        "signal": signal,
        "median": [
            {"t": r["window_start"], "value": r["median"], "completeness": r["completeness"]}
            for r in med
        ],
        "cosinor": [
            {"t": r["window_end"], "amplitude": r["amplitude"], "r2": r["r2"],
             "acrophase": r["acrophase"], "mesor": r["mesor"]}
            for r in cos
        ],
        "cri": [{"t": r["window_end"], "cri": r["cri"]} for r in cri],
        "isiv": [{"t": r["window_end"], "is": r["is_stability"], "iv": r["iv_variab"]} for r in isiv],
        "ground_truth": [
            {"type": r["event_type"], "start": r["start_time"], "end": r["end_time"],
             "magnitude": r["magnitude"]}
            for r in gt
        ],
    }

@app.get("/api/scalogram")
def api_scalogram(patient: str, signal: str):
    med = db.median_series(patient, signal)
    return cwtmod.scalogram([r["window_start"] for r in med], [r["median"] for r in med])

def _last(rows, key):
    for r in reversed(rows):
        v = r.get(key)
        if v is not None:
            return float(v)
    return None

@app.get("/api/lighttherapy")
def api_lighttherapy(patient: str, signal: str):
    """24 h Dynamic Lighting Therapy schedule derived from this signal's latest Cosinor acrophase +
    CWT CRI (rhythm strength). The 'light ceiling' Grafana can't render, integrated into the overview."""
    acro = _last(db.metrics(patient, signal, "cosinor"), "acrophase")
    cri = _last(db.metrics(patient, signal, "cwt"), "cri")
    if acro is None:
        return {"available": False, "reason": "no Cosinor acrophase for this signal yet"}
    return {"available": True, "signal": signal, "acrophase": acro, **dlt.schedule(acro, cri)}

_FRONTEND = Path(__file__).resolve().parent.parent / "frontend"
app.mount("/", StaticFiles(directory=str(_FRONTEND), html=True), name="frontend")