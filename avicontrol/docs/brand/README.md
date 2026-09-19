AviControl es el software de control avícola: lotes, galpones, ambiente y producción. La marca se siente de campo y de laboratorio a la vez: cálida como el cascarón, clara como un tablero de datos. Esta primera versión parte de cinco colores dados y de un nombre provisional.

## Fundamentos de contenido

- Escribe en español, en tono directo y tranquilo: "Lote 14 · 32 semanas", "Temperatura fuera de rango en Galpón 3".
- Habla al usuario de tú; nunca uses emojis. Casing de oración en títulos y botones ("Registrar mortalidad"), nunca todo en mayúsculas.
- Cifras y códigos de lote en el estilo `data`; unidades siempre visibles (°C, %, g, aves).
- Una alarma dice qué pasó, dónde y qué hacer, con icono y palabra además del color.

## Fundamentos visuales

- Fondo: `surface-100` en toda pantalla; tarjetas y paneles en `surface-200` con borde `line` de 1 px, sin sombras.
- Texto: `ink` para lectura y títulos, `ink-muted` para apoyo. `brand` sólo como texto sobre `surface-100` y `surface-200`.
- Acción primaria: relleno `brand` con texto `on-brand`. Secundaria: contorno `earth` de 1 px sobre `surface-100`. Un solo botón primario por vista.
- `accent` (ámbar) marca atención y el dato clave de la pantalla; rellena con `on-accent` como texto. `danger` sólo para alarmas críticas.
- Estados normales: chip `brand-soft` con texto `ink`. Estados de advertencia: chip `accent`. Críticos: chip `danger` con `on-danger`. Verde y rojo no se distinguen sólo por tono: cada estado lleva icono y palabra.
- Gráficos: serie principal `brand`, secundaria `earth`, apoyo `brand-soft`, umbral o alerta `accent`.
- Foco: anillo sólido de 2 px en `ink` con 2 px de separación, visible sobre `surface-100`, `surface-200`, `brand` y `accent` (mín. 3:1).
- Tipografía: `display`, `h1` y `h2` en Fraunces (serif cálida); interfaz en DM Sans; cifras y lotes en IBM Plex Mono como `data`.
- Espaciado en pasos de 4 px (`space-1` a `space-8`); esquinas `radius-sm`, `radius-md` (botones, campos) y `radius-lg` (tarjetas).
- Motivo: el huevo y su bandeja. Úsalo como patrón (cuadrícula de óvalos) en portadas y estados vacíos, no como decoración en pantallas de trabajo.

## Iconografía y logo

El isotipo `assets/Logos/avicontrol-mark.svg` (huevo con yema ámbar sobre cuadrado verde) es una propuesta original para este proyecto. Los iconos de interfaz aún no están definidos: usa trazo de 1.5 px y esquinas redondeadas en `ink`, y avísalo como pendiente.
