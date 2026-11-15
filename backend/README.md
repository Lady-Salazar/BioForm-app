# Backend · BioForm AI

API en **Python + FastAPI** con reglas de salud y agentes de IA (**Genkit + Gemini 3.1 Flash-Lite**).

## Estructura

```text
backend/
├── app/
│   ├── main.py               # Endpoints: GET /health y POST /advice
│   ├── schemas.py            # Modelos de datos y validación (Pydantic)
│   ├── auth.py               # Verificación del token de Firebase
│   ├── firebase.py           # Conexión con Firebase y guardado en Firestore
│   ├── config.py             # Variables de entorno (.env)
│   ├── rules/
│   │   └── heart_rate.py     # Reglas de FC en reposo (Agente Cardíaco)
│   └── agents/
│       ├── orchestrator.py   # Agente Orquestador: decide qué agentes llamar
│       ├── signal_agent.py   # Agente de Señales: actividad, sueño, recuperación
│       ├── coach_agent.py    # Agente Coach: consejo de entrenamiento (Gemini)
│       ├── nutrition_agent.py# Agente Nutricional: herramienta + Gemini
│       ├── prompts.py        # Instrucciones y guardrails de los agentes
│       └── llm.py            # Conexión con Gemini a través de Genkit
├── tests/                    # Pruebas automáticas (pytest, sin llamar a Gemini)
├── requirements.txt
└── .env.example
```

## Flujo de POST /advice

1. **Agente de Señales:** calcula el contexto (actividad, sueño, recuperación).
2. **Agente Cardíaco:** aplica las reglas de FC en reposo, **sin LLM**.
3. Si hay alerta de taquicardia, se detiene: **no se llama a Gemini** (`advice_status: "blocked"`).
4. **Agentes Coach y Nutricional** en paralelo, con Gemini vía Genkit (`advice_status: "ok"`).
5. Si no hay clave de Gemini o la llamada falla, la API responde igual con las reglas (`advice_status: "unavailable"`).

Gemini solo recibe datos numéricos y de contexto; nunca el nombre, el correo ni el UID.

## Cómo ejecutarlo (Windows / PowerShell)

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
pytest
uvicorn app.main:app --reload
```

Documentación interactiva: http://127.0.0.1:8000/docs

## Reglas de frecuencia cardíaca en reposo

| FC en reposo | Nivel | Acción |
| :--- | :--- | :--- |
| > 100 lpm | `alert` (posible taquicardia) | No entrenar; no se genera consejo de IA |
| < 50 lpm | `warning` (posible bradicardia) | Advertencia; consejo con intensidad máxima moderada |
| 50–100 lpm | `none` | Consejo normal |
| Medida en movimiento | `none` | No se evalúa |
