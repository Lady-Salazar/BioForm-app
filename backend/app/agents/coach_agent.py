"""Agente Coach: Gemini redacta el consejo de entrenamiento."""
from app.agents.llm import generate_structured
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


VALID_INTENSITIES = {"descanso", "suave", "moderada", "alta"}


def normalize_intensity(value: str, ctx: SignalContext) -> str:
    """Asegura que la intensidad sea uno de los 4 valores permitidos."""
    v = (value or "").strip().lower()
    if v in VALID_INTENSITIES:
        return v
    # Valor inesperado: se elige uno seguro segun la recuperacion
    return "suave" if ctx.recovery == "baja" else "moderada"


async def coach_agent(ai, data: BioData, ctx: SignalContext, rule: RuleResult) -> TrainingAdvice:
    advice = await generate_structured(ai, COACH_SYSTEM, build_coach_prompt(data, ctx, rule), TrainingAdvice)
    advice.intensity = normalize_intensity(advice.intensity, ctx)
    return advice
