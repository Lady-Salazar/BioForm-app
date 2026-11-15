"""Modelo falso de Genkit para probar los agentes sin llamar a Gemini."""
import json

from genkit import Genkit, Message, ModelResponse, Part

COACH_JSON = {
    "intensity": "moderada",
    "pre_workout": "Calienta 10 minutos.",
    "during_workout": "Mantente hidratado.",
    "post_workout": "Estira 5 minutos.",
}
NUTRITION_JSON = {
    "summary": "Comidas equilibradas hoy.",
    "suggestions": ["Arroz con pollo y ensalada", "Yogur con fruta"],
    "hydration": "Bebe agua durante el dia.",
}


def make_fake_ai(calls: list, fail: bool = False) -> Genkit:
    """Crea un Genkit cuyo modelo responde JSON segun el agente que lo llama."""
    ai = Genkit(model="test/fake")

    async def fake_model(request):
        text = str(request)
        calls.append(text)
        if fail:
            raise RuntimeError("Gemini no disponible")
        payload = NUTRITION_JSON if "Agente Nutricional" in text else COACH_JSON
        return ModelResponse(message=Message(role="model", content=[Part(text=json.dumps(payload))]))

    ai.define_model("test/fake", fake_model)
    return ai
