"""Pruebas del motor de reglas de frecuencia cardiaca."""
import pytest

from app.rules.heart_rate import evaluate_resting_hr
from app.schemas import AlertLevel


def test_taquicardia_en_reposo_bloquea_entrenamiento():
    r = evaluate_resting_hr(120, at_rest=True)
    assert r.level == AlertLevel.ALERT
    assert r.condition == "taquicardia"
    assert r.block_training is True


def test_bradicardia_en_reposo_genera_advertencia():
    r = evaluate_resting_hr(45, at_rest=True)
    assert r.level == AlertLevel.WARNING
    assert r.condition == "bradicardia"
    assert r.block_training is False


def test_frecuencia_normal_sin_alerta():
    r = evaluate_resting_hr(70, at_rest=True)
    assert r.level == AlertLevel.NONE
    assert r.block_training is False


@pytest.mark.parametrize("hr", [50, 100])
def test_limites_exactos_no_generan_alerta(hr):
    # Las reglas usan "> 100" y "< 50": 50 y 100 estan dentro del rango.
    assert evaluate_resting_hr(hr).level == AlertLevel.NONE


def test_fc_alta_en_movimiento_no_genera_alerta():
    # Validacion de contexto: 110 lpm subiendo escaleras es normal.
    r = evaluate_resting_hr(110, at_rest=False)
    assert r.level == AlertLevel.NONE
    assert r.block_training is False


def test_mensaje_no_es_diagnostico():
    # Los avisos sugieren consultar, no diagnostican.
    r = evaluate_resting_hr(130)
    assert "consultar a un medico" in r.message
    assert "tienes taquicardia" not in r.message.lower()
