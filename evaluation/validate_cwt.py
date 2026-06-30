"""
CWT reference parity: confirm the project's hand-written Java MorletCwt matches a reference
implementation (PyWavelets).

Run (from the project root):
  1. mvn -pl analytics test -Dcwt.export=true          # -> evaluation/reference/cwt_java_*.csv
  2. evaluation\\.venv\\Scripts\\python evaluation\\validate_cwt.py

Why a *shape* comparison: absolute scalogram power depends on each library's normalization
convention, so we compare the time-averaged ("global") wavelet spectrum after normalizing it to a
distribution over period -- i.e. WHERE the power sits, not its absolute scale. We use a complex
Morlet (`cmor`) with bandwidth/centre chosen to match our omega0=6, on the SAME period grid the Java
side used, then check: dominant period, spectrum-shape correlation, and CRI ordering.
"""
import csv
from pathlib import Path

import numpy as np
import pywt
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

N = 4320
DT = 1.0 / 60.0
BAND = (20.0, 28.0)
# Complex Morlet matching our wavelet: psi(t) ~ exp(i*w0*t)*exp(-t^2/2) with w0=6
# <=> cmor with bandwidth B=2 and centre frequency C = w0/(2*pi).
WAVELET = "cmor2.0-0.9549"

HERE = Path(__file__).resolve().parent
REF = HERE / "reference"
FIG = HERE / "figures"


def signals():
    t = np.arange(N) * DT
    decay = 1.0 - 0.8 * np.arange(N) / (N - 1.0)
    return {
        "s24": np.cos(2 * np.pi * t / 24.0),
        "s12": np.cos(2 * np.pi * t / 12.0),
        "s24_12": np.cos(2 * np.pi * t / 24.0) + 0.5 * np.cos(2 * np.pi * t / 12.0),
        "s24_decay": decay * np.cos(2 * np.pi * t / 24.0),
    }


def read_java():
    rows = list(csv.DictReader(open(REF / "cwt_java_spectrum.csv")))
    periods = sorted({float(r["period"]) for r in rows})
    idx = {p: i for i, p in enumerate(periods)}
    spec = {}
    for r in rows:
        spec.setdefault(r["signal"], np.zeros(len(periods)))
        spec[r["signal"]][idx[float(r["period"])]] = float(r["power"])
    cri = {r["signal"]: float(r["cri"]) for r in csv.DictReader(open(REF / "cwt_java_cri.csv"))}
    return np.array(periods), spec, cri


def pywt_global_spectrum(sig, periods):
    central = pywt.central_frequency(WAVELET)
    scales = periods * central / DT
    coef, _ = pywt.cwt(sig, scales, WAVELET, sampling_period=DT)
    return (np.abs(coef) ** 2).mean(axis=1)  # mean power over time, per period


def cri_from_spectrum(periods, spectrum):
    band = (periods >= BAND[0]) & (periods <= BAND[1])
    total = spectrum.sum()
    return float(spectrum[band].sum() / total) if total > 0 else 0.0


def main():
    periods, java_spec, java_cri = read_java()
    sigs = signals()
    FIG.mkdir(exist_ok=True)

    one_voice = 2.0 ** (1.0 / 12.0)  # tolerance for "same period" = one voice
    fig, axes = plt.subplots(2, 2, figsize=(12, 8))
    print(f"{'signal':10} {'peak_java':>9} {'peak_pywt':>9} {'shape_corr':>11} "
          f"{'cri_java':>9} {'cri_pywt':>9}  result")
    all_ok = True
    for ax, (name, sig) in zip(axes.ravel(), sigs.items()):
        js = java_spec[name]
        ps = pywt_global_spectrum(sig, periods)
        jn, pn = js / js.sum(), ps / ps.sum()
        corr = float(np.corrcoef(jn, pn)[0, 1])
        peak_j = periods[int(np.argmax(js))]
        peak_p = periods[int(np.argmax(ps))]
        cri_p = cri_from_spectrum(periods, ps)

        peaks_match = (max(peak_j, peak_p) / min(peak_j, peak_p)) <= one_voice
        ok = peaks_match and corr > 0.95
        all_ok &= ok
        print(f"{name:10} {peak_j:9.2f} {peak_p:9.2f} {corr:11.4f} "
              f"{java_cri[name]:9.3f} {cri_p:9.3f}  {'PASS' if ok else 'FAIL'}")

        ax.plot(periods, jn, label="Java MorletCwt", lw=2)
        ax.plot(periods, pn, "--", label="PyWavelets cmor", lw=2)
        ax.axvspan(BAND[0], BAND[1], color="orange", alpha=0.12, label="circadian band")
        ax.set_xscale("log")
        ax.set_title(name)
        ax.set_xlabel("period (h)")
        ax.set_ylabel("normalized power")
        ax.legend(fontsize=8)

    fig.suptitle("CWT parity: Java MorletCwt vs PyWavelets (normalized global spectrum)")
    fig.tight_layout()
    out = FIG / "cwt_parity.png"
    fig.savefig(out, dpi=110)
    print(f"\nplot saved: {out}")
    print(f"\nOVERALL: {'PASS - Java CWT matches the PyWavelets reference' if all_ok else 'FAIL'}")
    return 0 if all_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
