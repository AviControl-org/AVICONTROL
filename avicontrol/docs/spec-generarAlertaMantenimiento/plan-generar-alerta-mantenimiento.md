# Implementation Plan: Generar alerta de mantenimiento

**Fecha**: 2026-10-02
**Spec**: [generar-alerta-mantenimiento-spec.md](generar-alerta-mantenimiento-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US8, prioridad P1)

## Summary

El técnico reporta una falla en un galpón DISPONIBLE; el administrador atiende la alerta y el galpón pasa a MANTENIMIENTO.

**Enfoque técnico:**

1. `POST` crea una alerta PENDIENTE sin cambiar el estado del galpón (FR-008). `GET` lista la bandeja de pendientes y el historial. `PATCH` atiende la alerta.
2. `AlertaMantenimientoService.generar` valida que el galpón exista y esté DISPONIBLE, que la descripción no esté vacía y tenga como máximo 500 caracteres, y que no haya otra alerta PENDIENTE para ese galpón; la fecha y hora las pone el servidor con el `Clock` (FR-003 a FR-006).
3. La unicidad de alerta activa por galpón la garantiza el índice parcial `UNIQUE (galpon_id) WHERE estado = 'PENDIENTE'` (T006): ante dos técnicos simultáneos, la violación se traduce a `ALERTA_ACTIVA_EXISTENTE`.
4. `AlertaMantenimientoService.atender` marca la alerta ATENDIDA y solicita DISPONIBLE -> MANTENIMIENTO a `GalponEstadoService` con origen `ADMINISTRADOR`, en la misma transacción (FR-008). Si el cambio se rechaza, la alerta sigue PENDIENTE.

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T016, T018, T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/AlertaMantenimientoUseCase.java          # T072
│   └── usecase/AlertaMantenimientoService.java           # T073 (generar, atender, listar)
└── infrastructure/in/rest/
    ├── AlertaMantenimientoController.java                # T074
    ├── dto/
    │   ├── GenerarAlertaMantenimientoRequest.java        # T071
    │   ├── AlertaMantenimientoResponse.java              # T071
    │   ├── AtenderAlertaRequest.java                     # T071
    │   ├── AtenderAlertaResponse.java                    # T071
    │   └── AlertaMantenimientoPaginaResponse.java        # T071
    └── mapper/AlertaMantenimientoRestMapper.java         # T071

src/test/java/com/unimag/avicontrol/
├── application/AlertaMantenimientoServiceTest.java       # T097
└── infrastructure/
    ├── in/rest/AlertaMantenimientoControllerTest.java    # T069
    └── out/persistence/AlertaMantenimientoPersistenceTest.java  # T070
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `AlertaMantenimientoJpaRepository.java`, `AlertaMantenimientoPersistenceAdapter.java`. Están en la sección *Código de implementación*.

`AlertaMantenimiento`, su entidad y puerto ya existen por el plan general (T009, T010).

## Decisiones de diseño

**Descripción.** Se recorta antes de validar: una descripción de solo espacios se considera vacía (edge case del spec). El máximo es 500 caracteres (FR-004).

**Un único estado válido en el `PATCH`.** Solo se acepta `ATENDIDA`. `ELIMINADA` existe en el modelo pero ningún spec define quién ni cómo elimina una alerta, así que no tiene flujo.

**Atender es atómico.** `findByIdForUpdate` del galpón (vía `GalponEstadoService`) serializa la atención con cualquier otro cambio de estado. Si el galpón dejó de estar DISPONIBLE, la respuesta es `ESTADO_ESPERADO_CAMBIADO` y la alerta no se marca ATENDIDA (P-21).

**Una alerta pendiente por galpón.** Se comprueba antes de insertar para dar un mensaje claro, y el índice parcial resuelve la carrera: el adaptador de persistencia traduce la `DataIntegrityViolationException` a `ConflictoException` (`ALERTA_ACTIVA_EXISTENTE`).

**Bandeja e historial.** `GET` filtra por `estado` (por defecto `PENDIENTE`) y por `galponId`, con los más recientes primero. La misma ruta sirve de historial para el administrador (FR-010).

**Técnico.** Sin autenticación, `tecnicoId` llega en el body y es obligatorio (P-14). `severidad` y `tipo` son texto libre opcional (P-15).

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### POST `/api/v1/alertas-mantenimiento` — Generar alerta de mantenimiento

| Elemento | Definición |
|---|---|
| Request DTO | `GenerarAlertaMantenimientoRequest` |
| Response DTO | `AlertaMantenimientoResponse` |
| Éxito | 201 Created, cabecera `Location: /api/v1/alertas-mantenimiento/{id}` |

