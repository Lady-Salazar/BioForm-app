"""Caracteristicas de las senales inerciales para reconocer la actividad (HAR).

Este modulo lo usan EL ENTRENAMIENTO (ml/har/train_har.py) y LA INFERENCIA
(app/har/classifier.py). Asi ambos calculan exactamente las mismas
caracteristicas: si cambias algo aqui, hay que volver a entrenar el modelo.

Senal de entrada: matriz (n, 6) muestreada a 50 Hz con columnas
ax, ay, az (acelerometro, m/s2) y gx, gy, gz (giroscopio, rad/s).
"""
import numpy as np

RATE_HZ = 50
WINDOW = 128          # 2.56 s por ventana
STEP = 64             # 50 % de solapamiento
CHANNELS = ["ax", "ay", "az", "gx", "gy", "gz"]
STAT_NAMES = ["mean", "std", "min", "max", "median", "p25", "p75", "energy"]

# Actividades que reconoce el modelo y su categoria para los agentes
ACTIVITY_CATEGORY = {
    "reposo": "reposo",
    "caminar": "aerobico",
    "trotar": "aerobico",
    "saltos": "aerobico",
    "sentadillas": "fuerza",
}

FEATURE_NAMES = (
    [f"{c}_{s}" for c in CHANNELS + ["acc_mag", "gyr_mag"] for s in STAT_NAMES]
    + ["acc_mag_dom_freq"]
)


def _stats(x: np.ndarray) -> list[float]:
    return [
        x.mean(), x.std(), x.min(), x.max(), np.median(x),
        np.percentile(x, 25), np.percentile(x, 75), np.mean(x ** 2),
    ]


def window_features(w: np.ndarray) -> np.ndarray:
    """Vector de caracteristicas de UNA ventana (WINDOW x 6)."""
    acc_mag = np.linalg.norm(w[:, 0:3], axis=1)
    gyr_mag = np.linalg.norm(w[:, 3:6], axis=1)
    feats: list[float] = []
    for i in range(6):
        feats += _stats(w[:, i])
    feats += _stats(acc_mag) + _stats(gyr_mag)
    # Frecuencia dominante del movimiento (pasos, saltos, repeticiones)
    spec = np.abs(np.fft.rfft(acc_mag - acc_mag.mean()))
    freqs = np.fft.rfftfreq(len(acc_mag), d=1 / RATE_HZ)
    feats.append(float(freqs[int(spec.argmax())]))
    return np.asarray(feats, dtype=float)


def features_from_signal(signal: np.ndarray) -> np.ndarray:
    """Corta la senal en ventanas y devuelve una fila de caracteristicas por ventana."""
    signal = np.asarray(signal, dtype=float)
    if signal.ndim != 2 or signal.shape[1] != 6:
        raise ValueError("La senal debe tener 6 columnas: ax, ay, az, gx, gy, gz")
    rows = [window_features(signal[s:s + WINDOW]) for s in range(0, len(signal) - WINDOW + 1, STEP)]
    if not rows:
        raise ValueError(f"Se necesitan al menos {WINDOW} muestras ({WINDOW / RATE_HZ:.2f} s a {RATE_HZ} Hz)")
    return np.vstack(rows)
