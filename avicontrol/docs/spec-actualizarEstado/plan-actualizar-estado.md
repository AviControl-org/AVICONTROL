# Implementation Plan: Actualizar estado del galpón

**Fecha**: 2026-10-02
**Spec**: [actualizar-estados-spec.md](actualizar-estados-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US4, prioridad P1)

## Summary

El administrador cambia el estado de un galpón por las transiciones que le corresponden; cada cambio queda en el historial de estados.

**Enfoque técnico:**

1. Tres servicios REST: `GET .../transiciones` (qué puede pedir el administrador desde el estado actual), `PATCH .../estado` y `GET .../historial-estados`.
2. `GalponService` valida que el destino sea una de las transiciones del administrador y delega en `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, ADMINISTRADOR)`. No escribe el estado por sí mismo (FR-012).
3. `GalponEstadoService` (T016) bloquea el galpón (`findByIdForUpdate`), compara el estado vigente con `estadoEsperado` (FR-013), valida la matriz y el origen (FR-003 a FR-005), persiste el cambio y escribe el historial en la misma transacción (FR-008).
4. Los demás specs (registrar lote, alertas sanitarias, vaciado, mantenimiento) llaman al mismo servicio con su propio origen; solo cambia quién pide la transición.

## Contexto específico

- **Tareas previas del plan general**: T015 (`TransicionesPermitidas`), T016 (`GalponEstadoService`), T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/ActualizarEstadoUseCase.java             # T045
│   └── usecase/GalponService.java                        # T046 (transiciones del administrador)
└── infrastructure/in/rest/
    ├── GalponController.java                             # T047 (GET transiciones, PATCH estado, GET historial)
    └── dto/
        ├── ActualizarEstadoRequest.java                  # T044
        ├── CambioEstadoResponse.java                     # T044
        ├── TransicionesDisponiblesResponse.java          # T044
        ├── HistorialEstadoResponse.java                  # T044
        └── HistorialEstadoPaginaResponse.java            # T044

src/test/java/com/unimag/avicontrol/
├── application/GalponServiceEstadoTest.java              # T093
└── infrastructure/in/rest/GalponControllerTest.java      # T043
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `TransicionesPermitidas.java`, `GalponEstadoService.java`, `GalponRestMapper.java`, `HistorialPersistenceAdapter.java`. Están en la sección *Código de implementación*.

`TransicionesPermitidas` (T015) y `GalponEstadoService` (T016) ya existen por el plan general; sus pruebas son T017.

## Decisiones de diseño

**Orden de validación en `GalponEstadoService`.** Galpón existe (`404`) -> `estadoEsperado` igual al estado vigente (`409 ESTADO_ESPERADO_CAMBIADO`, FR-013) -> transición en la matriz (`409 TRANSICION_NO_PERMITIDA`; incluye AISLAMIENTO -> VACIADO_SANITARIO, FR-005) -> origen autorizado (`409 ORIGEN_NO_AUTORIZADO`; incluye DISPONIBLE -> PRODUCTIVO manual, FR-004).

**La matriz es un dato.** `TransicionesPermitidas` guarda, por cada transición, el conjunto de orígenes autorizados. `GET .../transiciones` es esa misma matriz filtrada por el origen `ADMINISTRADOR`, así que el cliente nunca ve una opción que el `PATCH` vaya a rechazar.

**DISPONIBLE -> MANTENIMIENTO no va por este `PATCH`.** Solo se entra atendiendo una alerta de mantenimiento (spec Generar alerta), para que exista la alerta que lo justifica. Pedirla aquí responde `ORIGEN_NO_AUTORIZADO`.

**Atomicidad del historial.** El cambio y su fila de `HistorialEstado` se guardan en una sola transacción; si falla el historial, el estado no cambia (FR-008, SC-004).

**Historial paginado.** `page` empieza en 1, `size` por defecto 20 (máximo 100), del más reciente al más antiguo. El usuario que hizo el cambio no se guarda porque la autenticación no está especificada (P-01).

**Confirmación.** El mensaje "¿Estás seguro de actualizar el estado?" (FR-006) es del cliente; la API recibe la solicitud ya confirmada. Esto incluye la atención de una alerta de mantenimiento: el `PATCH /alertas-mantenimiento/{alertaId}` también llega ya confirmado.

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### GET `/api/v1/galpones/{galponId}/transiciones` — Transiciones disponibles para el administrador

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `TransicionesDisponiblesResponse` |
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
| estadoActual | string | Uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO. |
| destinosPermitidos | array | Estados destino que el administrador puede solicitar desde el estado actual. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `galponId` no es un UUID |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |

Ejemplo de response:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoActual": "PRODUCTIVO",
  "destinosPermitidos": [
    "EN_COSECHA"
  ]
}
```

### PATCH `/api/v1/galpones/{galponId}/estado` — Actualizar el estado del galpón

| Elemento | Definición |
|---|---|
| Request DTO | `ActualizarEstadoRequest` |
| Response DTO | `CambioEstadoResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe ser un UUID válido |

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| estadoEsperado | string | sí | Estado que el cliente cree vigente; uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO |
| estadoDestino | string | sí | Estado solicitado; uno de los mismos valores |

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| galponId | UUID | Galpón actualizado. |
| estadoAnterior | string | Estado antes del cambio. |
| estadoNuevo | string | Estado después del cambio. |
| origen | string | `ADMINISTRADOR` para este servicio. |
| fechaHora | datetime | Momento del cambio. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `ESTADO_INVALIDO` | `estadoEsperado` o `estadoDestino` fuera de los seis valores |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |
| 409 | `TRANSICION_NO_PERMITIDA` | Transición fuera de la matriz, incluida AISLAMIENTO -> VACIADO_SANITARIO |
| 409 | `ORIGEN_NO_AUTORIZADO` | El administrador no puede solicitar esa transición, por ejemplo DISPONIBLE -> PRODUCTIVO manual |
| 409 | `ESTADO_ESPERADO_CAMBIADO` | El estado vigente ya no es `estadoEsperado` (solicitudes simultáneas) |
| 400 | `VALIDACION_FALLIDA` | Dos o más campos del body inválidos a la vez (el detalle va en `fieldErrors`) |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |

El estado vigente se valida dentro de la misma transacción que persiste el cambio (bloqueo pesimista por galpón, T016).

Ejemplo de request:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "EN_COSECHA"
}
```

Ejemplo de response:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "PRODUCTIVO",
  "estadoNuevo": "EN_COSECHA",
  "origen": "ADMINISTRADOR",
  "fechaHora": "2026-10-05T10:30:00-05:00"
}
```

### GET `/api/v1/galpones/{galponId}/historial-estados` — Historial de cambios de estado

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `HistorialEstadoPaginaResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe ser un UUID válido |

**Query params**

| Nombre | Tipo | Obligatorio | Por defecto | Regla |
|---|---|---|---|---|
| page | integer | no | 1 | Mayor o igual a 1 |
| size | integer | no | 20 | Entre 1 y 100 |

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| contenido | array | Lista de `{id, galponId, estadoAnterior, estadoNuevo, fechaHora, origen}`, del más reciente al más antiguo. |
| pagina | integer | Página actual (desde 1). |
| tamanio | integer | Elementos por página. |
| totalElementos | integer | Total de resultados. |
| totalPaginas | integer | Total de páginas. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `galponId`, `page` o `size` inválidos |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |

El usuario que hizo el cambio se omite porque la autenticación no está especificada (FR-008).

### Matriz de transiciones (FR-002)

| Desde | Hacia | Quién la solicita | Origen registrado | Por REST |
|---|---|---|---|---|
| DISPONIBLE | PRODUCTIVO | Registrar lote | `REGISTRO_LOTE` | No |
| DISPONIBLE | MANTENIMIENTO | Administrador al atender alerta | `ADMINISTRADOR` | Vía `PATCH` de la alerta |
| PRODUCTIVO | EN_COSECHA | Administrador | `ADMINISTRADOR` | Sí |
| PRODUCTIVO | AISLAMIENTO | Alerta sanitaria (Módulo 2) | `ALERTA_SANITARIA_MODULO_2` | No |
| EN_COSECHA | VACIADO_SANITARIO | Recibir vaciado sanitario | `ALERTA_VACIADO_SANITARIO` | No |
| VACIADO_SANITARIO | DISPONIBLE | Proceso automático | `PROCESO_AUTOMATICO` | No |
| MANTENIMIENTO | DISPONIBLE | Administrador | `ADMINISTRADOR` | Sí |
| AISLAMIENTO | PRODUCTIVO | Alerta sanitaria de reanudación | `ALERTA_SANITARIA_MODULO_2` | No |
| AISLAMIENTO | EN_COSECHA | Administrador | `ADMINISTRADOR` | Sí |

Las transiciones que no van por REST entran por el puerto interno `solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)` de `GalponEstadoService`.

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `ESTADO_INVALIDO` | Estado inválido | El estado indicado no es válido. Usa uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO. |
| `GALPON_NO_ENCONTRADO` | Galpón no encontrado | El galpón no existe. |
| `TRANSICION_NO_PERMITIDA` | Transición no permitida | No se puede pasar de ese estado al estado solicitado. |
| `ORIGEN_NO_AUTORIZADO` | Origen no autorizado | El administrador no puede solicitar esa transición por este servicio. |
| `ESTADO_ESPERADO_CAMBIADO` | Estado desactualizado | El estado del galpón cambió. Actualiza la información e inténtalo de nuevo. |
| `PARAMETRO_INVALIDO` | Parámetro inválido | Revisa los parámetros de la solicitud. |
| `VALIDACION_FALLIDA` | Validación fallida | Hay campos con datos inválidos. Revisa fieldErrors. |
| `CUERPO_INVALIDO` | Cuerpo inválido | El cuerpo de la solicitud no es un JSON válido. |

Ejemplo `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-esperado-cambiado",
  "title": "Estado desactualizado",
  "status": 409,
  "detail": "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "ESTADO_ESPERADO_CAMBIADO"
}
```

## Peticiones y respuestas por escenario

Los números de escenario son los del spec. Primero los `GET` y el `PATCH` que expone la API; después las solicitudes internas que otros casos de uso remiten a este spec.

**1. `200` — Transiciones disponibles con el galpón en PRODUCTIVO**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/transiciones`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: Solo las transiciones que el administrador puede pedir por REST.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoActual": "PRODUCTIVO",
  "destinosPermitidos": [
    "EN_COSECHA"
  ]
}
```

**2. `200` — Transiciones disponibles con el galpón en AISLAMIENTO**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/transiciones`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: Solo las transiciones que el administrador puede pedir por REST.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoActual": "AISLAMIENTO",
  "destinosPermitidos": [
    "EN_COSECHA"
  ]
}
```

**3. `200` — Transiciones disponibles con el galpón en MANTENIMIENTO**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/transiciones`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: Solo las transiciones que el administrador puede pedir por REST.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoActual": "MANTENIMIENTO",
  "destinosPermitidos": [
    "DISPONIBLE"
  ]
}
```

**4. `200` — Transiciones disponibles con el galpón en DISPONIBLE**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/transiciones`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: sin body
- **Nota**: Sin salidas manuales: los cambios desde este estado los piden otros casos de uso (atender alerta, vaciado, job).

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoActual": "DISPONIBLE",
  "destinosPermitidos": []
}
```

**5. `404` — Transiciones de un galpón inexistente**

- **Petición**: `GET /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/transiciones`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/transiciones",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**6. `400` — Transiciones con `galponId` que no es un UUID**

- **Petición**: `GET /api/v1/galpones/abc/transiciones`
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
  "instance": "/api/v1/galpones/abc/transiciones",
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

**7. `200` — Escenario 3: PRODUCTIVO -> EN_COSECHA**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Queda una fila en el historial de estados.

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "EN_COSECHA"
}
```

