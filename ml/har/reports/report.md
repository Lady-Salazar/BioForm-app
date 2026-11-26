# Reporte del modelo HAR

Entrenado: 2026-11-26T01:41:02

- Validacion: independiente del sujeto (GroupKFold, 2 sujetos)
- Ventanas evaluadas: 574 de 574 (100 %)
- Exactitud: 0.803
- F1 macro: 0.766
- Grabaciones: 14 · Ventanas: 574 · Sujetos: S01, S02
- Telefonos: 2311DRK48G, SM-A536E

## Matriz de confusion (filas = real, columnas = predicho)

|             |   caminar |   reposo |   saltos |   sentadillas |   trotar |
|:------------|----------:|---------:|---------:|--------------:|---------:|
| caminar     |        67 |        0 |       15 |             0 |        0 |
| reposo      |         0 |      161 |        0 |             3 |        0 |
| saltos      |         1 |        0 |      111 |             2 |        9 |
| sentadillas |        29 |        1 |        2 |            87 |        4 |
| trotar      |         0 |        0 |       47 |             0 |       35 |

## Reporte por clase

```
              precision    recall  f1-score   support

     caminar       0.69      0.82      0.75        82
      reposo       0.99      0.98      0.99       164
      saltos       0.63      0.90      0.74       123
 sentadillas       0.95      0.71      0.81       123
      trotar       0.73      0.43      0.54        82

    accuracy                           0.80       574
   macro avg       0.80      0.77      0.77       574
weighted avg       0.83      0.80      0.80       574
```

## Grabaciones descartadas

- S01_reposo_bolsillo_20261125_195038_2311DRK48G.csv: pantalla hacia afuera o telefono boca arriba (az=7.0)

## Caracteristicas mas importantes

- gx_energy: 0.060
- gy_std: 0.059
- gx_std: 0.053
- gy_energy: 0.052
- acc_mag_energy: 0.050
- acc_mag_std: 0.050
- ay_std: 0.047
- acc_mag_mean: 0.046
- acc_mag_max: 0.041
- ay_energy: 0.035
