# Implementation Plan: Consultar galpón y lote

**Fecha**: 2026-10-02
**Spec**: [consultar-galpón-lote.md](consultar-galpón-lote.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US3, prioridad P1)

## Summary

Administrador, Operario, Módulo 2 y Módulo 3 consultan el listado de galpones, el detalle con su lote activo y el historial de lotes.

**Enfoque técnico:**

1. `GalponController` expone tres `GET`; `page`, `estado` y `orden` se validan antes de llegar al caso de uso (FR-007, FR-008).
2. Listado: `GalponService` busca por nombre (parcial, sin distinguir mayúsculas, recortado), filtra por estado, ordena y pagina de 10 en 10 (FR-003, FR-006 a FR-008). Si el nombre no coincide con nada, repite la consulta sin el filtro de nombre y responde `sinCoincidencias=true` con un mensaje (FR-005).
3. Detalle: carga el galpón y su lote activo (`desvinculado_en IS NULL`) y calcula `edadDias` con el `Clock`, contando el día de ingreso como día 1 (FR-011, FR-012).
4. Historial: el lote activo más los lotes anteriores, del más reciente al más antiguo (FR-014).
5. Un campo de lote vacío o inválido no tumba la consulta: se omite y se informa en `incidencias` (FR-016, FR-017).

## Contexto específico

- **Tareas previas del plan general**: T006, T008, T010, T013, T014, T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── domain/model/Lote.java                              # T091 (ajuste: edadEn(hoy))
├── application/
│   ├── port/in/ConsultarGalponUseCase.java             # T040
│   └── usecase/GalponService.java                       # T041 (listado, detalle, historial)
└── infrastructure/
    ├── in/rest/
    │   ├── GalponController.java                        # T042 (3 GET)
    │   ├── dto/GalponResumenResponse.java               # T039
    │   ├── dto/GalponPaginaResponse.java                # T039
    │   ├── dto/GalponDetalleResponse.java               # T039
    │   ├── dto/LoteDetalleResponse.java                 # T039
    │   ├── dto/LoteHistorialResponse.java               # T039
    │   └── mapper/LoteRestMapper.java                   # T039 (ajuste)
    └── out/persistence/repository/GalponJpaRepository.java  # T041 (consulta por nombre, estado y orden)

src/test/java/com/unimag/avicontrol/
├── domain/model/LoteEdadTest.java                       # T091
├── application/GalponServiceConsultaTest.java           # T092
└── infrastructure/
    ├── in/rest/GalponControllerTest.java                # T037
    └── out/persistence/GalponPersistenceTest.java       # T038
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `ConsultaGalpones.java`, `ParametrosConsulta.java`, `IncidenciaResponse.java`, `GalponRestMapper.java`, `GalponPersistenceAdapter.java`. Están en la sección *Código de implementación*.

## Decisiones de diseño

**Búsqueda.** `lower(nombre) LIKE lower('%' || :nombre || '%')` con `%` y `_` escapados, para que "50%" no se interprete como comodín. Un nombre vacío o de solo espacios es "sin filtro" (FR-004).

**Sin coincidencias (FR-005).** Se vuelve a consultar sin el filtro de nombre; el filtro de estado y el orden se conservan (FR-019). La respuesta marca `sinCoincidencias=true` y trae `mensaje`.

**Orden estable.** Se ordena por el criterio pedido y se desempata siempre por `id`, para que la paginación no repita ni omita galpones. `orden=estado` ordena por el nombre del estado, no por el flujo del ciclo de vida.

**Lote activo y edad.** El lote activo es el de `desvinculado_en IS NULL` (P-08). `edadDias = días entre fechaIngreso y hoy + 1` (P-12), calculada en `Lote.edadEn(hoy)` con la fecha del `Clock`.

**Campos faltantes (FR-016, FR-017).** Si el nombre o la fecha de ingreso del lote llegan vacíos, el campo se omite de la respuesta y se informa con `CAMPO_NO_DISPONIBLE` en `incidencias`. `lote.id` es una columna `uuid` nativa, así que un identificador inválido no puede existir en la base y no hay código para ese caso. El cliente decide mostrar "No disponible".

**Solo lectura.** Los tres servicios usan `@Transactional(readOnly = true)` y devuelven `Cache-Control: no-store`.

**Rendimiento.** p95 menor a 300 ms: índices por `lower(nombre)`, `estado` y `(galpon_id, fecha_ingreso DESC)`.

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### GET `/api/v1/galpones` — Listar galpones

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `GalponPaginaResponse` |
| Éxito | 200 OK |

**Path params**: ninguno.

**Query params**

| Nombre | Tipo | Obligatorio | Por defecto | Regla |
|---|---|---|---|---|
| nombre | string | no | — | Búsqueda parcial sin distinguir mayúsculas; se recortan espacios; vacío = sin filtro |
| estado | string | no | — | Uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO |
| orden | string | no | `nombre,asc` | Formato `campo,dirección`: campo `nombre`, `capacidad` o `estado`; dirección `asc` o `desc` |
| page | integer | no | 1 | Mayor o igual a 1. Tamaño fijo de 10 |

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| contenido | array | Lista de `{id, nombre, capacidad, estado}`. |
| pagina | integer | Página actual (desde 1). |
| tamanio | integer | Elementos por página. |
| totalElementos | integer | Total de resultados. |
| totalPaginas | integer | Total de páginas. |
| sinCoincidencias | boolean | `true` si la búsqueda no encontró nada; en ese caso `contenido` trae todos los galpones. |
| mensaje | string | Opcional; solo cuando no hay coincidencias. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `orden`, `estado` o `page` inválidos |
| 500 | `ERROR_CONSULTA` | Error técnico al recuperar los datos (mensaje específico) |

