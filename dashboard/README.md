# Patient Overview Dashboard

A custom dashboard (FastAPI + Plotly.js, no build step) for a publication-grade **patient circadian overview** — the 1-min median (day/night shaded, disruptions marked), Cosinor amplitude & R², CWT CRI,
IS/IV, and the **CWT scalogram** (period × time heatmap, recomputed via PyWavelets — the one view Grafana cannot render). Reads the pipeline's Postgres output; nothing else in the system changes.

```
dashboard/
├── backend/   FastAPI: /api/patients · /api/overview · /api/scalogram + serves the frontend
│   ├── app.py  db.py  cwt.py  requirements.txt
└── frontend/  index.html · app.js · style.css   (Plotly.js via CDN)
```

## Run

1. Bring up infra and load data so Postgres has metrics (see the project README — `docker compose up -d`, generate/replay, submit the pipeline job).
2. **Set up once:**
   ```powershell
   cd dashboard/backend
   python -m venv .venv
   .venv\Scripts\pip install -r requirements.txt
   ```
3. **Start:**
   ```powershell
   .venv\Scripts\python -m uvicorn app:app --port 8000
   ```
4. Open **http://localhost:8000**, pick a patient + signal.