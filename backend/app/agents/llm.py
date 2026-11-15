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