Ejemplo de response:

```json
{
  "contenido": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "nombre": "Galpón Norte",
      "capacidad": 1500,
      "estado": "PRODUCTIVO"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 1,
  "totalPaginas": 1,
  "sinCoincidencias": false
}
```

### GET `/api/v1/galpones/{galponId}` — Detalle del galpón y su lote activo

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `GalponDetalleResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe ser un UUID válido |

**Query params**: ninguno.

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador asignado al crear. |
| nombre | string | Nombre del galpón. |
| capacidad | integer | Capacidad entera mayor que 0. |
| estado | string | Uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO. |
| loteActivo | object | Opcional. `{id, nombre, poblacionInicial, poblacionActual, fechaIngreso, edadDias, costoTotal}`; se omite si el galpón no tiene lote activo. |
| mensaje | string | Opcional; "No existen lotes registrados para este galpón" cuando no hay lotes. |
| incidencias | array | Lista de `{codigo, mensaje}` con datos faltantes o inconsistentes; se omite si no hay. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `galponId` no es un UUID |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe; no se devuelven datos de otro galpón |
| 500 | `ERROR_CONSULTA` | Error técnico al recuperar los datos |

Un campo del lote vacío se omite y se informa en `incidencias` con `codigo: CAMPO_NO_DISPONIBLE`; el cliente muestra "No disponible".

Ejemplo de response:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "PRODUCTIVO",
  "loteActivo": {
    "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "nombre": "Lote A1",
    "poblacionInicial": 1000,
    "poblacionActual": 975,
    "fechaIngreso": "2026-09-15",
    "edadDias": 21,
    "costoTotal": 5000000
  }
}
```

### GET `/api/v1/galpones/{galponId}/lotes` — Historial de lotes del galpón

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `LoteHistorialResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe ser un UUID válido |

**Query params**: ninguno.

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| galponId | UUID | Galpón consultado. |
| loteActivo | object | Opcional; mismo formato de `loteActivo` del detalle. Se omite si no hay lote activo. |
| lotesAnteriores | array | Lotes desvinculados, del más reciente al más antiguo. |
| incidencias | array | Lista de `{codigo, mensaje}` con datos faltantes o inconsistentes; se omite si no hay. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `galponId` no es un UUID |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |
| 500 | `ERROR_CONSULTA` | Error técnico |

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `PARAMETRO_INVALIDO` | Parámetro inválido | Revisa los parámetros de la solicitud. |
| `GALPON_NO_ENCONTRADO` | Galpón no encontrado | El galpón consultado no existe. |
| `ERROR_CONSULTA` | Error en la consulta | No fue posible obtener los datos. Puedes reintentar la consulta. |

Ejemplo `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón consultado no existe.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "GALPON_NO_ENCONTRADO"
}
```

## Peticiones y respuestas por escenario

Los tres `GET` son de solo lectura: sin body y con `Cache-Control: no-store`. Los números de escenario de los títulos son los del spec (historias 1 a 3).

**1. `200` — Escenario 1: listado sin nombre, paginado de a 10**

- **Petición**: `GET /api/v1/galpones?page=1`
- **Path params**: ninguno
- **Query params**: `page=1`
- **Body**: sin body
- **Nota**: Por defecto `orden=nombre,asc` y sin filtro de estado. El orden es sin distinguir mayúsculas.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "nombre": "Galpón Este",
      "capacidad": 800,
      "estado": "EN_COSECHA"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "nombre": "Galpón Norte",
      "capacidad": 1500,
      "estado": "PRODUCTIVO"
    },
    {
      "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "nombre": "Galpón Sur",
      "capacidad": 2000,
      "estado": "DISPONIBLE"
    },
    {
      "id": "c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f",
      "nombre": "Galpón Oeste",
      "capacidad": 1800,
      "estado": "PRODUCTIVO"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 4,
  "totalPaginas": 1,
  "sinCoincidencias": false
}
```

**2. `200` — Escenario 2: búsqueda por parte del nombre**

- **Petición**: `GET /api/v1/galpones?nombre=nor&page=1`
- **Path params**: ninguno
- **Query params**: `nombre=nor`, `page=1`
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "nombre": "Galpón Norte",
      "capacidad": 1500,
      "estado": "PRODUCTIVO"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 1,
  "totalPaginas": 1,
  "sinCoincidencias": false
}
```

**3. `200` — Escenario 3: filtro por estado y orden por capacidad descendente**

- **Petición**: `GET /api/v1/galpones?estado=PRODUCTIVO&orden=capacidad,desc&page=1`
- **Path params**: ninguno
- **Query params**: `estado=PRODUCTIVO`, `orden=capacidad,desc`, `page=1`
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f",
      "nombre": "Galpón Oeste",
      "capacidad": 1800,
      "estado": "PRODUCTIVO"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "nombre": "Galpón Norte",
      "capacidad": 1500,
      "estado": "PRODUCTIVO"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 2,
  "totalPaginas": 1,
  "sinCoincidencias": false
}
```

