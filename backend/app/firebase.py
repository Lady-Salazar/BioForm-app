"""Conexion con Firebase (Admin SDK): verificacion de tokens y Firestore."""
import firebase_admin
from firebase_admin import auth, credentials, firestore

from app.config import FIREBASE_CREDENTIALS
from app.schemas import AdviceContent, BioData, RuleResult

_app = None


def init_firebase():
    """Inicializa Firebase una sola vez con la clave de la cuenta de servicio."""
    global _app
    if _app is None:
        cred = credentials.Certificate(FIREBASE_CREDENTIALS)
        _app = firebase_admin.initialize_app(cred)
    return _app


def verify_token(id_token: str) -> str:
    """Verifica el token de Firebase Auth y devuelve el UID del usuario."""
    init_firebase()
    decoded = auth.verify_id_token(id_token)
    return decoded["uid"]


def save_result(
    uid: str,
    data: BioData,
    rule: RuleResult,
    advice_status: str,
    advice: AdviceContent | None,
) -> None:
    """Guarda la metrica diaria y el consejo/alerta en Firestore.

    users/{uid}/metrics/{fecha}  -> resumen diario
    users/{uid}/advice/{id}      -> consejo o alerta generada
    """
    init_firebase()
    db = firestore.client()
    user_ref = db.collection("users").document(uid)

    metrics = data.model_dump(mode="json", exclude={"day"})
    metrics["synced_at"] = firestore.SERVER_TIMESTAMP
    user_ref.collection("metrics").document(data.day.isoformat()).set(metrics)

    user_ref.collection("advice").add(
        {
            "type": "alert" if rule.level.value != "none" else "advice",
            "day": data.day.isoformat(),
            "alert_level": rule.level.value,
            "condition": rule.condition,
            "message": rule.message,
            "advice_status": advice_status,
            "advice": advice.model_dump(mode="json") if advice else None,
            "created_at": firestore.SERVER_TIMESTAMP,
        }
    )
