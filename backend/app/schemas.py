"""Modelos de datos: que recibe y que devuelve la API.

Pydantic valida automaticamente cada peticion: si un valor esta fuera
de rango (por ejemplo, una FC de 400 lpm), FastAPI responde con error 422
antes de llegar a las reglas. Esto cubre el "filtrado de valores irreales"
del pipeline de datos.
"""
from datetime import date
from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class AlertLevel(str, Enum):
    NONE = "none"          # todo en rango
    WARNING = "warning"    # advertencia (bradicardia)
    ALERT = "alert"        # alerta (taquicardia): no entrenar


class BioData(BaseModel):
    """Resumen diario que envia la app desde Health Connect."""

    day: date = Field(description="Fecha del resumen (AAAA-MM-DD)")
    resting_hr: int = Field(ge=25, le=220, description="FC en reposo (lpm)")
    at_rest: bool = Field(
        default=True,
        description="True si la FC se midio en reposo real (validado con el movimiento)",
    )
    steps: int | None = Field(default=None, ge=0, le=100_000)
    sleep_hours: float | None = Field(default=None, ge=0, le=24)
    calories: int | None = Field(default=None, ge=0, le=15_000)
    activity_type: str | None = Field(default=None, max_length=50)


class RuleResult(BaseModel):
    """Resultado del motor de reglas de frecuencia cardiaca (Agente Cardiaco)."""

    level: AlertLevel
    condition: str | None = None   # "taquicardia" | "bradicardia" | None
    block_training: bool
    message: str


class SignalContext(BaseModel):
    """Contexto calculado por el Agente de Senales."""

    at_rest: bool
    activity_level: Literal["sin datos", "sedentario", "moderado", "activo"]
    sleep_quality: Literal["sin datos", "insuficiente", "adecuado", "prolongado"]
    recovery: Literal["baja", "normal"]


class TrainingAdvice(BaseModel):
    """Salida estructurada del Agente Coach (la redacta Gemini)."""

    intensity: Literal["descanso", "suave", "moderada", "alta"] = Field(
        description="Intensidad recomendada para hoy"
    )
    pre_workout: str = Field(description="Consejo antes de entrenar (1-2 frases)")
    during_workout: str = Field(description="Consejo durante el entrenamiento (1-2 frases)")
    post_workout: str = Field(description="Consejo despues de entrenar (1-2 frases)")


class NutritionAdvice(BaseModel):
    """Salida estructurada del Agente Nutricional (la redacta Gemini)."""

    summary: str = Field(description="Resumen de alimentacion para hoy (1-2 frases)")
    suggestions: list[str] = Field(description="Entre 2 y 3 sugerencias concretas de comidas")
    hydration: str = Field(description="Recomendacion de hidratacion (1 frase)")


class AdviceContent(BaseModel):
    """Consejo final que une la salida de los agentes Coach y Nutricional."""

    training: TrainingAdvice
    nutrition: NutritionAdvice


class AdviceResponse(BaseModel):
    """Respuesta del endpoint /advice."""

    user_id: str
    day: date
    rule: RuleResult
    context: SignalContext
    advice_status: Literal["ok", "blocked", "unavailable"] = Field(
        description=(
            "ok: consejo generado; blocked: la alerta cardiaca bloquea el consejo de "
            "entrenamiento; unavailable: la IA no esta configurada o fallo"
        )
    )
    advice: AdviceContent | None = None