**4. `200` — Escenario 4: ningún nombre coincide**

- **Petición**: `GET /api/v1/galpones?nombre=xyz&page=1`
- **Path params**: ninguno
- **Query params**: `nombre=xyz`, `page=1`
- **Body**: sin body
- **Nota**: Se conservan el filtro de estado y el orden pedidos.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "nombre": "Galpón Este",
      "capacidad": 800,
      "estado": "EN_COSECHA"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "nombre": "Galpón Norte",
      "capacidad": 1500,
      "estado": "PRODUCTIVO"
    },
    {
      "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "nombre": "Galpón Sur",
      "capacidad": 2000,
      "estado": "DISPONIBLE"
    },
    {
      "id": "c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f",
      "nombre": "Galpón Oeste",
      "capacidad": 1800,
      "estado": "PRODUCTIVO"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 4,
  "totalPaginas": 1,
  "sinCoincidencias": true,
  "mensaje": "No se encontraron galpones con ese nombre. Se muestran todos los galpones."
}
```

**5. `200` — Listado cuando todavía no hay galpones registrados**

- **Petición**: `GET /api/v1/galpones?page=1`
- **Path params**: ninguno
- **Query params**: `page=1`
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 0,
  "totalPaginas": 0,
  "sinCoincidencias": false
}
```

**6. `400` — `orden` con campo o dirección no permitidos**

- **Petición**: `GET /api/v1/galpones?orden=precio,asc`
- **Path params**: ninguno
- **Query params**: `orden=precio,asc`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "orden",
      "code": "FORMATO_INVALIDO",
      "message": "Usa campo,direccion: nombre, capacidad o estado; asc o desc"
    }
  ]
}
```

**7. `400` — `estado` fuera de los seis valores**

- **Petición**: `GET /api/v1/galpones?estado=ACTIVO`
- **Path params**: ninguno
- **Query params**: `estado=ACTIVO`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "estado",
      "code": "VALOR_NO_PERMITIDO",
      "message": "Debe ser uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO"
    }
  ]
}
```

**8. `400` — `page` menor que 1**

- **Petición**: `GET /api/v1/galpones?page=0`
- **Path params**: ninguno
- **Query params**: `page=0`
- **Body**: sin body

También produce esta misma respuesta (cambia solo el body):

- `page=abc` responde igual con `fieldErrors.code = FORMATO_INVALIDO`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "page",
      "code": "MINIMO_1",
      "message": "Debe ser mayor o igual a 1"
    }
  ]
}
```

**9. `500` — Escenario 12 (spec US3): error al recuperar los datos**

- **Petición**: `GET /api/v1/galpones?page=1`
- **Path params**: ninguno
- **Query params**: `page=1`
- **Body**: sin body

Respuesta `500`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/error-consulta",
  "title": "Error en la consulta",
  "status": 500,
  "detail": "No fue posible obtener los datos. Puedes reintentar la consulta.",
  "instance": "/api/v1/galpones",
  "code": "ERROR_CONSULTA"
}
```

**10. `200` — Escenario 5: detalle del galpón con su lote activo**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: `edadDias` cuenta el día de ingreso como día 1.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "PRODUCTIVO",
  "loteActivo": {
    "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "nombre": "Lote A1",
    "poblacionInicial": 1000,
    "poblacionActual": 975,
    "fechaIngreso": "2026-09-15",
    "edadDias": 21,
    "costoTotal": 5000000
  }
}
```

**11. `200` — Escenario 6: galpón que ningún lote referencia**

- **Petición**: `GET /api/v1/galpones/6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Path params**: `galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "nombre": "Galpón Sur",
  "capacidad": 2000,
  "estado": "DISPONIBLE",
  "mensaje": "No existen lotes registrados para este galpón"
}
```

**12. `200` — Escenario 11: lote con un campo vacío ("No disponible")**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: El campo vacío se omite; el cliente muestra "No disponible". Si falta `fechaIngreso` también se omite `edadDias`.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "PRODUCTIVO",
  "loteActivo": {
    "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "poblacionInicial": 1000,
    "poblacionActual": 975,
    "fechaIngreso": "2026-09-15",
    "edadDias": 21,
    "costoTotal": 5000000
  },
  "incidencias": [
    {
      "codigo": "CAMPO_NO_DISPONIBLE",
      "mensaje": "El nombre del lote no está disponible."
    }
  ]
}
```

**13. `404` — Galpón inexistente**

- **Petición**: `GET /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón consultado no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**14. `400` — `galponId` que no es un UUID**

- **Petición**: `GET /api/v1/galpones/abc`
- **Path params**: `galponId=abc`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones/abc",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "galponId",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