**Path params**: ninguno.

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe existir y estar DISPONIBLE |
| descripcion | string | sí | No vacía tras recortar espacios; máximo 500 caracteres |
| severidad | string | no | Texto libre hasta que se defina un catálogo |
| tipo | string | no | Texto libre hasta que se defina un catálogo |
| tecnicoId | string | sí (provisional) | Identificador del técnico mientras no exista autenticación (P-14) |

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador de la alerta. |
| galponId | UUID | Galpón afectado. |
| descripcion | string | Descripción de la falla. |
| fechaHora | datetime | Capturada por el servidor. |
| tecnicoId | string | Técnico que la generó (provisional). |
| estado | string | `PENDIENTE`, `ATENDIDA` o `ELIMINADA`. |
| severidad | string | Opcional; se omite si no se informó. |
| tipo | string | Opcional; se omite si no se informó. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `DESCRIPCION_INVALIDA` | Vacía, solo espacios o mayor a 500 |
| 400 | `GALPON_REQUERIDO` | `galponId` ausente |
| 400 | `TECNICO_REQUERIDO` | `tecnicoId` ausente o vacío |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |
| 409 | `GALPON_NO_DISPONIBLE` | El galpón no está DISPONIBLE |
| 409 | `ALERTA_ACTIVA_EXISTENTE` | Ya hay una alerta PENDIENTE para ese galpón (también en concurrencia, por el índice parcial) |
| 400 | `VALIDACION_FALLIDA` | Dos o más campos del body inválidos a la vez (el detalle va en `fieldErrors`) |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |

Generar la alerta no cambia el estado del galpón (FR-008). Los galpones del formulario se obtienen con `GET /api/v1/galpones?estado=DISPONIBLE`.

Ejemplo de request:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tecnico-01"
}
```

Ejemplo de response:

```json
{
  "id": "7a4f9e10-2b3c-4d5e-8f60-1a2b3c4d5e6f",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "descripcion": "Reparar sistema de ventilación",
  "fechaHora": "2026-10-05T10:20:00-05:00",
  "tecnicoId": "tecnico-01",
  "estado": "PENDIENTE",
  "severidad": "Alta",
  "tipo": "Ventilación"
}
```

### GET `/api/v1/alertas-mantenimiento` — Listar alertas de mantenimiento

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `AlertaMantenimientoPaginaResponse` |
| Éxito | 200 OK |

**Path params**: ninguno.

**Query params**

| Nombre | Tipo | Obligatorio | Por defecto | Regla |
|---|---|---|---|---|
| estado | string | no | `PENDIENTE` | `PENDIENTE`, `ATENDIDA` o `ELIMINADA` |
| galponId | UUID | no | — | Filtra por galpón |
| page | integer | no | 1 | Mayor o igual a 1 |
| size | integer | no | 20 | Entre 1 y 100 |

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| contenido | array | Lista de `AlertaMantenimientoResponse`, más recientes primero. |
| pagina | integer | Página actual (desde 1). |
| tamanio | integer | Elementos por página. |
| totalElementos | integer | Total de resultados. |
| totalPaginas | integer | Total de páginas. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `estado`, `galponId`, `page` o `size` inválidos |

Sirve como bandeja del administrador (PENDIENTE) y como historial (FR-010).

### PATCH `/api/v1/alertas-mantenimiento/{alertaId}` — Atender alerta de mantenimiento

| Elemento | Definición |
|---|---|
| Request DTO | `AtenderAlertaRequest` |
| Response DTO | `AtenderAlertaResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| alertaId | UUID | sí | Debe ser un UUID válido |

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| estado | string | sí | Solo se acepta `ATENDIDA` |

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| alerta | object | `AlertaMantenimientoResponse` con estado `ATENDIDA`. |
| galpon | object | `{id, estado}` con el estado nuevo, `MANTENIMIENTO`. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `ESTADO_INVALIDO` | `estado` distinto de `ATENDIDA` |
| 404 | `ALERTA_NO_ENCONTRADA` | La alerta no existe |
| 409 | `ALERTA_NO_PENDIENTE` | La alerta ya no está PENDIENTE |
| 409 | `ESTADO_ESPERADO_CAMBIADO` | El galpón ya no está DISPONIBLE al atender |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |

Marcar la alerta ATENDIDA y solicitar DISPONIBLE -> MANTENIMIENTO (origen `ADMINISTRADOR`) ocurre en la misma transacción.

Ejemplo de request:

```json
{
  "estado": "ATENDIDA"
}
```

Ejemplo de response:

```json
{
  "alerta": {
    "id": "7a4f9e10-2b3c-4d5e-8f60-1a2b3c4d5e6f",
    "estado": "ATENDIDA"
  },
  "galpon": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "estado": "MANTENIMIENTO"
  }
}
```

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `DESCRIPCION_INVALIDA` | Descripción inválida | La descripción es obligatoria y tiene máximo 500 caracteres. |
| `GALPON_REQUERIDO` | Galpón requerido | Debes seleccionar un galpón. |
| `TECNICO_REQUERIDO` | Técnico requerido | Debes indicar el técnico que genera la alerta. |
| `GALPON_NO_ENCONTRADO` | Galpón no encontrado | El galpón seleccionado no existe. |
| `GALPON_NO_DISPONIBLE` | Galpón no disponible | Solo se pueden generar alertas para galpones disponibles. |
| `ALERTA_ACTIVA_EXISTENTE` | Alerta activa existente | Ya existe una alerta activa para ese galpón. |
| `ALERTA_NO_ENCONTRADA` | Alerta no encontrada | La alerta no existe. |
| `ALERTA_NO_PENDIENTE` | Alerta no pendiente | La alerta ya no está pendiente. |
| `ESTADO_INVALIDO` | Estado inválido | Solo se puede cambiar el estado de la alerta a ATENDIDA. |
| `ESTADO_ESPERADO_CAMBIADO` | Estado desactualizado | El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo. |
| `PARAMETRO_INVALIDO` | Parámetro inválido | Revisa los parámetros de la solicitud. |
| `VALIDACION_FALLIDA` | Validación fallida | Hay campos con datos inválidos. Revisa fieldErrors. |
| `CUERPO_INVALIDO` | Cuerpo inválido | El cuerpo de la solicitud no es un JSON válido. |

Ejemplo `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/alerta-activa-existente",
  "title": "Alerta activa existente",
  "status": 409,
  "detail": "Ya existe una alerta activa para ese galpón.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "ALERTA_ACTIVA_EXISTENTE"
}
```

## Peticiones y respuestas por escenario

Los números de escenario son los del spec. `POST` y `PATCH` reciben `application/json`; los `GET` no tienen body y llevan `Cache-Control: no-store`.

**1. `200` — Escenario 2: el formulario solo lista galpones DISPONIBLES**

- **Petición**: `GET /api/v1/galpones?estado=DISPONIBLE`
- **Path params**: ninguno
- **Query params**: `estado=DISPONIBLE`
- **Body**: sin body
- **Nota**: "Galpón Norte" está `PRODUCTIVO` y no aparece. Servicio del plan de consultar galpón y lote.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "nombre": "Galpón Sur",
      "capacidad": 2000,
      "estado": "DISPONIBLE"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 1,
  "totalPaginas": 1,
  "sinCoincidencias": false
}
```

**2. `201` — Escenario 1: generación exitosa**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: La fecha y hora las pone el servidor; la alerta nace `PENDIENTE` y el galpón sigue `DISPONIBLE`. `severidad` y `tipo` son opcionales.

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `201` — cabeceras: `Location: /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`:

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Reparar sistema de ventilación",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "tecnicoId": "tec-014",
  "estado": "PENDIENTE",
  "severidad": "Alta",
  "tipo": "Ventilación"
}
```

**3. `201` — Alerta sin severidad ni tipo**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: `severidad` y `tipo` se omiten de la respuesta.

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Revisar el techo",
  "tecnicoId": "tec-014"
}
```

Respuesta `201` — cabeceras: `Location: /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`:

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Revisar el techo",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "tecnicoId": "tec-014",
  "estado": "PENDIENTE"
}
```

**4. `409` — Escenario 3: ya existe una alerta pendiente para el galpón**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: También responde así si dos técnicos la generan a la vez (índice parcial único).

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/alerta-activa-existente",
  "title": "Alerta activa existente",
  "status": 409,
  "detail": "Ya existe una alerta activa para ese galpón.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "ALERTA_ACTIVA_EXISTENTE"
}
```

**5. `400` — Escenario 4: descripción vacía**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8", "descripcion": "   ", "severidad": "Alta", "tipo": "Ventilación", "tecnicoId": "tec-014"}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/descripcion-invalida",
  "title": "Descripción inválida",
  "status": 400,
  "detail": "La descripción es obligatoria y tiene máximo 500 caracteres.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "DESCRIPCION_INVALIDA",
  "fieldErrors": [
    {
      "field": "descripcion",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**6. `400` — Descripción de más de 500 caracteres**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/descripcion-invalida",
  "title": "Descripción inválida",
  "status": 400,
  "detail": "La descripción es obligatoria y tiene máximo 500 caracteres.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "DESCRIPCION_INVALIDA",
  "fieldErrors": [
    {
      "field": "descripcion",
      "code": "LONGITUD_INVALIDA",
      "message": "Longitud fuera del rango permitido"
    }
  ]
}
```