Respuesta `200`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "PRODUCTIVO",
  "estadoNuevo": "EN_COSECHA",
  "origen": "ADMINISTRADOR",
  "fechaHora": "2026-10-05T09:15:00-05:00"
}
```

**8. `200` — Escenario 6: retomar la cosecha desde AISLAMIENTO**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "AISLAMIENTO",
  "estadoDestino": "EN_COSECHA"
}
```

Respuesta `200`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "AISLAMIENTO",
  "estadoNuevo": "EN_COSECHA",
  "origen": "ADMINISTRADOR",
  "fechaHora": "2026-10-05T09:20:00-05:00"
}
```

**9. `200` — Escenario 8: MANTENIMIENTO -> DISPONIBLE**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "MANTENIMIENTO",
  "estadoDestino": "DISPONIBLE"
}
```

Respuesta `200`:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "MANTENIMIENTO",
  "estadoNuevo": "DISPONIBLE",
  "origen": "ADMINISTRADOR",
  "fechaHora": "2026-10-05T09:25:00-05:00"
}
```

**10. `409` — Transición fuera de la matriz (por ejemplo AISLAMIENTO -> VACIADO_SANITARIO)**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: El galpón conserva su estado y no se escribe historial.

Body de la petición:

```json
{
  "estadoEsperado": "AISLAMIENTO",
  "estadoDestino": "VACIADO_SANITARIO"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/transicion-no-permitida",
  "title": "Transición no permitida",
  "status": 409,
  "detail": "No se puede pasar de ese estado al estado solicitado.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "TRANSICION_NO_PERMITIDA"
}
```

**11. `409` — Transición que existe pero el administrador no puede pedir (DISPONIBLE -> PRODUCTIVO)**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Esa transición solo la pide el registro de un lote.

Body de la petición:

```json
{
  "estadoEsperado": "DISPONIBLE",
  "estadoDestino": "PRODUCTIVO"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/origen-no-autorizado",
  "title": "Origen no autorizado",
  "status": 409,
  "detail": "El administrador no puede solicitar esa transición por este servicio.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "ORIGEN_NO_AUTORIZADO"
}
```

**12. `409` — El estado vigente ya no es el que el administrador veía (solicitudes simultáneas)**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "EN_COSECHA"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-esperado-cambiado",
  "title": "Estado desactualizado",
  "status": 409,
  "detail": "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "ESTADO_ESPERADO_CAMBIADO"
}
```

