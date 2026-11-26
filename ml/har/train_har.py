"""Entrenamiento del modelo de reconocimiento de actividad (HAR) de BioForm AI.

Uso (desde la carpeta Bioform-app, con el entorno virtual del backend activo):
    python ml/har/train_har.py

1. Descomprime los .zip de ml/har/data/zips/ (los que envia el boton "Compartir grabaciones")
   en ml/har/data/raw/, sin duplicar archivos que ya existan.
2. Revisa la calidad de cada grabacion (posicion del telefono) y descarta las incorrectas.
3. Lee los CSV de ml/har/data/raw/ (grabados con la pantalla "Recolectar datos" de la app),
entrena un Random Forest, lo evalua con validacion INDEPENDIENTE DEL SUJETO (GroupKFold)
y guarda el modelo en backend/app/models/har_rf.joblib para que lo use el backend.

Nombre de cada archivo: <sujeto>_<actividad>_<posicion>_<fecha>_<hora>_<telefono>.csv
    ej.: S01_caminar_bolsillo_20261126_101500_SM-A536E.csv
Columnas: t_ms, ax, ay, az, gx, gy, gz
"""
import argparse
import json
import sys
import zipfile
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.model_selection import GroupKFold, StratifiedKFold

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))  # para usar las MISMAS caracteristicas que el backend
from app.har.features import (  # noqa: E402
    ACTIVITY_CATEGORY, CHANNELS, FEATURE_NAMES, RATE_HZ, WINDOW, features_from_signal,
)

TRIM_S = 3  # se descartan los 3 s iniciales y finales de cada grabacion (guardar/sacar el telefono)


def parse_name(path: Path) -> dict | None:
    parts = path.stem.split("_")
    if len(parts) < 3 or parts[1] not in ACTIVITY_CATEGORY:
        return None
    device = "_".join(parts[5:]) if len(parts) > 5 else "desconocido"
    return {"subject": parts[0], "activity": parts[1], "position": parts[2], "device": device}


def import_zips(zips_dir: Path, raw_dir: Path) -> None:
    """Descomprime cada .zip en raw/<nombre del zip>/ sin repetir grabaciones ya importadas."""
    if not zips_dir.exists():
        return
    raw_dir.mkdir(parents=True, exist_ok=True)
    existing = {p.name for p in raw_dir.rglob("*.csv")}
    for zpath in sorted(zips_dir.glob("*.zip")):
        added = 0
        with zipfile.ZipFile(zpath) as z:
            for member in z.namelist():
                name = Path(member).name
                if not name.endswith(".csv") or name in existing:
                    continue
                target = raw_dir / zpath.stem / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(member))
                existing.add(name)
                added += 1
        print(f"  {zpath.name}: {added} grabaciones nuevas")


def read_exclusions(path: Path) -> set[str]:
    """Nombres de archivo que se excluyen a mano (uno por linea; # = comentario)."""
    if not path.exists():
        return set()
    lines = path.read_text(encoding="utf-8").splitlines()
    return {ln.strip() for ln in lines if ln.strip() and not ln.strip().startswith("#")}


def check_position(activity: str, signal: np.ndarray) -> str | None:
    """Revisa la posicion del telefono segun hacia donde apunta la gravedad.

    Protocolo: bolsillo delantero derecho, vertical, camara hacia ABAJO, pantalla hacia la pierna.
    - De pie: ay cerca de -9.8.   - Sentado: az negativo (pantalla hacia el muslo, mirando al piso).
    Devuelve el motivo si la grabacion no cumple el protocolo, o None si esta bien.
    """
    ay = float(signal[:, 1].mean())
    az = float(signal[:, 2].mean())
    if activity in ("caminar", "trotar", "saltos") and abs(az) > 6:
        return f"telefono horizontal, probablemente en la mano (az={az:.1f})"
    if az > 5:
        return f"pantalla hacia afuera o telefono boca arriba (az={az:.1f})"
    if ay > 2:
        return f"camara hacia arriba (ay={ay:.1f})"
    return None


