"""Agente Coach: Gemini redacta el consejo de entrenamiento."""
from app.agents.prompts import COACH_SYSTEM
from app.schemas import BioData, RuleResult, SignalContext, TrainingAdvice


def build_coach_prompt(data: BioData, ctx: SignalContext, rule: RuleResult) -> str:
    # Solo datos numericos y de contexto: nada que identifique al usuario.
    return (
        "Datos de hoy del usuario:\n"
        f"- Frecuencia cardiaca en reposo: {data.resting_hr} lpm\n"
        f"- Nivel de actividad: {ctx.activity_level} (pasos: {data.steps if data.steps is not None else 'sin datos'})\n"
        f"- Sueno: {ctx.sleep_quality} ({data.sleep_hours if data.sleep_hours is not None else 'sin datos'} horas)\n"
        f"- Recuperacion estimada: {ctx.recovery}\n"
        f"- Actividad registrada: {data.activity_type or 'sin datos'}\n"
        f"- Resultado de las reglas de salud: {rule.level.value}"
        f"{' (' + rule.condition + ')' if rule.condition else ''}\n\n"
        "Genera el consejo de entrenamiento para hoy."
    )


async def coach_agent(ai, data: BioData, ctx: SignalContext, rule: RuleResult) -> TrainingAdvice:
    response = await ai.generate(
        system=COACH_SYSTEM,
        prompt=build_coach_prompt(data, ctx, rule),
        output_schema=TrainingAdvice,
    )
    if response.output is None:
        raise ValueError("Gemini no devolvio un consejo de entrenamiento valido")
    return response.output
