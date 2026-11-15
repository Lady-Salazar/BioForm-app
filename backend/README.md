# Backend · BioForm AI

API en **Python + FastAPI**. Fase 2: verificación del login de Firebase y motor de reglas de frecuencia cardíaca.

## Estructura

```text
backend/
├── app/
│   ├── main.py            # Endpoints: GET /health y POST /advice
│   ├── schemas.py         # Modelos de datos y validación (Pydantic)
│   ├── auth.py            # Verificación del token de Firebase
│   ├── firebase.py        # Conexión con Firebase y guardado en Firestore
│   ├── config.py          # Lectura de variables de entorno (.env)
│   └── rules/
│       └── heart_rate.py  # Motor de reglas de FC (Agente Cardíaco)
├── tests/                 # Pruebas automáticas (pytest)
├── requirements.txt       # Dependencias
└── .env.example           # Plantilla de configuración
```

## Cómo ejecutarlo (Windows / PowerShell)

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
pytest
uvicorn app.main:app --reload
```

Documentación interactiva: http://127.0.0.1:8000/docs

## Reglas de frecuencia cardíaca en reposo

| FC en reposo | Nivel | Acción |
| :--- | :--- | :--- |
| > 100 lpm | `alert` (posible taquicardia) | No entrenar; sugerir consultar a un médico |
| < 50 lpm | `warning` (posible bradicardia) | Advertencia de salud |
| 50–100 lpm | `none` | Sin alerta |
| Medida en movimiento | `none` | No se evalúa |
