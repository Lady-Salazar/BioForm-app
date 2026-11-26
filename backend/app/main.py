"""Backend de BioForm AI: API con FastAPI, reglas de salud y agentes de IA."""
from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import RedirectResponse

from app import firebase
from app.agents.llm import get_ai
from app.agents.orchestrator import run_advisor
from app.auth import get_current_user
from app.har import classifier as har
from app.schemas import AdviceResponse, BioData, HarRequest, HarResult

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
    return {"status": "ok", "ai_configured": get_ai() is not None, "har_model": har.is_available()}


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


def get_har():
    """Devuelve el clasificador de actividad o 503 si aun no hay modelo entrenado."""
    if not har.is_available():
        raise HTTPException(status_code=503, detail="Aun no hay un modelo de actividad entrenado.")
    return har


@app.post("/har/classify", response_model=HarResult)
def classify_activity(
    req: HarRequest,
    uid: str = Depends(get_current_user),
    clf=Depends(get_har),
):
    """Reconoce la actividad (reposo, caminar, trotar, saltos, sentadillas) a partir de la senal inercial."""
    if any(len(row) != 6 for row in req.samples):
        raise HTTPException(status_code=422, detail="Cada muestra debe tener 6 valores: ax, ay, az, gx, gy, gz.")
    return clf.classify(req.samples)
