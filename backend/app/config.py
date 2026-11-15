"""Configuracion del backend, leida desde variables de entorno (.env)."""
import os

from dotenv import load_dotenv

load_dotenv()

FIREBASE_CREDENTIALS = os.getenv("FIREBASE_CREDENTIALS", "serviceAccount.json")
AUTH_DISABLED = os.getenv("AUTH_DISABLED", "false").lower() == "true"
DEV_USER_ID = "dev-user"