**15. `500` — Error técnico al recuperar el detalle**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `500`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/error-consulta",
  "title": "Error en la consulta",
  "status": 500,
  "detail": "No fue posible obtener los datos. Puedes reintentar la consulta.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "ERROR_CONSULTA"
}
```

**16. `200` — Escenario 7: historial con lote activo y lotes anteriores**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/lotes`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: `edadDias` de un lote anterior se mide hasta el día en que salió del galpón. Orden: más reciente primero.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteActivo": {
    "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
    "nombre": "Lote B2",
    "poblacionInicial": 1200,
    "poblacionActual": 1180,
    "fechaIngreso": "2026-09-25",
    "edadDias": 11,
    "costoTotal": 6000000
  },
  "lotesAnteriores": [
    {
      "id": "9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18",
      "nombre": "Lote A1",
      "poblacionInicial": 1000,
      "poblacionActual": 940,
      "fechaIngreso": "2026-07-01",
      "edadDias": 52,
      "costoTotal": 5000000
    }
  ]
}
```

**17. `200` — Historial de un galpón que nunca tuvo lotes**

- **Petición**: `GET /api/v1/galpones/6ba7b810-9dad-41d1-80b4-00c04fd430c8/lotes`
- **Path params**: `galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "lotesAnteriores": []
}
```

**18. `404` — Historial de un galpón inexistente**

- **Petición**: `GET /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/lotes`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón consultado no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/lotes",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**19. `400` — Historial con `galponId` que no es un UUID**

- **Petición**: `GET /api/v1/galpones/abc/lotes`
- **Path params**: `galponId=abc`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones/abc/lotes",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "galponId",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/ConsultarGalponUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.Pagina;
import java.util.List;
import java.util.UUID;

public interface ConsultarGalponUseCase {

    ResultadoListado listar(ConsultaGalpones consulta);

    DetalleGalpon detalle(UUID galponId);

    HistorialLotes historialLotes(UUID galponId);

    /** `sinCoincidencias`: la búsqueda por nombre no encontró nada y `pagina` trae todos los galpones. */
    record ResultadoListado(Pagina<Galpon> pagina, boolean sinCoincidencias) {
    }

    /** `loteActivo` es null si el galpón no tiene lote activo; `sinLotes`, si nunca tuvo ninguno. */
    record DetalleGalpon(Galpon galpon, Lote loteActivo, boolean sinLotes, List<Incidencia> incidencias) {
    }

    record HistorialLotes(UUID galponId, Lote loteActivo, List<Lote> lotesAnteriores, List<Incidencia> incidencias) {
    }

    /** Dato faltante o inconsistente que no tumba la consulta (FR-016, FR-017). */
    record Incidencia(String codigo, String mensaje) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/domain/model/ConsultaGalpones.java`**