def load_dataset(data_dir: Path, position: str | None, excluded: set[str] | None = None, check: bool = True):
    X, y, groups, used, rejected = [], [], [], [], []
    excluded = excluded or set()
    seen: set[str] = set()
    for path in sorted(data_dir.rglob("*.csv")):
        if path.name in seen:
            continue  # misma grabacion en dos carpetas
        seen.add(path.name)
        if path.name in excluded:
            rejected.append({"file": path.name, "reason": "excluida a mano (excluir.txt)"})
            continue
        meta = parse_name(path)
        if meta is None:
            print(f"  [omitido] nombre no valido: {path.name}")
            continue
        if position and meta["position"] != position:
            continue
        df = pd.read_csv(path)
        signal = df[CHANNELS].to_numpy(dtype=float)
        trim = TRIM_S * RATE_HZ
        signal = signal[trim:len(signal) - trim]
        if len(signal) < WINDOW:
            print(f"  [omitido] muy corto: {path.name}")
            continue
        reason = check_position(meta["activity"], signal) if check else None
        if reason:
            rejected.append({"file": path.name, "reason": reason})
            print(f"  [descartado] {path.name}: {reason}")
            continue
        feats = features_from_signal(signal)
        X.append(feats)
        y += [meta["activity"]] * len(feats)
        groups += [meta["subject"]] * len(feats)
        used.append({**meta, "file": path.name, "seconds": round(len(df) / RATE_HZ, 1), "windows": len(feats)})
    if not X:
        raise SystemExit(f"No hay datos validos en {data_dir}")
    return np.vstack(X), np.array(y), np.array(groups, dtype=object), used, rejected


def make_model() -> RandomForestClassifier:
    return RandomForestClassifier(
        n_estimators=200, class_weight="balanced", random_state=42, n_jobs=-1,
    )


def evaluate(X, y, groups):
    """Validacion independiente del sujeto: se entrena con unos participantes y se prueba con otro.

    Si un participante tiene actividades que el resto no grabo, esas ventanas no se pueden evaluar
    (el modelo de ese pliegue no las conoce) y se informan como no evaluadas.
    """
    subjects = np.unique(groups)
    if len(subjects) >= 2:
        cv = GroupKFold(n_splits=min(5, len(subjects)))
        splits = list(cv.split(X, y, groups))
        mode = f"independiente del sujeto (GroupKFold, {len(subjects)} sujetos)"
    else:
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
        splits = list(cv.split(X, y))
        mode = "POR VENTANAS con 1 solo sujeto (optimista: graba al menos 2 personas)"
    y_true, y_pred, skipped = [], [], 0
    for train, test in splits:
        known = set(y[train])
        test_ok = np.array([i for i in test if y[i] in known], dtype=int)
        skipped += len(test) - len(test_ok)
        if len(test_ok) == 0:
            continue
        model = make_model().fit(X[train], y[train])
        y_true += list(y[test_ok])
        y_pred += list(model.predict(X[test_ok]))
    if not y_true:
        raise SystemExit("No se pudo evaluar: ningun participante comparte actividades con otro.")
    labels = sorted(set(y_true) | set(y_pred))
    total = len(y)
    return {
        "mode": mode,
        "evaluated_windows": len(y_true),
        "skipped_windows": skipped,
        "coverage": round(len(y_true) / total, 3),
        "accuracy": accuracy_score(y_true, y_pred),
        # F1 promedio solo de las actividades que se pudieron evaluar
        "f1_macro": f1_score(y_true, y_pred, labels=sorted(set(y_true)), average="macro"),
        "labels": labels,
        "confusion": confusion_matrix(y_true, y_pred, labels=labels).tolist(),
        "report": classification_report(y_true, y_pred, labels=labels, zero_division=0),
    }


