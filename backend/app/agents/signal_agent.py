"""Agente de Senales: convierte los datos del reloj en contexto.

MVP: usa reglas simples sobre pasos y sueno. Health Connect no entrega las
senales crudas del acelerometro/giroscopio, asi que el clasificador
Random Forest (HAR) queda para cuando exista una app propia en Wear OS.
"""
from app.schemas import BioData, SignalContext


def analyze_context(data: BioData) -> SignalContext:
    if data.steps is None:
        activity = "sin datos"
    elif data.steps < 5_000:
        activity = "sedentario"
    elif data.steps < 10_000:
        activity = "moderado"
    else:
        activity = "activo"

    if data.sleep_hours is None:
        sleep = "sin datos"
    elif data.sleep_hours < 6:
        sleep = "insuficiente"
    elif data.sleep_hours <= 9:
        sleep = "adecuado"
    else:
        sleep = "prolongado"

    recovery = "baja" if sleep == "insuficiente" else "normal"

    return SignalContext(
        at_rest=data.at_rest,
        activity_level=activity,
        sleep_quality=sleep,
        recovery=recovery,
    )