**13. `400` — Estado fuera de los seis valores**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "ACTIVO"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-invalido",
  "title": "Estado inválido",
  "status": 400,
  "detail": "El estado indicado no es válido. Usa uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "ESTADO_INVALIDO",
  "fieldErrors": [
    {
      "field": "estadoDestino",
      "code": "VALOR_NO_PERMITIDO",
      "message": "Valor no permitido"
    }
  ]
}
```

**14. `400` — Estado ausente**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/estado-invalido",
  "title": "Estado inválido",
  "status": 400,
  "detail": "El estado indicado no es válido. Usa uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "ESTADO_INVALIDO",
  "fieldErrors": [
    {
      "field": "estadoDestino",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**15. `400` — Los dos estados inválidos a la vez**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "ACTIVO",
  "estadoDestino": "INACTIVO"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/validacion-fallida",
  "title": "Validación fallida",
  "status": 400,
  "detail": "Hay campos con datos inválidos. Revisa fieldErrors.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "VALIDACION_FALLIDA",
  "fieldErrors": [
    {
      "field": "estadoEsperado",
      "code": "VALOR_NO_PERMITIDO",
      "message": "Valor no permitido"
    },
    {
      "field": "estadoDestino",
      "code": "VALOR_NO_PERMITIDO",
      "message": "Valor no permitido"
    }
  ]
}
```

**16. `400` — Body que no es un JSON válido**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"estadoEsperado": "PRODUCTIVO"
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/estado",
  "code": "CUERPO_INVALIDO"
}
```

**17. `404` — Galpón inexistente**

- **Petición**: `PATCH /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/estado`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "EN_COSECHA"
}
```

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/estado",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**18. `400` — `galponId` que no es un UUID**

- **Petición**: `PATCH /api/v1/galpones/abc/estado`
- **Path params**: `galponId=abc`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "EN_COSECHA"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones/abc/estado",
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

**Escenario 9: cancelar la actualización.** Es del cliente: al elegir "Cancelar" no se envía ninguna petición y el galpón conserva su estado (no hay request ni response).

#### Solicitudes internas (escenarios 1, 2, 4, 5 y 7)

No son HTTP: las remiten otros casos de uso al mismo punto de entrada que usa el `PATCH` (`GalponEstadoService`), con su propio `origen`. El resultado es la fila que queda en el historial de estados.

**19. Escenario 1: DISPONIBLE -> MANTENIMIENTO al atender una alerta de mantenimiento**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `AlertaMantenimientoService.atender`.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "DISPONIBLE",
  "estadoDestino": "MANTENIMIENTO",
  "origen": "ADMINISTRADOR"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "DISPONIBLE",
  "estadoNuevo": "MANTENIMIENTO",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "ADMINISTRADOR"
}
```

**20. Escenario 2: PRODUCTIVO -> AISLAMIENTO por una alerta sanitaria del Módulo 2**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `AlertaSanitariaService`.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "AISLAMIENTO",
  "origen": "ALERTA_SANITARIA_MODULO_2"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "PRODUCTIVO",
  "estadoNuevo": "AISLAMIENTO",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "ALERTA_SANITARIA_MODULO_2"
}
```

**21. Escenario 4: EN_COSECHA -> VACIADO_SANITARIO por el vaciado sanitario**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `VaciadoSanitarioService.recibir`.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "EN_COSECHA",
  "estadoDestino": "VACIADO_SANITARIO",
  "origen": "ALERTA_VACIADO_SANITARIO"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "EN_COSECHA",
  "estadoNuevo": "VACIADO_SANITARIO",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "ALERTA_VACIADO_SANITARIO"
}
```

**22. Escenario 5: AISLAMIENTO -> PRODUCTIVO por una alerta de reanudación**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `AlertaSanitariaService`.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "AISLAMIENTO",
  "estadoDestino": "PRODUCTIVO",
  "origen": "ALERTA_SANITARIA_MODULO_2"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "AISLAMIENTO",
  "estadoNuevo": "PRODUCTIVO",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "ALERTA_SANITARIA_MODULO_2"
}
```

**23. Escenario 7: VACIADO_SANITARIO -> DISPONIBLE al cumplirse el período**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `VaciadoSanitarioService.finalizarPeriodosCumplidos`, que ejecuta el job.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "VACIADO_SANITARIO",
  "estadoDestino": "DISPONIBLE",
  "origen": "PROCESO_AUTOMATICO"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "VACIADO_SANITARIO",
  "estadoNuevo": "DISPONIBLE",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "PROCESO_AUTOMATICO"
}
```

**24. Registro de un lote: DISPONIBLE -> PRODUCTIVO**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Lo pide `LoteService.registrar`.

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "DISPONIBLE",
  "estadoDestino": "PRODUCTIVO",
  "origen": "REGISTRO_LOTE"
}
```

Resultado (fila de `historial_estado`):

