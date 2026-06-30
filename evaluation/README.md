# evaluation

Python tooling that validates the Java analytics and (later) turns experiment results into thesis
figures. Reads from Postgres / the Java reference exports; never re-runs the pipeline.

## Setup

```powershell
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
```

## CWT reference parity (`validate_cwt.py`)

Confirms the hand-written Java `MorletCwt` matches a reference implementation (PyWavelets), since
there is no production-quality CWT library for Java. The Java CWT's output on canonical signals is
checked in under `reference/` (the golden export); run the comparison from the project root:

```powershell
evaluation\.venv\Scripts\python evaluation\validate_cwt.py
```

To **regenerate** the golden reference (e.g. after changing the CWT), run the Java exporter first;
it depends on the `analytics` module but lives here in `evaluation/` (tooling, not a project module):

```powershell
evaluation\cwt_reference\export-reference.ps1
```

It compares the time-averaged ("global") wavelet spectrum: **dominant period** (must match), the
**normalized spectrum shape** (correlation), and the **CRI ordering**. Absolute CRI differs between
implementations (normalization conventions differ), so the metric is validated *relatively*, not as
an absolute. Output: a PASS/FAIL table and `figures/cwt_parity.png`.

Reference CSVs in `reference/` are checked in (the golden Java output); `figures/` and `.venv/` are
gitignored.
