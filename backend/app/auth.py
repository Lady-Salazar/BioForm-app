"""Dependencia de FastAPI que identifica al usuario de cada peticion.

La app envia el token de Firebase en la cabecera:
    Authorization: Bearer <token>
"""
from fastapi import Header, HTTPException, status

from app import firebase
from app.config import AUTH_DISABLED, DEV_USER_ID


def get_current_user(authorization: str | None = Header(default=None)) -> str:
    """Devuelve el UID del usuario o responde 401 si el token no es valido."""
    if AUTH_DISABLED:
        return DEV_USER_ID

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Falta el token de autenticacion (Authorization: Bearer <token>).",
        )

    token = authorization.removeprefix("Bearer ").strip()
    try:
        return firebase.verify_token(token)
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token invalido o expirado.",
        )