def main():
    ap = argparse.ArgumentParser(description="Entrena el modelo HAR de BioForm AI")
    ap.add_argument("--data", default=str(ROOT / "ml" / "har" / "data" / "raw"))
    ap.add_argument("--out", default=str(ROOT / "backend" / "app" / "models" / "har_rf.joblib"))
    ap.add_argument("--reports", default=str(ROOT / "ml" / "har" / "reports"))
    ap.add_argument("--zips", default=str(ROOT / "ml" / "har" / "data" / "zips"),
                    help="carpeta con los .zip enviados desde la app")
    ap.add_argument("--exclude", default=str(ROOT / "ml" / "har" / "data" / "excluir.txt"),
                    help="archivo con nombres de grabaciones a excluir")
    ap.add_argument("--no-check", action="store_true", help="no revisar la posicion del telefono")
    ap.add_argument("--position", default=None, help="usar solo una posicion, ej. bolsillo o muneca")
    args = ap.parse_args()

    print(f"Importando .zip de {args.zips}")
    import_zips(Path(args.zips), Path(args.data))
    print(f"Leyendo datos de {args.data}")
    X, y, groups, used, rejected = load_dataset(
        Path(args.data), args.position, read_exclusions(Path(args.exclude)), check=not args.no_check,
    )
    print(f"  descartadas: {len(rejected)}")
    counts = {str(k): int(v) for k, v in pd.Series(y).value_counts().items()}
    print(f"  {len(used)} grabaciones, {len(X)} ventanas, sujetos: {sorted(set(map(str, groups)))}")
    print(f"  ventanas por actividad: {counts}")
    print(f"  telefonos: {sorted({u['device'] for u in used})}")

    print("Evaluando...")
    ev = evaluate(X, y, groups)
    print(f"  Validacion: {ev['mode']}")
    print(f"  Ventanas evaluadas: {ev['evaluated_windows']} ({ev['coverage'] * 100:.0f} %); "
          f"sin evaluar: {ev['skipped_windows']} (actividades que el otro sujeto no grabo)")
    print(f"  Exactitud: {ev['accuracy']:.3f}   F1 macro: {ev['f1_macro']:.3f}")
    print(ev["report"])

    print("Entrenando el modelo final con todos los datos...")
    model = make_model().fit(X, y)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    trained_at = datetime.now().isoformat(timespec="seconds")
    joblib.dump({
        "model": model,
        "feature_names": FEATURE_NAMES,
        "window": WINDOW,
        "rate_hz": RATE_HZ,
        "labels": sorted(set(y)),
        "trained_at": trained_at,
        "subjects": sorted(set(map(str, groups))),
        "position": args.position or "todas",
    }, out)
    print(f"Modelo guardado en {out}")

    rep = Path(args.reports)
    rep.mkdir(parents=True, exist_ok=True)
    top = sorted(zip(FEATURE_NAMES, model.feature_importances_), key=lambda t: -t[1])[:10]
    (rep / "metrics.json").write_text(json.dumps({
        "trained_at": trained_at, "validation": ev["mode"],
        "accuracy": round(ev["accuracy"], 4), "f1_macro": round(ev["f1_macro"], 4),
        "evaluated_windows": ev["evaluated_windows"], "skipped_windows": ev["skipped_windows"],
        "labels": ev["labels"], "confusion_matrix": ev["confusion"],
        "windows_per_activity": counts, "recordings": used, "rejected": rejected,
        "top_features": [{"feature": f, "importance": round(float(i), 4)} for f, i in top],
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    cm = pd.DataFrame(ev["confusion"], index=ev["labels"], columns=ev["labels"])
    (rep / "report.md").write_text(
        f"# Reporte del modelo HAR\n\nEntrenado: {trained_at}\n\n"
        f"- Validacion: {ev['mode']}\n"
        f"- Ventanas evaluadas: {ev['evaluated_windows']} de {len(X)} ({ev['coverage'] * 100:.0f} %)\n- Exactitud: {ev['accuracy']:.3f}\n- F1 macro: {ev['f1_macro']:.3f}\n"
        f"- Grabaciones: {len(used)} · Ventanas: {len(X)} · Sujetos: {', '.join(sorted(set(map(str, groups))))}\n"
        f"- Telefonos: {', '.join(sorted({u['device'] for u in used}))}\n\n"
        f"## Matriz de confusion (filas = real, columnas = predicho)\n\n{cm.to_markdown()}\n\n"
        f"## Reporte por clase\n\n```\n{ev['report']}```\n\n"
        "## Grabaciones descartadas\n\n"
        + ("\n".join(f"- {r['file']}: {r['reason']}" for r in rejected) or "Ninguna")
        + "\n\n## Caracteristicas mas importantes\n\n"
        + "\n".join(f"- {f}: {i:.3f}" for f, i in top) + "\n",
        encoding="utf-8",
    )
    print(f"Reporte guardado en {rep}")


if __name__ == "__main__":
    main()
