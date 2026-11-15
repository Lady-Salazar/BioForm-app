"""Pruebas de la API sin conectarse a Firebase.

Se reemplazan dos dependencias:
  - get_current_user: simula un usuario autenticado.
  - get_saver: guarda en una lista en memoria en lugar de Firestore.
"""
from fastapi.testclient import TestClient

from app.auth import get_current_user
from app.main import app, get_saver

saved = []


def fake_saver(uid, data, rule, advice):
    saved.append((uid, data, rule, advice))


app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
app.dependency_overrides[get_saver] = lambda: fake_saver
client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_advice_con_taquicardia():
    saved.clear()
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 112, "steps": 3000})
    assert r.status_code == 200
    body = r.json()
    assert body["user_id"] == "usuario-prueba"
    assert body["rule"]["level"] == "alert"
    assert body["rule"]["block_training"] is True
    assert len(saved) == 1


def test_advice_normal():
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 64, "sleep_hours": 7.5})
    assert r.status_code == 200
    assert r.json()["rule"]["level"] == "none"


def test_valor_irreal_es_rechazado():
    # Una FC de 400 lpm es imposible: la API la rechaza con error 422.
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 400})
    assert r.status_code == 422


def test_sin_token_responde_401():
    app.dependency_overrides.pop(get_current_user)
    try:
        r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 70})
        assert r.status_code == 401
    finally:
        app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
