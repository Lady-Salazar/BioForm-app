"""Modelos de datos: que recibe y que devuelve la API.

Pydantic valida automaticamente cada peticion: si un valor esta fuera
de rango (por ejemplo, una FC de 400 lpm), FastAPI responde con error 422
antes de llegar a las reglas. Esto cubre el "filtrado de valores irreales"
del pipeline de datos.
"""
from datetime import date
from enum import Enum

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
    """Resultado del motor de reglas de frecuencia cardiaca."""

    level: AlertLevel
    condition: str | None = None   # "taquicardia" | "bradicardia" | None
    block_training: bool
    message: str


class AdviceResponse(BaseModel):
    """Respuesta del endpoint /advice."""

    user_id: str
    day: date
    rule: RuleResult
    advice: str | None = Field(
        default=None,
        description="Consejo de entrenamiento y nutricion (lo generara Gemini en la Fase 3)",
    )