```java
package com.unimag.avicontrol.domain.model;

/** Criterios del listado de galpones; `nombre` y `estado` en null significan "sin filtro". */
public record ConsultaGalpones(String nombre, EstadoGalpon estado, CampoOrden campo, boolean ascendente,
                               int pagina, int tamanio) {

    public enum CampoOrden { NOMBRE, CAPACIDAD, ESTADO }

    public ConsultaGalpones sinNombre() {
        return new ConsultaGalpones(null, estado, campo, ascendente, pagina, tamanio);
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/ParametrosConsulta.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest;

import com.unimag.avicontrol.domain.exception.ErrorDeCampo;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.ConsultaGalpones.CampoOrden;
import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Validación de los query params de las consultas; todo fallo es 400 PARAMETRO_INVALIDO con el parámetro señalado. */
public final class ParametrosConsulta {

    public static final int TAMANIO_PAGINA_FIJO = 10;
    public static final int TAMANIO_PAGINA_POR_DEFECTO = 20;
    public static final int TAMANIO_PAGINA_MAXIMO = 100;

    private ParametrosConsulta() {
    }

    public record Orden(CampoOrden campo, boolean ascendente) {
    }

    public static int pagina(int page) {
        if (page < 1) {
            throw invalido("page", "MINIMO_1", "Debe ser mayor o igual a 1");
        }
        return page;
    }

    public static int tamanio(int size) {
        if (size < 1 || size > TAMANIO_PAGINA_MAXIMO) {
            throw invalido("size", "FUERA_DE_RANGO", "Debe estar entre 1 y 100");
        }
        return size;
    }

    /** `estado` ausente o vacío significa "sin filtro". */
    public static EstadoGalpon estadoGalpon(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return EstadoGalpon.valueOf(texto.strip());
        } catch (IllegalArgumentException e) {
            throw invalido("estado", "VALOR_NO_PERMITIDO", "Debe ser uno de: " + valores(EstadoGalpon.values()));
        }
    }

    /** Por defecto PENDIENTE (la bandeja del administrador). */
    public static EstadoAlertaMantenimiento estadoAlerta(String texto) {
        if (texto == null || texto.isBlank()) {
            return EstadoAlertaMantenimiento.PENDIENTE;
        }
        try {
            return EstadoAlertaMantenimiento.valueOf(texto.strip());
        } catch (IllegalArgumentException e) {
            throw invalido("estado", "VALOR_NO_PERMITIDO",
                    "Debe ser uno de: " + valores(EstadoAlertaMantenimiento.values()));
        }
    }

    /** Formato `campo,direccion`: campo nombre, capacidad o estado; dirección asc o desc (la dirección es opcional). */
    public static Orden orden(String texto) {
        String[] partes = texto.strip().split(",");
        if (partes.length > 2) {
            throw ordenInvalido();
        }
        CampoOrden campo;
        try {
            campo = CampoOrden.valueOf(partes[0].strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ordenInvalido();
        }
        String direccion = partes.length == 2 ? partes[1].strip().toLowerCase() : "asc";
        if (!direccion.equals("asc") && !direccion.equals("desc")) {
            throw ordenInvalido();
        }
        return new Orden(campo, direccion.equals("asc"));
    }

    private static ValidacionNegocioException ordenInvalido() {
        return invalido("orden", "FORMATO_INVALIDO", "Usa campo,direccion: nombre, capacidad o estado; asc o desc");
    }

    private static ValidacionNegocioException invalido(String parametro, String codigo, String mensaje) {
        return new ValidacionNegocioException("PARAMETRO_INVALIDO", "Revisa los parámetros de la solicitud.",
                new ErrorDeCampo(parametro, codigo, mensaje));
    }

    private static String valores(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).collect(Collectors.joining(", "));
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/GalponResumenResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.UUID;

public record GalponResumenResponse(UUID id, String nombre, long capacidad, EstadoGalpon estado) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/GalponPaginaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.util.List;

/** `mensaje` solo viene cuando `sinCoincidencias` es true; entonces `contenido` trae todos los galpones. */
public record GalponPaginaResponse(List<GalponResumenResponse> contenido, int pagina, int tamanio,
                                   long totalElementos, int totalPaginas, boolean sinCoincidencias, String mensaje) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/GalponDetalleResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.List;
import java.util.UUID;

/** `loteActivo`, `mensaje` e `incidencias` se omiten cuando no aplican (nunca viajan como null). */
public record GalponDetalleResponse(UUID id, String nombre, long capacidad, EstadoGalpon estado,
                                    LoteDetalleResponse loteActivo, String mensaje,
                                    List<IncidenciaResponse> incidencias) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/LoteDetalleResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Un campo vacío del lote (nombre, fechaIngreso, edadDias) se omite y se informa en `incidencias`. */
public record LoteDetalleResponse(UUID id, String nombre, long poblacionInicial, long poblacionActual,
                                  LocalDate fechaIngreso, Integer edadDias, long costoTotal) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/LoteHistorialResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.util.List;
import java.util.UUID;

public record LoteHistorialResponse(UUID galponId, LoteDetalleResponse loteActivo,
                                    List<LoteDetalleResponse> lotesAnteriores, List<IncidenciaResponse> incidencias) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/IncidenciaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

public record IncidenciaResponse(String codigo, String mensaje) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/GalponRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase.DetalleGalpon;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase.HistorialLotes;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase.Incidencia;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase.ResultadoListado;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponDetalleResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponResumenResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.IncidenciaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LoteHistorialResponse;
import java.time.LocalDate;
import java.util.List;

public final class GalponRestMapper {

    private static final String SIN_COINCIDENCIAS =
            "No se encontraron galpones con ese nombre. Se muestran todos los galpones.";
    private static final String SIN_LOTES = "No existen lotes registrados para este galpón";

    private GalponRestMapper() {
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    public static GalponPaginaResponse aPagina(ResultadoListado resultado) {
        Pagina<Galpon> p = resultado.pagina();
        List<GalponResumenResponse> contenido = p.contenido().stream()
                .map(g -> new GalponResumenResponse(g.id(), g.nombre(), g.capacidad(), g.estado())).toList();
        return new GalponPaginaResponse(contenido, p.pagina(), p.tamanio(), p.totalElementos(), p.totalPaginas(),
                resultado.sinCoincidencias(), resultado.sinCoincidencias() ? SIN_COINCIDENCIAS : null);
    }

    public static GalponDetalleResponse aDetalle(DetalleGalpon d, LocalDate hoy) {
        Galpon g = d.galpon();
        return new GalponDetalleResponse(g.id(), g.nombre(), g.capacidad(), g.estado(),
                d.loteActivo() == null ? null : LoteRestMapper.aDetalle(d.loteActivo(), hoy),
                d.sinLotes() ? SIN_LOTES : null,
                aIncidencias(d.incidencias()));
    }

    public static LoteHistorialResponse aHistorial(HistorialLotes h, LocalDate hoy) {
        return new LoteHistorialResponse(h.galponId(),
                h.loteActivo() == null ? null : LoteRestMapper.aDetalle(h.loteActivo(), hoy),
                h.lotesAnteriores().stream().map(l -> LoteRestMapper.aDetalle(l, hoy)).toList(),
                aIncidencias(h.incidencias()));
    }

    /** Lista vacía = se omite el campo `incidencias` (convención de nulos omitidos). */
    private static List<IncidenciaResponse> aIncidencias(List<Incidencia> incidencias) {
        return incidencias.isEmpty() ? null
                : incidencias.stream().map(i -> new IncidenciaResponse(i.codigo(), i.mensaje())).toList();
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/LoteRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LoteDetalleResponse;
import java.time.LocalDate;

public final class LoteRestMapper {

    private LoteRestMapper() {
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    /**
     * `edadDias` cuenta el día de ingreso como día 1. Para el lote activo se mide hasta hoy; para uno ya
     * desvinculado, hasta el día en que salió del galpón. Sin fecha de ingreso se omite (FR-016).
     */
    public static LoteDetalleResponse aDetalle(Lote l, LocalDate hoy) {
        LocalDate referencia = l.activo() ? hoy : l.desvinculadoEn().atZone(ZonaNegocio.BOGOTA).toLocalDate();
        Integer edad = l.fechaIngreso() == null ? null : l.edadEn(referencia);
        return new LoteDetalleResponse(l.id(), l.nombre(), l.poblacionInicial(), l.poblacionActual(),
                l.fechaIngreso(), edad, l.costoTotal());
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/GalponService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.ActualizarEstadoUseCase;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.EditarGalponUseCase;
import com.unimag.avicontrol.application.port.in.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GalponService implements RegistrarGalponUseCase, ConsultarGalponUseCase, ActualizarEstadoUseCase,
        EditarGalponUseCase {

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final HistorialEstadoRepository historialEstados;
    private final HistorialCambioGalponRepository historialCambios;
    private final GalponEstadoService estados;
    private final Clock clock;

    public GalponService(GalponRepository galpones, LoteRepository lotes, HistorialEstadoRepository historialEstados,
                         HistorialCambioGalponRepository historialCambios, GalponEstadoService estados, Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.historialEstados = historialEstados;
        this.historialCambios = historialCambios;
        this.estados = estados;
        this.clock = clock;
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    @Override
    @Transactional(readOnly = true)
    public ResultadoListado listar(ConsultaGalpones consulta) {
        Pagina<Galpon> pagina = galpones.search(consulta);
        if (pagina.totalElementos() == 0 && consulta.nombre() != null) {
            // FR-005: sin coincidencias se muestran todos, conservando el filtro de estado y el orden (FR-019)
            return new ResultadoListado(galpones.search(consulta.sinNombre()), true);
        }
        return new ResultadoListado(pagina, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleGalpon detalle(UUID galponId) {
        Galpon galpon = galponConsultado(galponId);
        Lote activo = lotes.findActivoByGalponId(galponId).orElse(null);
        boolean sinLotes = activo == null && !lotes.existsByGalponId(galponId);
        return new DetalleGalpon(galpon, activo, sinLotes, incidenciasDe(activo));
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialLotes historialLotes(UUID galponId) {
        galponConsultado(galponId);
        List<Lote> todos = lotes.findByGalponIdOrderByFechaIngresoDesc(galponId);
        Lote activo = todos.stream().filter(Lote::activo).findFirst().orElse(null);
        List<Lote> anteriores = todos.stream().filter(l -> !l.activo()).toList();
        List<Incidencia> incidencias = new ArrayList<>(incidenciasDe(activo));
        anteriores.forEach(l -> incidencias.addAll(incidenciasDe(l)));
        return new HistorialLotes(galponId, activo, anteriores, incidencias);
    }

    private Galpon galponConsultado(UUID galponId) {
        return galpones.findById(galponId).orElseThrow(() -> new RecursoNoEncontradoException(
                "GALPON_NO_ENCONTRADO", "El galpón consultado no existe."));
    }

    /** Un campo vacío del lote no tumba la consulta: se omite en la respuesta y se informa aquí (FR-016). */
    private static List<Incidencia> incidenciasDe(Lote lote) {
        List<Incidencia> incidencias = new ArrayList<>();
        if (lote == null) {
            return incidencias;
        }
        if (lote.nombre() == null || lote.nombre().isBlank()) {
            incidencias.add(new Incidencia("CAMPO_NO_DISPONIBLE", "El nombre del lote no está disponible."));
        }
        if (lote.fechaIngreso() == null) {
            incidencias.add(new Incidencia("CAMPO_NO_DISPONIBLE",
                    "La fecha de ingreso del lote no está disponible; no se puede calcular la edad."));
        }
        return incidencias;
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/GalponController.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest;

import com.unimag.avicontrol.application.port.in.ActualizarEstadoUseCase;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.EditarGalponUseCase;
import com.unimag.avicontrol.application.port.in.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.model.ConsultaGalpones;
import com.unimag.avicontrol.domain.policy.ReglasFecha;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponDetalleResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LoteHistorialResponse;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.GalponRestMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST: valida la forma, delega en el puerto y mapea. No contiene reglas de negocio. */
@RestController
@RequestMapping("/api/v1/galpones")
public class GalponController {

    private final RegistrarGalponUseCase registrarGalpon;
    private final ConsultarGalponUseCase consultarGalpon;
    private final ActualizarEstadoUseCase actualizarEstado;
    private final EditarGalponUseCase editarGalpon;
    private final Clock clock;

    public GalponController(RegistrarGalponUseCase registrarGalpon, ConsultarGalponUseCase consultarGalpon,
                            ActualizarEstadoUseCase actualizarEstado, EditarGalponUseCase editarGalpon, Clock clock) {
        this.registrarGalpon = registrarGalpon;
        this.consultarGalpon = consultarGalpon;
        this.actualizarEstado = actualizarEstado;
        this.editarGalpon = editarGalpon;
        this.clock = clock;
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    @GetMapping
    public GalponPaginaResponse listar(@RequestParam(required = false) String nombre,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(defaultValue = "nombre,asc") String orden,
                                       @RequestParam(defaultValue = "1") int page) {
        ParametrosConsulta.Orden ordenado = ParametrosConsulta.orden(orden);
        ConsultaGalpones consulta = new ConsultaGalpones(
                nombre == null || nombre.isBlank() ? null : nombre.strip(),
                ParametrosConsulta.estadoGalpon(estado),
                ordenado.campo(), ordenado.ascendente(),
                ParametrosConsulta.pagina(page), ParametrosConsulta.TAMANIO_PAGINA_FIJO);
        return GalponRestMapper.aPagina(consultarGalpon.listar(consulta));
    }

    @GetMapping("/{galponId}")
    public GalponDetalleResponse detalle(@PathVariable UUID galponId) {
        return GalponRestMapper.aDetalle(consultarGalpon.detalle(galponId), ReglasFecha.hoy(clock));
    }

    @GetMapping("/{galponId}/lotes")
    public LoteHistorialResponse historialLotes(@PathVariable UUID galponId) {
        return GalponRestMapper.aHistorial(consultarGalpon.historialLotes(galponId), ReglasFecha.hoy(clock));
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/repository/GalponJpaRepository.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.repository;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.GalponEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GalponJpaRepository extends JpaRepository<GalponEntity, UUID> {

    /** `SELECT ... FOR UPDATE`: serializa los cambios de estado concurrentes del mismo galpón. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GalponEntity g where g.id = :id")
    Optional<GalponEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(g) > 0 from GalponEntity g where lower(g.nombre) = :clave")
    boolean existsByClave(@Param("clave") String clave);

    @Query("select count(g) > 0 from GalponEntity g where lower(g.nombre) = :clave and g.id <> :id")
    boolean existsByClaveAndIdNot(@Param("clave") String clave, @Param("id") UUID id);

    /** `patron` ya viene en minúsculas, con los comodines escapados y rodeado de `%`. */
    @Query("""
            select g from GalponEntity g
            where g.estado in :estados and lower(g.nombre) like :patron escape '\\'
            """)
    Page<GalponEntity> buscar(@Param("estados") Collection<EstadoGalpon> estados, @Param("patron") String patron,
                              Pageable pageable);
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/GalponPersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.model.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.GalponPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.GalponJpaRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class GalponPersistenceAdapter implements GalponRepository {

    private final GalponJpaRepository jpa;

    public GalponPersistenceAdapter(GalponJpaRepository jpa) {
        this.jpa = jpa;
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    @Override
    public Optional<Galpon> findById(UUID id) {
        return jpa.findById(id).map(GalponPersistenceMapper::aDominio);
    }

    @Override
    public Optional<Galpon> findByIdForUpdate(UUID id) {
        return jpa.findByIdForUpdate(id).map(GalponPersistenceMapper::aDominio);
    }

    @Override
    public boolean existsByNombreClave(String clave) {
        return jpa.existsByClave(clave);
    }

    @Override
    public boolean existsByNombreClaveAndIdNot(String clave, UUID id) {
        return jpa.existsByClaveAndIdNot(clave, id);
    }

    @Override
    public Pagina<Galpon> search(ConsultaGalpones c) {
        List<EstadoGalpon> estados = c.estado() == null ? Arrays.asList(EstadoGalpon.values()) : List.of(c.estado());
        Page<?> pagina;
        Sort.Direction sentido = c.ascendente() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order principal = new Sort.Order(sentido, switch (c.campo()) {
            case NOMBRE -> "nombre";
            case CAPACIDAD -> "capacidad";
            case ESTADO -> "estado";
        });
        if (c.campo() == ConsultaGalpones.CampoOrden.NOMBRE) {
            principal = principal.ignoreCase();
        }
        // Siempre se desempata por id: la paginación no repite ni omite galpones.
        var peticion = PageRequest.of(c.pagina() - 1, c.tamanio(), Sort.by(principal, Sort.Order.asc("id")));
        var resultado = jpa.buscar(estados, patron(c.nombre()), peticion);
        return new Pagina<>(resultado.getContent().stream().map(GalponPersistenceMapper::aDominio).toList(),
                c.pagina(), c.tamanio(), resultado.getTotalElements());
    }

    /** Búsqueda parcial sin distinguir mayúsculas; `%`, `_` y `\` del texto se escapan para no actuar de comodín. */
    private static String patron(String nombre) {
        if (nombre == null) {
            return "%";
        }
        String escapado = nombre.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
```

