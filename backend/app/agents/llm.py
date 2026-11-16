"""Conexion con Gemini a traves de Genkit.

Genkit se crea una sola vez y solo si hay GEMINI_API_KEY. Si no hay clave,
get_ai() devuelve None y el backend sigue funcionando con las reglas de salud.
"""
from app.config import GEMINI_API_KEY, GEMINI_MODEL

_ai = None


def get_ai():
    """Devuelve la instancia de Genkit configurada con Gemini (o None)."""
    global _ai
    if _ai is None and GEMINI_API_KEY:
        from genkit import Genkit
        from genkit_google_genai import GoogleAI

        _ai = Genkit(
            plugins=[GoogleAI(api_key=GEMINI_API_KEY)],
            model=GoogleAI.gemini(GEMINI_MODEL),
        )
    return _ai


import logging

logger = logging.getLogger("bioform.llm")

RETRY_PROMPT = (
    "Tu respuesta anterior no cumplio el formato pedido. "
    "Responde de nuevo SOLO con el objeto JSON que sigue el esquema, sin texto adicional."
)


async def generate_structured(ai, system: str, prompt: str, schema):
    """Pide a Gemini una respuesta con formato fijo; si no cumple, reintenta una vez."""
    response = await ai.generate(system=system, prompt=prompt, output_schema=schema)
    if response.output is not None:
        return response.output

    logger.warning(
        "Gemini no cumplio el esquema %s (finish_reason=%s). Texto: %s",
        schema.__name__, response.finish_reason, (response.text or "")[:500],
    )
    retry = await ai.generate(messages=response.messages, prompt=RETRY_PROMPT, output_schema=schema)
    if retry.output is not None:
        return retry.output

    logger.error(
        "Reintento fallido para %s (finish_reason=%s). Texto: %s",
        schema.__name__, retry.finish_reason, (retry.text or "")[:500],
    )
    raise ValueError(f"Gemini no devolvio una respuesta valida para {schema.__name__}")
