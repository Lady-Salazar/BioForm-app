"""Pruebas de la API sin conectarse a Firebase ni a Gemini.

Se reemplazan tres dependencias:
  - get_current_user: simula un usuario autenticado.
  - get_saver: guarda en una lista en memoria en lugar de Firestore.
  - get_llm: usa un modelo falso en lugar de Gemini.
"""
from fastapi.testclient import TestClient

from app.auth import get_current_user
from app.main import app, get_llm, get_saver
from tests.fake_llm import make_fake_ai

saved = []


def fake_saver(uid, data, rule, status, advice):
    saved.append((uid, data, rule, status, advice))


app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
app.dependency_overrides[get_saver] = lambda: fake_saver
app.dependency_overrides[get_llm] = lambda: make_fake_ai([])
client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_raiz_redirige_a_docs():
    r = client.get("/", follow_redirects=False)
    assert r.status_code in (302, 307)
    assert r.headers["location"] == "/docs"


def test_advice_con_taquicardia_bloquea_consejo():
    saved.clear()
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 112, "steps": 3000})
    assert r.status_code == 200
    body = r.json()
    assert body["user_id"] == "usuario-prueba"
    assert body["rule"]["level"] == "alert"
    assert body["advice_status"] == "blocked"
    assert body["advice"] is None
    assert len(saved) == 1


def test_advice_normal_devuelve_consejo():
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 64, "sleep_hours": 7.5, "steps": 9000})
    assert r.status_code == 200
    body = r.json()
    assert body["rule"]["level"] == "none"
    assert body["advice_status"] == "ok"
    assert body["advice"]["training"]["intensity"] == "moderada"
    assert body["context"]["activity_level"] == "moderado"


def test_valor_irreal_es_rechazado():
    r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 400})
    assert r.status_code == 422


def test_sin_token_responde_401(monkeypatch):
    # Fuerza la autenticacion real aunque el .env local tenga AUTH_DISABLED=true
    monkeypatch.setattr("app.auth.AUTH_DISABLED", False)
    app.dependency_overrides.pop(get_current_user)
    try:
        r = client.post("/advice", json={"day": "2026-11-15", "resting_hr": 70})
        assert r.status_code == 401
    finally:
        app.dependency_overrides[get_current_user] = lambda: "usuario-prueba"