## Fuera de alcance

- Solo lectura: no crea ni modifica galpones ni lotes.
- La diferencia de permisos entre administrador y operario queda pendiente en el spec (FR-022); el plan general deja la autenticación fuera del alcance.
- Conservar búsqueda, filtros y orden ante un error es del cliente: la API no guarda estado.
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Coherencia entre módulos

El Módulo 1 es **proveedor**: dueño de galpones y lotes. Consumen estos servicios el Módulo 3 (liquidación, por REST) y el Módulo 2 (consulta de galpones, edad y resumen).

| Aspecto | Qué define este plan | A acordar con |
|---|---|---|
| Nombres de campo | `capacidad`. El plan del Módulo 2 lo llama `aforoMaximo`; el resto coincide (`id`, `nombre`, `estado`, `poblacionInicial`, `poblacionActual`, `fechaIngreso`, `costoTotal`, `galponId`). | Módulo 2: traducir en su mapper o acordar un solo nombre |
| Estados | Seis valores exactos: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO. | Módulos 2 y 3 |
| Lote activo | El lote con `desvinculado_en IS NULL`, nunca "el de fecha más reciente". Coincide con el plan del Módulo 2 ("la interfaz propietaria identifica el lote actualmente alojado"). | Módulo 2 |
| Edad | Día de ingreso = día 1, como en el Módulo 2. Ambos módulos deben mostrar la misma edad para el mismo lote. | Módulo 2 |
| Tipos y formatos | UUID y fechas como texto; `costoTotal` entero en COP (el Módulo 2 lo modela con decimales: compatible). | Módulo 3 |
| Sin lote | `200` sin el campo `loteActivo`. Galpón inexistente: `404`. Errores en ProblemDetail con `code`. | Módulos 2 y 3 |
| Ruta y versión | `/api/v1/galpones`. El plan del Módulo 2 usa `/api/galpones`; si comparten aplicación, no duplicar controladores ni rutas. | Módulo 2 |
| Paginación y nulos | `page` desde 1, tamaño fijo de 10 y campos ausentes omitidos (no `null`), según las convenciones de contrato. | Módulos 2 y 3 |