**7. `400` — Técnico no indicado**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/tecnico-requerido",
  "title": "Técnico requerido",
  "status": 400,
  "detail": "Debes indicar el técnico que genera la alerta.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "TECNICO_REQUERIDO",
  "fieldErrors": [
    {
      "field": "tecnicoId",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**8. `400` — Galpón no seleccionado**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-requerido",
  "title": "Galpón requerido",
  "status": 400,
  "detail": "Debes seleccionar un galpón.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "GALPON_REQUERIDO",
  "fieldErrors": [
    {
      "field": "galponId",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**9. `404` — Galpón inexistente**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón seleccionado no existe.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**10. `409` — Galpón que no está DISPONIBLE (también si se manipula la solicitud)**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: "Galpón Norte" está `PRODUCTIVO`.

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": "tec-014"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-disponible",
  "title": "Galpón no disponible",
  "status": 409,
  "detail": "Solo se pueden generar alertas para galpones disponibles.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "GALPON_NO_DISPONIBLE"
}
```

**11. `400` — Dos campos inválidos a la vez**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "descripcion": "",
  "severidad": "Alta",
  "tipo": "Ventilación",
  "tecnicoId": ""
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/validacion-fallida",
  "title": "Validación fallida",
  "status": 400,
  "detail": "Hay campos con datos inválidos. Revisa fieldErrors.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "VALIDACION_FALLIDA",
  "fieldErrors": [
    {
      "field": "descripcion",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    },
    {
      "field": "tecnicoId",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**12. `400` — Body que no es un JSON válido**

- **Petición**: `POST /api/v1/alertas-mantenimiento`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8", "descripcion": 
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "CUERPO_INVALIDO"
}
```

**13. `200` — Escenario 6: bandeja del administrador con las alertas pendientes (valor por defecto de `estado`)**

- **Petición**: `GET /api/v1/alertas-mantenimiento?page=1&size=20`
- **Path params**: ninguno
- **Query params**: `page=1`, `size=20`
- **Body**: sin body
- **Nota**: Más recientes primero. Con `estado=ATENDIDA` se listan las atendidas; con `galponId=<uuid>` solo las de ese galpón.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "descripcion": "Reparar sistema de ventilación",
      "fechaHora": "2026-10-05T10:30:00-05:00",
      "tecnicoId": "tec-014",
      "estado": "PENDIENTE",
      "severidad": "Alta",
      "tipo": "Ventilación"
    }
  ],
  "pagina": 1,
  "tamanio": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

**14. `200` — Escenario 6: historial de alertas atendidas (aparte de la bandeja)**

- **Petición**: `GET /api/v1/alertas-mantenimiento?estado=ATENDIDA&page=1&size=20`
- **Path params**: ninguno
- **Query params**: `estado=ATENDIDA`, `page=1`, `size=20`
- **Body**: sin body
- **Nota**: El front hace esta segunda llamada para mostrar el historial sin la acción de atender.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
      "galponId": "550e8400-e29b-41d4-a716-446655440000",
      "descripcion": "Revisar bebederos",
      "fechaHora": "2026-10-03T09:00:00-05:00",
      "tecnicoId": "tec-009",
      "estado": "ATENDIDA"
    }
  ],
  "pagina": 1,
  "tamanio": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

**15. `200` — Bandeja sin alertas**

- **Petición**: `GET /api/v1/alertas-mantenimiento?estado=PENDIENTE&galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Path params**: ninguno
- **Query params**: `estado=PENDIENTE`, `galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Body**: sin body

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [],
  "pagina": 1,
  "tamanio": 20,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

**16. `400` — `estado` fuera de PENDIENTE o ATENDIDA**

- **Petición**: `GET /api/v1/alertas-mantenimiento?estado=ZZZ`
- **Path params**: ninguno
- **Query params**: `estado=ZZZ`
- **Body**: sin body
- **Nota**: El texto de `fieldErrors.message` sale de los valores del enum del código.

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "estado",
      "code": "VALOR_NO_PERMITIDO",
      "message": "Debe ser uno de: PENDIENTE, ATENDIDA, ELIMINADA"
    }
  ]
}
```

**17. `400` — `galponId` que no es un UUID**

- **Petición**: `GET /api/v1/alertas-mantenimiento?galponId=abc`
- **Path params**: ninguno
- **Query params**: `galponId=abc`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/alertas-mantenimiento",
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

**18. `400` — `size` fuera de 1 a 100**

- **Petición**: `GET /api/v1/alertas-mantenimiento?size=0`
- **Path params**: ninguno
- **Query params**: `size=0`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/alertas-mantenimiento",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "size",
      "code": "FUERA_DE_RANGO",
      "message": "Debe estar entre 1 y 100"
    }
  ]
}
```