```json
{
  "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoAnterior": "DISPONIBLE",
  "estadoNuevo": "PRODUCTIVO",
  "fechaHora": "2026-10-05T10:30:00-05:00",
  "origen": "REGISTRO_LOTE"
}
```

**25. Solicitud interna rechazada: origen no autorizado**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "PRODUCTIVO",
  "estadoDestino": "AISLAMIENTO",
  "origen": "ADMINISTRADOR"
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ConflictoException",
  "codigo": "ORIGEN_NO_AUTORIZADO",
  "mensaje": "El administrador no puede solicitar esa transición por este servicio."
}
```

**26. Solicitud interna rechazada: el estado cambió entre la lectura y el bloqueo**

- **Llamada**: `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`
- **Nota**: Cada llamador traduce este código al suyo (`GALPON_NO_DISPONIBLE`, `ACCION_INCOMPATIBLE`, `GALPON_NO_EN_COSECHA`).

Argumentos:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "estadoEsperado": "EN_COSECHA",
  "estadoDestino": "VACIADO_SANITARIO",
  "origen": "ALERTA_VACIADO_SANITARIO"
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ConflictoException",
  "codigo": "ESTADO_ESPERADO_CAMBIADO",
  "mensaje": "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo."
}
```

**27. `200` — Historial de cambios de estado, más reciente primero**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados?page=1&size=20`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: `page=1`, `size=20`
- **Body**: sin body
- **Nota**: `size` por defecto 20, máximo 100. `fechaHora` en America/Bogota.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "b6f0c1d4-0e2a-4c6b-9a57-3d1e8f2a7c90",
      "galponId": "550e8400-e29b-41d4-a716-446655440000",
      "estadoAnterior": "PRODUCTIVO",
      "estadoNuevo": "EN_COSECHA",
      "fechaHora": "2026-10-05T09:15:00-05:00",
      "origen": "ADMINISTRADOR"
    },
    {
      "id": "e2a4b7c9-5d1f-4e83-a6b0-9c3d7f1a2b45",
      "galponId": "550e8400-e29b-41d4-a716-446655440000",
      "estadoAnterior": "DISPONIBLE",
      "estadoNuevo": "PRODUCTIVO",
      "fechaHora": "2026-09-15T08:00:00-05:00",
      "origen": "REGISTRO_LOTE"
    }
  ],
  "pagina": 1,
  "tamanio": 20,
  "totalElementos": 2,
  "totalPaginas": 1
}
```

**28. `200` — Historial de un galpón sin cambios de estado todavía**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados`
- **Path params**: `galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8`
- **Query params**: ninguno
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

**29. `404` — Historial de un galpón inexistente**

- **Petición**: `GET /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/historial-estados`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: sin body

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55/historial-estados",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**30. `400` — `size` fuera de 1 a 100**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados?size=0`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: `size=0`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados",
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

**31. `400` — `page` menor que 1**

- **Petición**: `GET /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados?page=0`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: `page=0`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000/historial-estados",
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

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/domain/policy/TransicionesPermitidas.java`**

```java
package com.unimag.avicontrol.domain.policy;

import static com.unimag.avicontrol.domain.model.EstadoGalpon.*;
import static com.unimag.avicontrol.domain.model.OrigenCambioEstado.*;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Matriz de las 9 transiciones del galpón con los orígenes autorizados para cada una (FR-002). */
public final class TransicionesPermitidas {

    public record Transicion(EstadoGalpon desde, EstadoGalpon hacia) {
    }

    private static final Map<Transicion, Set<OrigenCambioEstado>> MATRIZ = Map.of(
            new Transicion(DISPONIBLE, PRODUCTIVO), Set.of(REGISTRO_LOTE),
            new Transicion(DISPONIBLE, MANTENIMIENTO), Set.of(ADMINISTRADOR),
            new Transicion(PRODUCTIVO, EN_COSECHA), Set.of(ADMINISTRADOR),
            new Transicion(PRODUCTIVO, AISLAMIENTO), Set.of(ALERTA_SANITARIA_MODULO_2),
            new Transicion(EN_COSECHA, VACIADO_SANITARIO), Set.of(ALERTA_VACIADO_SANITARIO),
            new Transicion(VACIADO_SANITARIO, DISPONIBLE), Set.of(PROCESO_AUTOMATICO),
            new Transicion(MANTENIMIENTO, DISPONIBLE), Set.of(ADMINISTRADOR),
            new Transicion(AISLAMIENTO, PRODUCTIVO), Set.of(ALERTA_SANITARIA_MODULO_2),
            new Transicion(AISLAMIENTO, EN_COSECHA), Set.of(ADMINISTRADOR));

