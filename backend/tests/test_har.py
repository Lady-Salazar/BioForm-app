"""Pruebas del reconocimiento de actividad (HAR) con senales sinteticas."""
import joblib
import numpy as np
import pytest
from fastapi.testclient import TestClient
from sklearn.ensemble import RandomForestClassifier

from app.auth import get_current_user
from app.har import classifier
from app.har.features import FEATURE_NAMES, WINDOW, features_from_signal
from app.main import app

RATE = 50


def synthetic(activity: str, seconds: int = 20, seed: int = 0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    n = seconds * RATE
    t = np.arange(n) / RATE
    f, amp = {"reposo": (0.0, 0.05), "caminar": (1.8, 2.5), "trotar": (2.6, 7.0)}[activity]
    wave = np.sin(2 * np.pi * f * t) if f else np.zeros(n)
    acc = np.column_stack([amp * 0.3 * wave, 9.81 + amp * wave, amp * 0.5 * wave]) + rng.normal(0, 0.15, (n, 3))
    gyr = np.column_stack([amp * 0.3 * wave, amp * 0.1 * wave, amp * 0.05 * wave]) + rng.normal(0, 0.05, (n, 3))
    return np.column_stack([acc, gyr])


@pytest.fixture
def trained_model(tmp_path, monkeypatch):
    X, y = [], []
    for act in ["reposo", "caminar", "trotar"]:
        for seed in range(3):
            f = features_from_signal(synthetic(act, seed=seed))
            X.append(f)
            y += [act] * len(f)
    model = RandomForestClassifier(n_estimators=50, random_state=0).fit(np.vstack(X), y)
    path = tmp_path / "har_rf.joblib"
    joblib.dump({"model": model, "feature_names": FEATURE_NAMES}, path)
    monkeypatch.setenv("HAR_MODEL_PATH", str(path))
    classifier.reset_cache()
    yield path
    classifier.reset_cache()


def test_caracteristicas_tienen_forma_correcta():
    feats = features_from_signal(synthetic("caminar", seconds=10))
    assert feats.shape[1] == len(FEATURE_NAMES)
    assert feats.shape[0] == (10 * RATE - WINDOW) // 64 + 1


def test_senal_corta_es_rechazada():
    with pytest.raises(ValueError):
        features_from_signal(np.zeros((50, 6)))


def test_clasifica_trotar(trained_model):
    result = classifier.classify(synthetic("trotar", seconds=10, seed=9).tolist())
    assert result["activity"] == "trotar"
    assert result["category"] == "aerobico"
    assert 0 <= result["confidence"] <= 1


def test_endpoint_har(trained_model):
    app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
    try:
        client = TestClient(app)
        r = client.post("/har/classify", json={"samples": synthetic("reposo", seconds=6).tolist()})
        assert r.status_code == 200
        assert r.json()["activity"] == "reposo"
        corto = client.post("/har/classify", json={"samples": [[0, 0, 9.8, 0, 0, 0]] * 10})
        assert corto.status_code == 422
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_endpoint_sin_modelo_responde_503(tmp_path, monkeypatch):
    monkeypatch.setenv("HAR_MODEL_PATH", str(tmp_path / "no_existe.joblib"))
    classifier.reset_cache()
    app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
    try:
        r = TestClient(app).post("/har/classify", json={"samples": synthetic("reposo", seconds=6).tolist()})
        assert r.status_code == 503
    finally:
        app.dependency_overrides.pop(get_current_user, None)
