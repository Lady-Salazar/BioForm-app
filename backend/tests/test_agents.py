"""Pruebas de los agentes y del orquestador con un modelo falso."""
import asyncio
from datetime import date

from app.agents.orchestrator import run_advisor
from app.agents.signal_agent import analyze_context
from app.schemas import BioData
from tests.fake_llm import make_fake_ai


def bio(**kw):
    base = {"day": date(2026, 11, 15), "resting_hr": 64, "steps": 8000, "sleep_hours": 7.5, "calories": 2300}
    base.update(kw)
    return BioData(**base)


def test_agente_senales_clasifica_actividad_y_sueno():
    ctx = analyze_context(bio(steps=12000, sleep_hours=5))
    assert ctx.activity_level == "activo"
    assert ctx.sleep_quality == "insuficiente"
    assert ctx.recovery == "baja"


def test_consejo_completo_con_gemini():
    calls = []
    rule, ctx, status, advice = asyncio.run(run_advisor(bio(), make_fake_ai(calls)))
    assert status == "ok"
    assert advice.training.intensity == "moderada"
    assert len(advice.nutrition.suggestions) == 2
    assert len(calls) == 2  # Coach + Nutricional


def test_alerta_cardiaca_no_llama_a_gemini():
    calls = []
    rule, ctx, status, advice = asyncio.run(run_advisor(bio(resting_hr=118), make_fake_ai(calls)))
    assert status == "blocked"
    assert advice is None
    assert calls == []  # guardrail: el LLM nunca se ejecuta si hay riesgo


def test_sin_clave_de_gemini_funciona_solo_con_reglas():
    rule, ctx, status, advice = asyncio.run(run_advisor(bio(), None))
    assert status == "unavailable"
    assert advice is None


def test_si_gemini_falla_la_api_no_se_cae():
    rule, ctx, status, advice = asyncio.run(run_advisor(bio(), make_fake_ai([], fail=True)))
    assert status == "unavailable"


def test_prompt_no_incluye_datos_personales():
    calls = []
    asyncio.run(run_advisor(bio(), make_fake_ai(calls)))
    joined = " ".join(calls)
    assert "dev-user" not in joined and "@" not in joined


def test_reintenta_si_gemini_no_cumple_el_formato():
    """Primera respuesta invalida (texto libre), segunda valida: el consejo se genera igual."""
    import json
    from genkit import Genkit, Message, ModelResponse, Part
    from tests.fake_llm import COACH_JSON, NUTRITION_JSON

    ai = Genkit(model="test/flaky")
    calls = {"coach": 0}

    async def flaky(request):
        text = str(request)
        if "Agente Nutricional" in text:
            payload = json.dumps(NUTRITION_JSON)
        else:
            calls["coach"] += 1
            payload = "Hoy entrena suave." if calls["coach"] == 1 else json.dumps(COACH_JSON)
        return ModelResponse(message=Message(role="model", content=[Part(text=payload)]))

    ai.define_model("test/flaky", flaky)
    rule, ctx, status, advice = asyncio.run(run_advisor(bio(), ai))
    assert status == "ok"
    assert calls["coach"] == 2


def test_intensidad_inesperada_se_normaliza():
    from app.agents.coach_agent import normalize_intensity
    ctx_baja = analyze_context(bio(sleep_hours=4))
    ctx_normal = analyze_context(bio())
    assert normalize_intensity("Alta", ctx_normal) == "alta"
    assert normalize_intensity("ligera", ctx_baja) == "suave"
    assert normalize_intensity("intensa", ctx_normal) == "moderada"
