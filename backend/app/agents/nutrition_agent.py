"""Agente Nutricional: herramienta de calculo + Gemini para redactar."""
from app.agents.llm import generate_structured
from app.agents.prompts import NUTRITION_SYSTEM
from app.schemas import BioData, NutritionAdvice, SignalContext


def energy_tool(data: BioData, ctx: SignalContext) -> dict:
    """Herramienta deterministica: resume el gasto del dia y el enfoque sugerido.

    Los numeros los calcula el codigo, no el LLM, para que Gemini no invente cifras.
    """
    focus = {
        "activo": "reponer energia con carbohidratos complejos y proteina",
        "moderado": "comidas equilibradas con proteina, verduras y carbohidratos",
        "sedentario": "comidas ligeras y equilibradas, priorizando verduras y proteina",
        "sin datos": "comidas equilibradas",
    }[ctx.activity_level]
    return {
        "calories_burned": data.calories,
        "activity_level": ctx.activity_level,
        "focus": focus,
    }


def build_nutrition_prompt(energy: dict, data: BioData) -> str:
    return (
        "Datos de hoy del usuario:\n"
        f"- Calorias gastadas segun el reloj: {energy['calories_burned'] if energy['calories_burned'] is not None else 'sin datos'}\n"
        f"- Nivel de actividad: {energy['activity_level']}\n"
        f"- Actividad registrada: {data.activity_type or 'sin datos'}\n"
        f"- Enfoque calculado: {energy['focus']}\n\n"
        "Genera la recomendacion de alimentacion e hidratacion para hoy."
    )


async def nutrition_agent(ai, data: BioData, ctx: SignalContext) -> NutritionAdvice:
    energy = energy_tool(data, ctx)
    return await generate_structured(ai, NUTRITION_SYSTEM, build_nutrition_prompt(energy, data), NutritionAdvice)
