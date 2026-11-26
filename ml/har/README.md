# Entrenamiento del modelo de actividad (HAR)

Modelo **Random Forest** que reconoce la actividad a partir del acelerómetro y el giroscopio:
`reposo`, `caminar`, `trotar`, `saltos`, `sentadillas`.

## Dónde vive cada parte

| Parte | Dónde | Qué hace |
| :--- | :--- | :--- |
| Recolección | App → menú del avatar → **Recolectar datos** | Graba 60 s a 50 Hz y guarda un CSV |
| Datos | `ml/har/data/raw/` (no se sube a GitHub) | CSV copiados del teléfono |
| Características | `backend/app/har/features.py` | Ventanas de 2.56 s y 65 características; las usa el entrenamiento Y el backend |
| Entrenamiento | `ml/har/train_har.py` (en tu computador) | Entrena, evalúa por sujeto y guarda el modelo |
| Modelo | `backend/app/models/har_rf.joblib` (sí se sube) | Lo carga el backend en Render |
| Uso | `POST /har/classify` | Devuelve la actividad, su categoría y la confianza |

## Protocolo de recolección

> **NOTA IMPORTANTE: posición del teléfono (igual en TODAS las grabaciones)**
>
> 1. **Bolsillo delantero DERECHO** del pantalón.
> 2. **Vertical**, con la **cámara (parte de arriba) hacia ABAJO**: el teléfono entra al bolsillo por la parte de la cámara.
> 3. **Pantalla mirando hacia la pierna.**
> 4. **Nunca** en la mano, sobre la mesa ni en otro bolsillo.
>
> La app muestra una animación de 5 segundos con esta posición antes de cada grabación
> (menú del avatar → Recolectar datos).
>
> **Por qué:** el acelerómetro mide hacia dónde apunta la gravedad. Si la posición cambia entre
> actividades, el modelo aprende la posición en lugar del movimiento: da buenos resultados en el
> entrenamiento y falla con usuarios reales.
>
> **Cómo se verifica:** con el teléfono vertical y la cámara hacia abajo, el eje `ay` del acelerómetro
> queda cerca de **−9.8 m/s²** de pie. Valores de `az` cercanos a ±9.8 de pie indican que el teléfono
> estaba horizontal (en la mano o sobre una mesa): esa grabación se descarta.

- Cada participante usa un **código** (S01, S02…), nunca su nombre.
- Por persona: **2 grabaciones de 60 s por actividad** (10 grabaciones, unos 15 minutos).
- **Reposo:** una grabación **sentado** y otra **de pie**, siempre con el teléfono en el bolsillo.
- Mínimo **3 personas** para evaluar con validación independiente del sujeto.

## Paso a paso

```powershell
cd C:\Users\USUARIO\Desktop\Bioform-app
backend\.venv\Scripts\Activate.ps1

# 1. Cada participante toca "Compartir grabaciones" y envía su .zip.
#    Guarda TODOS los .zip, sin descomprimir, en:  ml/har/data/zips/
#    (puedes dejar los .zip viejos: el script no duplica grabaciones)

# 2. (Opcional) Excluir grabaciones a mano: escribe sus nombres en ml/har/data/excluir.txt, uno por línea

# 3. Entrenar
python ml/har/train_har.py

# 4. Revisar ml/har/reports/report.md y subir el modelo
git add backend/app/models/har_rf.joblib ml/har/reports
git commit -m "Modelo HAR entrenado"
git push   # Render lo publica solo
```

El script hace todo solo:

1. **Descomprime** cada .zip de `ml/har/data/zips/` en `ml/har/data/raw/<nombre del zip>/`, sin repetir grabaciones ya importadas.
2. **Revisa la posición del teléfono** de cada grabación y **descarta** las que no cumplen el protocolo (cámara hacia arriba, pantalla hacia afuera o teléfono horizontal). Para no revisarla: `--no-check`.
3. **Entrena y evalúa** por participante, y guarda el modelo y el reporte (con la lista de grabaciones descartadas y el motivo).

## Probar el pipeline sin datos reales

`ml/har/tools/make_synthetic_data.py` genera señales **inventadas** solo para comprobar que todo funciona.
**Nunca** uses ese modelo ni sus métricas en la presentación ni en el backend.

## Reloj

El mismo formato de CSV sirve para un reloj **Wear OS** (Galaxy Watch 4+, Pixel Watch) con posición `muneca`.
Entrena por posición con `python ml/har/train_har.py --position muneca`.
Los relojes Huawei no permiten leer las señales crudas de sus sensores.
