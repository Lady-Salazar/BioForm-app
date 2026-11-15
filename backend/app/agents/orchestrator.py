"""Agente Orquestador: decide que agentes llamar y une sus respuestas.

Orden fijo del MVP:
  1. Agente de Senales   -> contexto (reposo, actividad, sueno, recuperacion)
  2. Agente Cardiaco     -> reglas fijas de FC (sin LLM)
  3. Si la alerta bloquea el entrenamiento, se detiene aqui: NO se llama a Gemini.
  4. Agentes Coach y Nutricional en paralelo (Gemini via Genkit).
"""
import asyncio
import logging

from app.agents.coach_agent import coach_agent
from app.agents.nutrition_agent import nutrition_agent
from app.agents.signal_agent import analyze_context
from app.rules.heart_rate import evaluate_resting_hr
from app.schemas import AdviceContent, BioData, RuleResult, SignalContext

logger = logging.getLogger("bioform.orchestrator")


async def run_advisor(data: BioData, ai) -> tuple[RuleResult, SignalContext, str, AdviceContent | None]:
    ctx = analyze_context(data)
    rule = evaluate_resting_hr(data.resting_hr, ctx.at_rest)

    if rule.block_training:
        return rule, ctx, "blocked", None

    if ai is None:
        return rule, ctx, "unavailable", None

    try:
        training, nutrition = await asyncio.gather(
            coach_agent(ai, data, ctx, rule),
            nutrition_agent(ai, data, ctx),
        )
    except Exception:
        logger.exception("Fallo la generacion del consejo con Gemini")
        return rule, ctx, "unavailable", None

    return rule, ctx, "ok", AdviceContent(training=training, nutrition=nutrition)