Lo que el Módulo 2 pide y este spec no cubre: resumen de conteos por estado, lote actual por conjunto de galpones y páginas de más de 10. No se agrega sin un spec; si lo necesitan, se pide como servicio nuevo (por ejemplo `GET /api/v1/galpones/resumen`).

Pendientes relacionados: P-08 (lote activo), P-09 (nombres y rutas), P-10 (topología), P-12 (edad) y P-20 (servicios que el Módulo 2 pide y ningún spec cubre).

**Evidencia de coherencia:** La prueba de contrato T037 compara la respuesta contra los fixtures `galpon-*.json` de este plan, que son los JSON que se comparten con los otros módulos.

## Fixtures

En `src/test/resources/contratos/`. Son los mismos JSON que se comparten con los Módulos 2 y 3:

| Archivo | Contenido |
|---|---|
| `galpon-lista-response.json` | Una página del listado |
| `galpon-lista-sin-coincidencias-response.json` | Búsqueda sin resultados: `sinCoincidencias` y `mensaje` |
| `galpon-detalle-response.json` | Detalle con lote activo |
| `galpon-detalle-sin-lote-response.json` | Detalle de un galpón sin lotes (sin `loteActivo`) |
| `galpon-historial-lotes-response.json` | Lote activo y lotes anteriores |
| `error-galpon-no-encontrado.json` | El `404` de ejemplo |

