"""SOLO PARA PROBAR QUE EL PIPELINE FUNCIONA: genera senales SINTETICAS (inventadas).

Nunca uses este modelo ni sus metricas en la presentacion ni en el backend.
Uso: python ml/har/tools/make_synthetic_data.py
     python ml/har/train_har.py --data ml/har/data/synthetic --out ml/har/data/synthetic_model.joblib --reports ml/har/data/synthetic_reports
"""
from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parents[1] / "data" / "synthetic"
RATE = 50
PATTERNS = {  # (frecuencia Hz, amplitud acelerometro, amplitud giroscopio)
    "reposo": (0.0, 0.05, 0.02),
    "caminar": (1.8, 2.5, 1.0),
    "trotar": (2.6, 7.0, 2.5),
    "saltos": (1.5, 12.0, 1.5),
    "sentadillas": (0.4, 3.0, 0.6),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(0)
    for s in ["SYN1", "SYN2", "SYN3"]:
        for act, (f, aa, ga) in PATTERNS.items():
            n = 60 * RATE
            t = np.arange(n) / RATE
            ph = rng.uniform(0, 6.28)
            wave = np.sin(2 * np.pi * f * t + ph) if f else np.zeros(n)
            acc = np.column_stack([
                aa * 0.3 * wave, 9.81 + aa * wave, aa * 0.5 * np.cos(2 * np.pi * f * t + ph),
            ]) + rng.normal(0, 0.15, (n, 3))
            gyr = np.column_stack([ga * wave, ga * 0.4 * wave, ga * 0.2 * wave]) + rng.normal(0, 0.05, (n, 3))
            data = np.column_stack([np.round(t * 1000), acc, gyr])
            path = OUT / f"{s}_{act}_bolsillo_20260101_000000.csv"
            np.savetxt(path, data, delimiter=",", header="t_ms,ax,ay,az,gx,gy,gz", comments="", fmt="%.4f")
    print(f"Datos sinteticos en {OUT}")


if __name__ == "__main__":
    main()
