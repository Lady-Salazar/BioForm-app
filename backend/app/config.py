"""Configuracion del backend, leida desde variables de entorno (.env)."""
import os

from dotenv import load_dotenv

load_dotenv()

FIREBASE_CREDENTIALS = os.getenv("FIREBASE_CREDENTIALS", "serviceAccount.json")
AUTH_DISABLED = os.getenv("AUTH_DISABLED", "false").lower() == "true"
DEV_USER_ID = "dev-user"

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.1-flash-lite").strip()
