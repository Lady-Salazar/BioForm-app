"""Backend de BioForm AI (Fase 2): API con FastAPI y motor de reglas de FC."""
from fastapi import Depends, FastAPI

from app import firebase
from app.auth import get_current_user
from app.rules.heart_rate import evaluate_resting_hr
from app.schemas import AdviceResponse, BioData

app = FastAPI(
    title="BioForm AI API",
    description="Backend del asesor fitness digital: reglas de salud y (Fase 3) agentes de IA.",
    version="0.2.0",
)


def get_saver():
    """Devuelve la funcion que guarda en Firestore (se reemplaza en las pruebas)."""
    return firebase.save_result


@app.get("/health")
def health():
    """Comprueba que el servidor esta funcionando."""
    return {"status": "ok"}


@app.post("/advice", response_model=AdviceResponse)
def request_advice(
    data: BioData,
    uid: str = Depends(get_current_user),
    save=Depends(get_saver),
):
    """Recibe el resumen diario, aplica las reglas de FC y guarda el resultado.

    En la Fase 3, si no hay alerta, aqui se llamara a los agentes de Genkit
    para que Gemini redacte el consejo personalizado.
    """
    rule = evaluate_resting_hr(data.resting_hr, data.at_rest)

    advice = None  # Fase 3: consejo generado por Gemini (solo si no hay bloqueo)

    save(uid, data, rule, advice)
    return AdviceResponse(user_id=uid, day=data.day, rule=rule, advice=advice)