**19. `200` — Atender la alerta (el galpón pasa a MANTENIMIENTO)**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Path params**: `alertaId=7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: En la misma transacción se solicita `DISPONIBLE -> MANTENIMIENTO` (origen `ADMINISTRADOR`) al servicio de estado.

Body de la petición:

```json
{
  "estado": "ATENDIDA"
}
```

Respuesta `200`:

```json
{
  "alerta": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "estado": "ATENDIDA"
  },
  "galpon": {
    "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
    "estado": "MANTENIMIENTO"
  }
}
```

**20. `404` — Alerta inexistente**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Path params**: `alertaId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estado": "ATENDIDA"
}
```

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/alerta-no-encontrada",
  "title": "Alerta no encontrada",
  "status": 404,
  "detail": "La alerta no existe.",
  "instance": "/api/v1/alertas-mantenimiento/d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "code": "ALERTA_NO_ENCONTRADA"
}
```

**21. `409` — La alerta ya no está pendiente**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Path params**: `alertaId=7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estado": "ATENDIDA"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/alerta-no-pendiente",
  "title": "Alerta no pendiente",
  "status": 409,
  "detail": "La alerta ya no está pendiente.",
  "instance": "/api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "code": "ALERTA_NO_PENDIENTE"
}
```

**22. `409` — El galpón ya no está DISPONIBLE al atender (por ejemplo se registró un lote)**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Path params**: `alertaId=7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: La alerta sigue `PENDIENTE` (P-21).

Body de la petición:

```json
{
  "estado": "ATENDIDA"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-esperado-cambiado",
  "title": "Estado desactualizado",
  "status": 409,
  "detail": "El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo.",
  "instance": "/api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "code": "ESTADO_ESPERADO_CAMBIADO"
}
```

**23. `400` — Estado distinto de ATENDIDA**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Path params**: `alertaId=7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estado": "ELIMINADA"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-invalido",
  "title": "Estado inválido",
  "status": 400,
  "detail": "Solo se puede cambiar el estado de la alerta a ATENDIDA.",
  "instance": "/api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "code": "ESTADO_INVALIDO",
  "fieldErrors": [
    {
      "field": "estado",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

**24. `400` — `alertaId` que no es un UUID**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/abc`
- **Path params**: `alertaId=abc`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estado": "ATENDIDA"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/alertas-mantenimiento/abc",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "alertaId",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

**25. `400` — Body que no es un JSON válido**

- **Petición**: `PATCH /api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Path params**: `alertaId=7c9e6679-7425-40de-944b-e07fc1f90ae7`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"estado": 
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/alertas-mantenimiento/7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "code": "CUERPO_INVALIDO"
}
```

**Escenario 5: cancelar la generación.** Es del cliente: al elegir "Cancelar" no se envía ninguna petición y no se crea la alerta (no hay request ni response).

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/AlertaMantenimientoUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Pagina;
import java.util.UUID;

public interface AlertaMantenimientoUseCase {

    AlertaMantenimiento generar(Comando comando);

    ResultadoAtencion atender(UUID alertaId);

    Pagina<AlertaMantenimiento> listar(EstadoAlertaMantenimiento estado, UUID galponId, int pagina, int tamanio);

    record Comando(UUID galponId, String descripcion, String severidad, String tipo, String tecnicoId) {
    }

    record ResultadoAtencion(AlertaMantenimiento alerta, UUID galponId, EstadoGalpon estadoGalpon) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/GenerarAlertaMantenimientoRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GenerarAlertaMantenimientoRequest(
        @NotNull UUID galponId,
        @NotBlank @Size(max = 500) String descripcion,
        String severidad,
        String tipo,
        @NotBlank String tecnicoId) {

    public GenerarAlertaMantenimientoRequest {
        descripcion = descripcion == null ? null : descripcion.strip();
        tecnicoId = tecnicoId == null ? null : tecnicoId.strip();
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/AlertaMantenimientoResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import java.time.OffsetDateTime;
import java.util.UUID;

/** `severidad` y `tipo` se omiten cuando no se informaron. */
public record AlertaMantenimientoResponse(UUID id, UUID galponId, String descripcion, OffsetDateTime fechaHora,
                                          String tecnicoId, EstadoAlertaMantenimiento estado, String severidad,
                                          String tipo) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/AlertaMantenimientoPaginaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.util.List;

public record AlertaMantenimientoPaginaResponse(List<AlertaMantenimientoResponse> contenido, int pagina, int tamanio,
                                                long totalElementos, int totalPaginas) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/AtenderAlertaRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Solo se acepta `ATENDIDA`: ELIMINADA existe en el modelo pero ningún spec define su flujo. */
public record AtenderAlertaRequest(@NotNull @Pattern(regexp = "ATENDIDA") String estado) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/AtenderAlertaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.UUID;

public record AtenderAlertaResponse(AlertaEstado alerta, GalponEstado galpon) {

    public record AlertaEstado(UUID id, EstadoAlertaMantenimiento estado) {
    }

    public record GalponEstado(UUID id, EstadoGalpon estado) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/AlertaMantenimientoRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.application.port.in.AlertaMantenimientoUseCase.ResultadoAtencion;
import com.unimag.avicontrol.domain.model.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.model.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AlertaMantenimientoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AlertaMantenimientoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AtenderAlertaResponse;

public final class AlertaMantenimientoRestMapper {

    private AlertaMantenimientoRestMapper() {
    }

    public static AlertaMantenimientoResponse aResponse(AlertaMantenimiento a) {
        return new AlertaMantenimientoResponse(a.id(), a.galponId(), a.descripcion(),
                a.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime(), a.tecnicoId(), a.estado(),
                a.severidad(), a.tipo());
    }

    public static AlertaMantenimientoPaginaResponse aPagina(Pagina<AlertaMantenimiento> p) {
        return new AlertaMantenimientoPaginaResponse(
                p.contenido().stream().map(AlertaMantenimientoRestMapper::aResponse).toList(),
                p.pagina(), p.tamanio(), p.totalElementos(), p.totalPaginas());
    }

    public static AtenderAlertaResponse aAtencion(ResultadoAtencion r) {
        return new AtenderAlertaResponse(
                new AtenderAlertaResponse.AlertaEstado(r.alerta().id(), r.alerta().estado()),
                new AtenderAlertaResponse.GalponEstado(r.galponId(), r.estadoGalpon()));
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/AlertaMantenimientoService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.AlertaMantenimientoUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.AlertaMantenimientoRepository;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertaMantenimientoService implements AlertaMantenimientoUseCase {

    private static final int DESCRIPCION_MAXIMA = 500;

    private final AlertaMantenimientoRepository alertas;
    private final GalponRepository galpones;
    private final GalponEstadoService estados;
    private final Clock clock;

    public AlertaMantenimientoService(AlertaMantenimientoRepository alertas, GalponRepository galpones,
                                      GalponEstadoService estados, Clock clock) {
        this.alertas = alertas;
        this.galpones = galpones;
        this.estados = estados;
        this.clock = clock;
    }

    /** No cambia el estado del galpón (FR-008). La fecha y hora las pone el servidor (FR-005). */
    @Override
    @Transactional
    public AlertaMantenimiento generar(Comando comando) {
        String descripcion = comando.descripcion() == null ? "" : comando.descripcion().strip();
        if (descripcion.isEmpty() || descripcion.length() > DESCRIPCION_MAXIMA) {
            throw new ValidacionNegocioException("DESCRIPCION_INVALIDA",
                    "La descripción es obligatoria y tiene máximo 500 caracteres.");
        }
        if (comando.tecnicoId() == null || comando.tecnicoId().isBlank()) {
            throw new ValidacionNegocioException("TECNICO_REQUERIDO",
                    "Debes indicar el técnico que genera la alerta.");
        }
        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));
        if (galpon.estado() != EstadoGalpon.DISPONIBLE) {
            throw new ConflictoException("GALPON_NO_DISPONIBLE",
                    "Solo se pueden generar alertas para galpones disponibles.");
        }
        if (alertas.existsPendienteByGalponId(galpon.id())) {
            throw new ConflictoException("ALERTA_ACTIVA_EXISTENTE", "Ya existe una alerta activa para ese galpón.");
        }
        // Dos técnicos a la vez: el índice parcial (galpon_id) WHERE estado = 'PENDIENTE' rechaza al segundo y
        // el adaptador de persistencia lo traduce a este mismo ALERTA_ACTIVA_EXISTENTE.
        return alertas.save(AlertaMantenimiento.nueva(galpon.id(), descripcion, comando.tecnicoId().strip(),
                vacioANulo(comando.severidad()), vacioANulo(comando.tipo()), clock.instant()));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** Marca la alerta ATENDIDA y pasa el galpón DISPONIBLE -> MANTENIMIENTO en la misma transacción (FR-008). */
    @Override
    @Transactional
    public ResultadoAtencion atender(UUID alertaId) {
        AlertaMantenimiento alerta = alertas.findById(alertaId).orElseThrow(() ->
                new RecursoNoEncontradoException("ALERTA_NO_ENCONTRADA", "La alerta no existe."));
        if (alerta.estado() != EstadoAlertaMantenimiento.PENDIENTE) {
            throw new ConflictoException("ALERTA_NO_PENDIENTE", "La alerta ya no está pendiente.");
        }
        HistorialEstado cambio;
        try {
            cambio = estados.solicitarCambio(alerta.galponId(), EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                    OrigenCambioEstado.ADMINISTRADOR);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                        "El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo.");
            }
            throw e;
        }
        AlertaMantenimiento atendida = alertas.save(alerta.atendida());
        return new ResultadoAtencion(atendida, alerta.galponId(), cambio.estadoNuevo());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<AlertaMantenimiento> listar(EstadoAlertaMantenimiento estado, UUID galponId, int pagina, int tamanio) {
        return alertas.search(estado, galponId, pagina, tamanio);
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/AlertaMantenimientoController.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest;

import com.unimag.avicontrol.application.port.in.AlertaMantenimientoUseCase;
import com.unimag.avicontrol.domain.model.AlertaMantenimiento;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AlertaMantenimientoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AlertaMantenimientoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AtenderAlertaRequest;
import com.unimag.avicontrol.infrastructure.in.rest.dto.AtenderAlertaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GenerarAlertaMantenimientoRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.AlertaMantenimientoRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alertas-mantenimiento")
public class AlertaMantenimientoController {

    private final AlertaMantenimientoUseCase alertas;

    public AlertaMantenimientoController(AlertaMantenimientoUseCase alertas) {
        this.alertas = alertas;
    }

    @PostMapping
    public ResponseEntity<AlertaMantenimientoResponse> generar(
            @Valid @RequestBody GenerarAlertaMantenimientoRequest request) {
        AlertaMantenimiento alerta = alertas.generar(new AlertaMantenimientoUseCase.Comando(request.galponId(),
                request.descripcion(), request.severidad(), request.tipo(), request.tecnicoId()));
        return ResponseEntity.created(URI.create("/api/v1/alertas-mantenimiento/" + alerta.id()))
                .body(AlertaMantenimientoRestMapper.aResponse(alerta));
    }

    @GetMapping
    public AlertaMantenimientoPaginaResponse listar(@RequestParam(required = false) String estado,
                                                    @RequestParam(required = false) UUID galponId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return AlertaMantenimientoRestMapper.aPagina(alertas.listar(ParametrosConsulta.estadoAlerta(estado), galponId,
                ParametrosConsulta.pagina(page), ParametrosConsulta.tamanio(size)));
    }

    /** Atender la alerta pasa el galpón DISPONIBLE -> MANTENIMIENTO en la misma transacción. */
    @PatchMapping("/{alertaId}")
    public AtenderAlertaResponse atender(@PathVariable UUID alertaId, @Valid @RequestBody AtenderAlertaRequest request) {
        return AlertaMantenimientoRestMapper.aAtencion(alertas.atender(alertaId));
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/repository/AlertaMantenimientoJpaRepository.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.repository;

import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.AlertaMantenimientoEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaMantenimientoJpaRepository extends JpaRepository<AlertaMantenimientoEntity, UUID> {

    boolean existsByGalponIdAndEstado(UUID galponId, EstadoAlertaMantenimiento estado);

    Page<AlertaMantenimientoEntity> findByEstado(EstadoAlertaMantenimiento estado, Pageable pageable);

    Page<AlertaMantenimientoEntity> findByEstadoAndGalponId(EstadoAlertaMantenimiento estado, UUID galponId,
                                                            Pageable pageable);
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/AlertaMantenimientoPersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.AlertaMantenimientoRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.AlertaPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.AlertaMantenimientoJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class AlertaMantenimientoPersistenceAdapter implements AlertaMantenimientoRepository {

    private final AlertaMantenimientoJpaRepository jpa;

    public AlertaMantenimientoPersistenceAdapter(AlertaMantenimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AlertaMantenimiento save(AlertaMantenimiento alerta) {
        try {
            return AlertaPersistenceMapper.aDominio(jpa.saveAndFlush(AlertaPersistenceMapper.aEntidad(alerta)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.ALERTA_PENDIENTE_POR_GALPON)) {
                throw new ConflictoException("ALERTA_ACTIVA_EXISTENTE", "Ya existe una alerta activa para ese galpón.");
            }
            throw e;
        }
    }

    @Override
    public Optional<AlertaMantenimiento> findById(UUID id) {
        return jpa.findById(id).map(AlertaPersistenceMapper::aDominio);
    }

    @Override
    public boolean existsPendienteByGalponId(UUID galponId) {
        return jpa.existsByGalponIdAndEstado(galponId, EstadoAlertaMantenimiento.PENDIENTE);
    }

    @Override
    public Pagina<AlertaMantenimiento> search(EstadoAlertaMantenimiento estado, UUID galponId, int pagina,
                                              int tamanio) {
        // Dos métodos derivados en lugar de un parámetro nulo: un `:galponId is null` obliga a PostgreSQL a
        // inferir el tipo del parámetro y falla con uuid.
        var peticion = PageRequest.of(pagina - 1, tamanio,
                Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
        var resultado = galponId == null
                ? jpa.findByEstado(estado, peticion)
                : jpa.findByEstadoAndGalponId(estado, galponId, peticion);
        return new Pagina<>(resultado.getContent().stream().map(AlertaPersistenceMapper::aDominio).toList(),
                pagina, tamanio, resultado.getTotalElements());
    }
}
```

## Fuera de alcance

- No cambia el estado del galpón al generar la alerta (FR-008).
- El estado ELIMINADA existe en la entidad pero el spec no define quién ni cómo elimina una alerta; queda sin flujo en este plan.
- El técnico no consulta sus alertas anteriores; el historial es del administrador.
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `alerta-mantenimiento-request.json` | El request del `POST` |
| `alerta-mantenimiento-response.json` | El response `201` |
| `alerta-mantenimiento-lista-response.json` | Una página de la bandeja |
| `alerta-atender-request.json` | El request del `PATCH` |
| `alerta-atender-response.json` | El response del `PATCH` |
| `error-alerta-activa-existente.json` | El `409` de ejemplo |

## Tareas

**Goal**: El tecnico reporta una falla en un galpon Disponible; el administrador la atiende y el galpon pasa a Mantenimiento.

**Independent Test**: tecnico crea alerta Pendiente sin cambiar estado; segunda alerta para el mismo galpon devuelve 409; al atender la alerta el galpon queda en MANTENIMIENTO.

### Tests

- [ ] T069 [P] [US8] Prueba de contrato comparando contra los fixtures `alerta-*.json` y `error-alerta-activa-existente.json`: crear alerta de mantenimiento, listar alertas pendientes y atendidas por separado (bandeja e historial, escenario 6), atender alerta — clase `AlertaMantenimientoControllerTest` (FR-001 a FR-004, FR-006, FR-008, FR-010, FR-011; SC-002, SC-003)
- [ ] T070 [P] [US8] Prueba de persistencia: indice parcial impide dos alertas pendientes para el mismo galpon en concurrencia — clase `AlertaMantenimientoPersistenceTest` (FR-006; SC-003)
- [ ] T097 [P] [US8] Pruebas unitarias de `AlertaMantenimientoService` (Mockito) en test/application/: descripción de solo espacios rechazada; 501 caracteres rechazados; galpón no DISPONIBLE rechazado; segunda alerta pendiente rechazada; atender marca ATENDIDA y llama a `GalponEstadoService` con origen ADMINISTRADOR; si el cambio de estado se rechaza la alerta no queda ATENDIDA — clase `AlertaMantenimientoServiceTest` (FR-003, FR-004, FR-006, FR-008; SC-002, SC-003)

### Implementation

- [ ] T071 [P] [US8] Crear GenerarAlertaMantenimientoRequest, AlertaMantenimientoResponse, AtenderAlertaRequest, AtenderAlertaResponse, AlertaMantenimientoPaginaResponse en main/infrastructure/in/rest/dto/ y AlertaMantenimientoRestMapper en main/infrastructure/in/rest/mapper/ (FR-004, FR-005)
- [ ] T072 [P] [US8] Crear AlertaMantenimientoUseCase en main/application/port/in/
- [ ] T073 [US8] Implementar AlertaMantenimientoService.generar y AlertaMantenimientoService.atender en main/application/usecase/: generar valida galpon Disponible, descripcion no vacia hasta 500 caracteres, una sola alerta pendiente por galpon; atender marca la alerta ATENDIDA y solicita DISPONIBLE -> MANTENIMIENTO con origen ADMINISTRADOR (FR-002 a FR-006, FR-008, FR-010)
- [ ] T074 [US8] Implementar AlertaMantenimientoController en main/infrastructure/in/rest/: POST /api/v1/alertas-mantenimiento, GET /api/v1/alertas-mantenimiento y PATCH /api/v1/alertas-mantenimiento/{alertaId} (FR-001, FR-010)

**Checkpoint**: todas las transiciones de la matriz tienen su camino implementado.

## Dependencias

- Solo del plan general (Foundational).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-14: identificar al técnico (spec: "la asocia al galpón y al técnico"). Sin autenticación se acepta `tecnicoId` en el body de forma provisional.
- P-15: valores de `severidad` y `tipo`; el spec solo da ejemplos y se tratan como texto libre opcional.
- P-21: qué hacer con una alerta PENDIENTE cuando el galpón deja de estar DISPONIBLE (por ejemplo, se registra un lote antes de atenderla).
- P-04: confirmar si el Operario puede ver la bandeja de alertas.
