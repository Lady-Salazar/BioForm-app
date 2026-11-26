"""Carga el modelo entrenado (Random Forest) y clasifica la actividad."""
import os
from collections import Counter
from pathlib import Path

import joblib
import numpy as np

from app.har.features import ACTIVITY_CATEGORY, FEATURE_NAMES, features_from_signal

DEFAULT_MODEL_PATH = Path(__file__).resolve().parents[1] / "models" / "har_rf.joblib"

_bundle = None


def model_path() -> Path:
    return Path(os.getenv("HAR_MODEL_PATH", str(DEFAULT_MODEL_PATH)))


def is_available() -> bool:
    return model_path().exists()


def load_bundle() -> dict:
    """Carga el modelo una sola vez. Verifica que use las mismas caracteristicas."""
    global _bundle
    if _bundle is None:
        bundle = joblib.load(model_path())
        if bundle.get("feature_names") != FEATURE_NAMES:
            raise RuntimeError("El modelo se entreno con otras caracteristicas: vuelve a entrenarlo.")
        _bundle = bundle
    return _bundle


def reset_cache() -> None:
    global _bundle
    _bundle = None


def classify(samples: list[list[float]]) -> dict:
    """Clasifica cada ventana y decide por mayoria. Devuelve actividad, categoria y confianza."""
    bundle = load_bundle()
    model = bundle["model"]
    X = features_from_signal(np.asarray(samples, dtype=float))
    probs = model.predict_proba(X)
    labels = model.classes_[probs.argmax(axis=1)]
    votes = Counter(labels)
    activity, count = votes.most_common(1)[0]
    idx = list(model.classes_).index(activity)
    confidence = float(probs[:, idx].mean())
    return {
        "activity": str(activity),
        "category": ACTIVITY_CATEGORY.get(str(activity), "desconocida"),
        "confidence": round(confidence, 3),
        "windows": int(len(X)),
        "votes": {str(k): int(v) for k, v in votes.items()},
    }