## Tareas

**Goal**: Administrador, Operario, Modulo 2 y Modulo 3 consultan el listado de galpones, el detalle con el lote activo y el historial de lotes.

**Independent Test**: con 15 galpones el listado devuelve 10 paginados; busqueda sin resultados devuelve todos con sinCoincidencias true; detalle sin lote devuelve la respuesta sin el campo loteActivo.

### Tests

- [ ] T037 [P] [US3] Prueba de contrato comparando contra los fixtures `galpon-*.json` y `error-galpon-no-encontrado.json`: listado de galpones, detalle de galpon con lote activo e historial de lotes — incluidos 404 y criterio de orden invalido — clase `GalponControllerTest` (FR-001 a FR-010, FR-014, FR-015, FR-018, FR-021; SC-001, SC-008)
- [ ] T038 [P] [US3] Prueba de persistencia: busqueda parcial sin mayusculas, filtro por estado, paginacion y lote activo (desvinculado_en IS NULL) — clase `GalponPersistenceTest` (FR-003, FR-006 a FR-008, FR-013; SC-002, SC-008)
- [ ] T091 [P] [US3] Prueba unitaria de dominio `Lote.edadEn(hoy)`: el día de ingreso cuenta como día 1; ingreso hace 20 días da 21; ingreso hoy da 1 — clase `LoteEdadTest` (FR-012; P-12)
- [ ] T092 [P] [US3] Pruebas unitarias de `GalponService` (Mockito) en test/application/: búsqueda sin coincidencias devuelve todos con `sinCoincidencias=true` conservando el filtro de estado; nombre vacío es sin filtro; orden desempata por id; lote con campo vacío genera incidencia sin fallar la consulta — clase `GalponServiceConsultaTest` (FR-004, FR-005, FR-016, FR-017, FR-019)

### Implementation

- [ ] T039 [P] [US3] Crear GalponResumenResponse, GalponPaginaResponse, GalponDetalleResponse, LoteDetalleResponse y LoteHistorialResponse en main/infrastructure/in/rest/dto/; actualizar LoteRestMapper con calculo de edadDias usando Clock (FR-011, FR-012, FR-016, FR-017)
- [ ] T040 [P] [US3] Crear ConsultarGalponUseCase en main/application/port/in/
- [ ] T041 [US3] Implementar en GalponService: listado paginado (10), busqueda parcial con reintento sin filtro, filtro por estado, orden permitido; detalle con lote activo (desvinculado_en IS NULL) e historial (FR-003 a FR-008, FR-011 a FR-015)
- [ ] T042 [US3] Implementar en GalponController: GET /api/v1/galpones, GET /api/v1/galpones/{galponId} y GET /api/v1/galpones/{galponId}/lotes (FR-001, FR-009, FR-014)

**Checkpoint**: la consulta sirve para verificar las historias siguientes.

## Dependencias

- Ninguna entre historias. Sirve para verificar los resultados de las demás.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-08: los specs ya definen `desvinculado_en` (nulo = lote activo) en la entidad Lote, en FR-013 de este spec y en FR-008 del spec de vaciado sanitario; queda por confirmar con el Módulo 2.
- P-12: edad con el día de ingreso como día 1, igual que el Módulo 2; el spec no lo define.
- P-04: confirmar si el Operario también ve alertas de mantenimiento; FR-022 deja pendiente la diferencia de permisos.
- P-20: resumen de conteos por estado, lote actual por conjunto de galpones y páginas de más de 10 que el Módulo 2 pide y este spec no cubre.
