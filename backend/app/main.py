"""Backend de BioForm AI: API con FastAPI, reglas de salud y agentes de IA."""
from fastapi import Depends, FastAPI
from fastapi.responses import RedirectResponse

from app import firebase
from app.agents.llm import get_ai
from app.agents.orchestrator import run_advisor
from app.auth import get_current_user
from app.schemas import AdviceResponse, BioData

app = FastAPI(
    title="BioForm AI API",
    description="Backend del asesor fitness digital: reglas de salud y agentes de IA (Genkit + Gemini).",
    version="0.3.0",
)


def get_saver():
    """Devuelve la funcion que guarda en Firestore (se reemplaza en las pruebas)."""
    return firebase.save_result


def get_llm():
    """Devuelve Genkit configurado con Gemini, o None (se reemplaza en las pruebas)."""
    return get_ai()


@app.get("/", include_in_schema=False)
def root():
    """Redirige la direccion principal a la documentacion interactiva."""
    return RedirectResponse(url="/docs")


@app.get("/health")
def health():
    """Comprueba que el servidor esta funcionando y si la IA esta configurada."""
    return {"status": "ok", "ai_configured": get_ai() is not None}


@app.post("/advice", response_model=AdviceResponse)
async def request_advice(
    data: BioData,
    uid: str = Depends(get_current_user),
    save=Depends(get_saver),
    ai=Depends(get_llm),
):
    """Recibe el resumen diario y devuelve las alertas y el consejo personalizado."""
    rule, ctx, status, advice = await run_advisor(data, ai)
    save(uid, data, rule, status, advice)
    return AdviceResponse(
        user_id=uid, day=data.day, rule=rule, context=ctx, advice_status=status, advice=advice
    )
