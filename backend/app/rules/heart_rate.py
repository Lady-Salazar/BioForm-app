"""Motor de reglas de frecuencia cardiaca en reposo (Agente Cardiaco).

Reglas del MVP:
  - FC en reposo > 100 lpm  -> ALERTA (posible taquicardia): no entrenar.
  - FC en reposo < 50 lpm   -> ADVERTENCIA (posible bradicardia).
  - Entre 50 y 100 lpm      -> sin alerta.

Las reglas solo se aplican si la medicion fue en reposo real. Una FC de
110 lpm subiendo una escalera es normal, por eso no genera alerta.

Los mensajes NO son diagnosticos: son avisos de bienestar que sugieren
consultar a un profesional (requisito de los terminos de Gemini y de la
ley de proteccion de datos de salud).
"""
from app.schemas import AlertLevel, RuleResult

TACHYCARDIA_THRESHOLD = 100   # lpm
BRADYCARDIA_THRESHOLD = 50    # lpm


def evaluate_resting_hr(resting_hr: int, at_rest: bool = True) -> RuleResult:
    """Evalua la FC en reposo y devuelve el nivel de alerta."""
    if not at_rest:
        return RuleResult(
            level=AlertLevel.NONE,
            block_training=False,
            message=(
                "La frecuencia cardiaca no se midio en reposo, "
                "por eso no se evalua para alertas."
            ),
        )

    if resting_hr > TACHYCARDIA_THRESHOLD:
        return RuleResult(
            level=AlertLevel.ALERT,
            condition="taquicardia",
            block_training=True,
            message=(
                f"Tu frecuencia en reposo hoy es de {resting_hr} lpm, mas alta de lo "
                "habitual. Te sugerimos no entrenar hoy y, si se repite o te sientes "
                "mal, consultar a un medico."
            ),
        )

    if resting_hr < BRADYCARDIA_THRESHOLD:
        return RuleResult(
            level=AlertLevel.WARNING,
            condition="bradicardia",
            block_training=False,
            message=(
                f"Tu frecuencia en reposo hoy es de {resting_hr} lpm, mas baja de lo "
                "habitual. En deportistas puede ser normal, pero si tienes mareos o "
                "cansancio, consulta a un medico."
            ),
        )

    return RuleResult(
        level=AlertLevel.NONE,
        block_training=False,
        message=f"Tu frecuencia en reposo ({resting_hr} lpm) esta en un rango habitual.",
    )