    /**
     * Transiciones que el administrador pide directamente por REST. DISPONIBLE -> MANTENIMIENTO también es
     * del administrador, pero solo se hace atendiendo una alerta de mantenimiento.
     */
    private static final Set<Transicion> MANUALES = Set.of(
            new Transicion(PRODUCTIVO, EN_COSECHA),
            new Transicion(AISLAMIENTO, EN_COSECHA),
            new Transicion(MANTENIMIENTO, DISPONIBLE));

    private TransicionesPermitidas() {
    }

    public static boolean existe(EstadoGalpon desde, EstadoGalpon hacia) {
        return MATRIZ.containsKey(new Transicion(desde, hacia));
    }

    /** Destinos que el administrador puede pedir por REST desde el estado dado. */
    public static List<EstadoGalpon> destinosManuales(EstadoGalpon desde) {
        return MANUALES.stream()
                .filter(t -> t.desde() == desde)
                .map(Transicion::hacia)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Valida matriz y origen; el orden del spec es: transición existente y luego origen autorizado. */
    public static void validar(EstadoGalpon desde, EstadoGalpon hacia, OrigenCambioEstado origen) {
        Set<OrigenCambioEstado> origenes = MATRIZ.get(new Transicion(desde, hacia));
        if (origenes == null) {
            throw transicionNoPermitida();
        }
        if (!origenes.contains(origen)) {
            throw origenNoAutorizado(origen);
        }
    }

    public static ConflictoException transicionNoPermitida() {
        return new ConflictoException("TRANSICION_NO_PERMITIDA",
                "No se puede pasar de ese estado al estado solicitado.");
    }

    public static ConflictoException origenNoAutorizado(OrigenCambioEstado origen) {
        String detalle = origen == ADMINISTRADOR
                ? "El administrador no puede solicitar esa transición por este servicio."
                : "El origen de la solicitud no está autorizado para esa transición.";
        return new ConflictoException("ORIGEN_NO_AUTORIZADO", detalle);
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/port/in/ActualizarEstadoUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import java.util.List;
import java.util.UUID;

public interface ActualizarEstadoUseCase {

    /** Cambio pedido por el administrador; devuelve la fila de historial que quedó escrita. */
    HistorialEstado actualizarEstado(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino);

    TransicionesDisponibles transiciones(UUID galponId);

    Pagina<HistorialEstado> historial(UUID galponId, int pagina, int tamanio);

    record TransicionesDisponibles(UUID galponId, EstadoGalpon estadoActual, List<EstadoGalpon> destinos) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/GalponEstadoService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.policy.TransicionesPermitidas;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única autoridad que escribe el estado del galpón. Todos los specs que cambian el estado lo piden aquí
 * con su propio origen (FR-012).
 */
@Service
public class GalponEstadoService {

    private final GalponRepository galpones;
    private final HistorialEstadoRepository historial;
    private final Clock clock;

    public GalponEstadoService(GalponRepository galpones, HistorialEstadoRepository historial, Clock clock) {
        this.galpones = galpones;
        this.historial = historial;
        this.clock = clock;
    }

    /**
     * Orden de validación: galpón existe (404) -> el estado vigente es el esperado (409) -> transición en la matriz
     * (409) -> origen autorizado (409). El cambio y su fila de historial se guardan en la misma transacción.
     */
    @Transactional
    public HistorialEstado solicitarCambio(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino,
                                           OrigenCambioEstado origen) {
        Galpon galpon = galpones.findByIdForUpdate(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        if (galpon.estado() != estadoEsperado) {
            throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                    "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.");
        }
        TransicionesPermitidas.validar(galpon.estado(), estadoDestino, origen);

        galpones.save(galpon.conEstado(estadoDestino));
        return historial.save(new HistorialEstado(UUID.randomUUID(), galponId, galpon.estado(), estadoDestino,
                clock.instant(), origen));
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
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.policy.TransicionesPermitidas;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
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
    @Transactional
    public HistorialEstado actualizarEstado(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino) {
        Galpon galpon = galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        if (galpon.estado() != estadoEsperado) {
            throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                    "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.");
        }
        if (!TransicionesPermitidas.destinosManuales(galpon.estado()).contains(estadoDestino)) {
            // Existe en la matriz pero no es del administrador por este servicio -> origen; si no existe -> transición
            throw TransicionesPermitidas.existe(galpon.estado(), estadoDestino)
                    ? TransicionesPermitidas.origenNoAutorizado(OrigenCambioEstado.ADMINISTRADOR)
                    : TransicionesPermitidas.transicionNoPermitida();
        }
        // El estado se escribe solo en GalponEstadoService, que lo vuelve a validar bajo el bloqueo del galpón.
        return estados.solicitarCambio(galponId, estadoEsperado, estadoDestino, OrigenCambioEstado.ADMINISTRADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public TransicionesDisponibles transiciones(UUID galponId) {
        Galpon galpon = galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        return new TransicionesDisponibles(galponId, galpon.estado(),
                TransicionesPermitidas.destinosManuales(galpon.estado()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<HistorialEstado> historial(UUID galponId, int pagina, int tamanio) {
        galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        return historialEstados.findByGalponId(galponId, pagina, tamanio);
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/ActualizarEstadoRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import jakarta.validation.constraints.NotNull;

/** Un valor fuera de los seis estados falla al deserializar y se responde como ESTADO_INVALIDO. */
public record ActualizarEstadoRequest(@NotNull EstadoGalpon estadoEsperado, @NotNull EstadoGalpon estadoDestino) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/CambioEstadoResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CambioEstadoResponse(UUID galponId, EstadoGalpon estadoAnterior, EstadoGalpon estadoNuevo,
                                   OrigenCambioEstado origen, OffsetDateTime fechaHora) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/TransicionesDisponiblesResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.List;
import java.util.UUID;

public record TransicionesDisponiblesResponse(UUID galponId, EstadoGalpon estadoActual,
                                              List<EstadoGalpon> destinosPermitidos) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/HistorialEstadoResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistorialEstadoResponse(UUID id, UUID galponId, EstadoGalpon estadoAnterior, EstadoGalpon estadoNuevo,
                                      OffsetDateTime fechaHora, OrigenCambioEstado origen) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/HistorialEstadoPaginaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.util.List;

public record HistorialEstadoPaginaResponse(List<HistorialEstadoResponse> contenido, int pagina, int tamanio,
                                            long totalElementos, int totalPaginas) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/GalponRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.application.port.in.ActualizarEstadoUseCase.TransicionesDisponibles;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.model.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.in.rest.dto.CambioEstadoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.HistorialEstadoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.HistorialEstadoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.TransicionesDisponiblesResponse;
import java.util.List;

public final class GalponRestMapper {

    private static final String SIN_COINCIDENCIAS =
            "No se encontraron galpones con ese nombre. Se muestran todos los galpones.";
    private static final String SIN_LOTES = "No existen lotes registrados para este galpón";

    private GalponRestMapper() {
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

    public static CambioEstadoResponse aCambio(HistorialEstado h) {
        return new CambioEstadoResponse(h.galponId(), h.estadoAnterior(), h.estadoNuevo(), h.origen(),
                h.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime());
    }

    public static TransicionesDisponiblesResponse aTransiciones(TransicionesDisponibles t) {
        return new TransicionesDisponiblesResponse(t.galponId(), t.estadoActual(), t.destinos());
    }

    public static HistorialEstadoPaginaResponse aHistorialEstados(Pagina<HistorialEstado> p) {
        List<HistorialEstadoResponse> contenido = p.contenido().stream()
                .map(h -> new HistorialEstadoResponse(h.id(), h.galponId(), h.estadoAnterior(), h.estadoNuevo(),
                        h.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime(), h.origen())).toList();
        return new HistorialEstadoPaginaResponse(contenido, p.pagina(), p.tamanio(), p.totalElementos(),
                p.totalPaginas());
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/GalponController.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest;

import com.unimag.avicontrol.application.port.in.ActualizarEstadoUseCase;
import com.unimag.avicontrol.application.port.in.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.EditarGalponUseCase;
import com.unimag.avicontrol.application.port.in.RegistrarGalponUseCase;
import com.unimag.avicontrol.infrastructure.in.rest.dto.ActualizarEstadoRequest;
import com.unimag.avicontrol.infrastructure.in.rest.dto.CambioEstadoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.HistorialEstadoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.TransicionesDisponiblesResponse;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.GalponRestMapper;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/{galponId}/transiciones")
    public TransicionesDisponiblesResponse transiciones(@PathVariable UUID galponId) {
        return GalponRestMapper.aTransiciones(actualizarEstado.transiciones(galponId));
    }

    @PatchMapping("/{galponId}/estado")
    public CambioEstadoResponse actualizarEstado(@PathVariable UUID galponId,
                                                 @Valid @RequestBody ActualizarEstadoRequest request) {
        return GalponRestMapper.aCambio(
                actualizarEstado.actualizarEstado(galponId, request.estadoEsperado(), request.estadoDestino()));
    }

    @GetMapping("/{galponId}/historial-estados")
    public HistorialEstadoPaginaResponse historialEstados(
            @PathVariable UUID galponId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + ParametrosConsulta.TAMANIO_PAGINA_POR_DEFECTO) int size) {
        return GalponRestMapper.aHistorialEstados(
                actualizarEstado.historial(galponId, ParametrosConsulta.pagina(page), ParametrosConsulta.tamanio(size)));
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/HistorialPersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.model.HistorialCambioGalpon;
import com.unimag.avicontrol.domain.model.HistorialEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.HistorialPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.HistorialCambioGalponJpaRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.HistorialEstadoJpaRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Un solo adaptador para los dos historiales: ambos son solo de inserción y consulta. */
@Component
public class HistorialPersistenceAdapter implements HistorialEstadoRepository, HistorialCambioGalponRepository {

    private final HistorialEstadoJpaRepository estados;
    private final HistorialCambioGalponJpaRepository cambios;

    public HistorialPersistenceAdapter(HistorialEstadoJpaRepository estados,
                                       HistorialCambioGalponJpaRepository cambios) {
        this.estados = estados;
        this.cambios = cambios;
    }

    @Override
    public HistorialEstado save(HistorialEstado historial) {
        return HistorialPersistenceMapper.aDominio(
                estados.save(HistorialPersistenceMapper.aEntidad(historial)));
    }

    @Override
    public HistorialCambioGalpon save(HistorialCambioGalpon cambio) {
        return HistorialPersistenceMapper.aDominio(
                cambios.save(HistorialPersistenceMapper.aEntidad(cambio)));
    }

    @Override
    public Pagina<HistorialEstado> findByGalponId(UUID galponId, int pagina, int tamanio) {
        var peticion = PageRequest.of(pagina - 1, tamanio,
                Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
        var resultado = estados.findByGalponId(galponId, peticion);
        return new Pagina<>(resultado.getContent().stream().map(HistorialPersistenceMapper::aDominio).toList(),
                pagina, tamanio, resultado.getTotalElements());
    }
}
```

## Fuera de alcance

- No usa la población actual como condición para pasar a VACIADO_SANITARIO (FR-011); eso lo decide el flujo de vaciado.
- Los demás specs validan y registran su propio evento, pero no escriben el estado (FR-012).
- El mensaje de confirmación del administrador (FR-006), incluida la atención de una alerta de mantenimiento, es del cliente; la API recibe la solicitud ya confirmada.
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `galpon-transiciones-response.json` | Transiciones disponibles para el administrador |
| `galpon-estado-request.json` | El request del `PATCH` |
| `galpon-estado-response.json` | El response del `PATCH` |
| `galpon-historial-estados-response.json` | Una página del historial |
| `error-estado-esperado-cambiado.json` | El `409` de ejemplo |

## Tareas

**Goal**: El administrador cambia el estado de un galpon por las transiciones que le corresponden; el historial registra cada cambio.

**Independent Test**: galpon Productivo pasa a En cosecha con la solicitud de actualizacion; historial muestra origen ADMINISTRADOR; pedir Productivo desde Disponible devuelve 409.

### Tests

- [ ] T043 [P] [US4] Prueba de contrato comparando contra los fixtures `galpon-estado-*.json`, `galpon-transiciones-response.json` y `error-estado-esperado-cambiado.json`: transiciones disponibles para el admin, aplicar transicion, historial de estados — incluida transicion invalida y estado ya cambiado por concurrencia — clase `GalponControllerTest` (FR-001 a FR-005, FR-010, FR-013; SC-002, SC-003)
- [ ] T093 [P] [US4] Pruebas unitarias de `GalponService` (Mockito) en test/application/: las 3 transiciones del administrador llaman a `GalponEstadoService` con origen ADMINISTRADOR; DISPONIBLE -> PRODUCTIVO manual y DISPONIBLE -> MANTENIMIENTO por este servicio dan `ORIGEN_NO_AUTORIZADO`; AISLAMIENTO -> VACIADO_SANITARIO da `TRANSICION_NO_PERMITIDA`; el service no escribe el estado — clase `GalponServiceEstadoTest` (FR-002 a FR-005, FR-012; SC-003)

### Implementation

- [ ] T044 [P] [US4] Crear ActualizarEstadoRequest, CambioEstadoResponse, TransicionesDisponiblesResponse, HistorialEstadoResponse y HistorialEstadoPaginaResponse en main/infrastructure/in/rest/dto/ (FR-001, FR-008)
- [ ] T045 [P] [US4] Crear ActualizarEstadoUseCase en main/application/port/in/
- [ ] T046 [US4] Implementar en GalponService las transiciones del administrador (PRODUCTIVO->EN_COSECHA, AISLAMIENTO->EN_COSECHA, MANTENIMIENTO->DISPONIBLE) llamando a GalponEstadoService con origen ADMINISTRADOR (FR-002, FR-003, FR-012)
- [ ] T047 [US4] Implementar en GalponController: GET /api/v1/galpones/{galponId}/transiciones, PATCH /api/v1/galpones/{galponId}/estado y GET /api/v1/galpones/{galponId}/historial-estados (FR-001, FR-008)

**Checkpoint**: el ciclo de vida manual del galpon funciona.

## Dependencias

- Solo del plan general (Foundational).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-17: el enum `OrigenCambioEstado` (T007) debe incluir `ALERTA_VACIADO_SANITARIO` y `PROCESO_AUTOMATICO`, además de los tres ya previstos.
- P-01: sin autenticación, el historial no guarda qué usuario hizo el cambio.
