# Implementation Plan: Registrar lote

**Fecha**: 2026-10-02
**Spec**: [registrar-lote-spec.md](registrar-lote-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US2, prioridad P1)

## Summary

El administrador registra un lote en un galpón DISPONIBLE. El lote queda con población actual igual a la inicial y el galpón pasa a PRODUCTIVO en la misma transacción.

**Enfoque técnico:**

1. `LoteController` recibe `RegistrarLoteRequest`; Bean Validation rechaza campos ausentes, enteros no positivos y decimales (FR-005 a FR-008).
2. `LoteService.registrar` (una sola transacción) normaliza el nombre con `NombreNormalizado`, valida la fecha contra el `Clock` (America/Bogota), exige que el galpón exista y valida la unicidad del nombre (FR-004).
3. Crea el `Lote` con UUID, `poblacionActual = poblacionInicial` (FR-009, FR-011) y sin `desvinculado_en`, es decir, como lote activo del galpón.
4. Solicita a `GalponEstadoService` DISPONIBLE -> PRODUCTIVO con origen `REGISTRO_LOTE` (FR-010). Si el cambio se rechaza, la excepción revierte la transacción y el lote no queda guardado (FR-012).
5. Responde `201` con `Location` y `LoteResponse`. `GET /lotes` lista todos los lotes registrados (activos y anteriores; la condición la da `desvinculadoEn`), del más reciente al más antiguo.

## Contexto específico

- **Tareas previas del plan general**: T006, T008, T010, T016 (`GalponEstadoService`), T085 (`NombreNormalizado`), T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/RegistrarLoteUseCase.java               # T033
│   └── usecase/LoteService.java                         # T034 (método registrar y listado)
└── infrastructure/
    ├── in/rest/
    │   ├── LoteController.java                          # T035 (POST y GET)
    │   ├── dto/RegistrarLoteRequest.java                # T031
    │   ├── dto/LoteResponse.java                        # T031
    │   ├── dto/LotePaginaResponse.java                  # T031
    │   └── mapper/LoteRestMapper.java                   # T032
    └── out/persistence/mapper/LotePersistenceMapper.java    # T032

src/test/java/com/unimag/avicontrol/
├── application/LoteServiceTest.java                     # T036
└── infrastructure/in/rest/LoteControllerTest.java       # T029
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `ListarLotesUseCase.java`, `LotePersistenceAdapter.java`. Están en la sección *Código de implementación*.

`Lote`, `LoteEntity`, `LoteRepository`, `GalponEstadoService` y `NombreNormalizado` ya existen por el plan general (T008, T010, T016, T085).

## Decisiones de diseño

**Orden de validación.** Formato y enteros del body (`400`, antes de entrar al servicio) -> nombre (`400 NOMBRE_INVALIDO`) -> fecha futura (`400 FECHA_INGRESO_FUTURA`) -> galpón existe (`404`) -> nombre único (`409 NOMBRE_DUPLICADO`) -> galpón DISPONIBLE (`409 GALPON_NO_DISPONIBLE`). El orden es fijo para que la misma solicitud inválida siempre dé el mismo error.

**Carrera por el mismo galpón.** Dos administradores pueden registrar un lote en el mismo galpón a la vez. La solicitud de cambio de estado (`findByIdForUpdate`, T016) la serializa: el segundo recibe `ESTADO_ESPERADO_CAMBIADO` de `GalponEstadoService`, que `LoteService` traduce a `GALPON_NO_DISPONIBLE` para que el cliente vea un solo código (FR-003, FR-014). Todo ocurre en la misma transacción, así que el lote del segundo no se guarda.

**Nombre repetido en carrera.** Igual que en galpones: el índice único de `lower(nombre)` es la defensa final y el adaptador de persistencia traduce su violación a `ConflictoException` (`NOMBRE_DUPLICADO`).

**Lote activo.** Un lote activo es el que tiene `desvinculado_en IS NULL`; el índice parcial único `(galpon_id) WHERE desvinculado_en IS NULL` (T006) impide dos lotes activos en un galpón (P-08).

**Fecha de ingreso.** Puede ser hoy o pasada; "hoy" se evalúa con el `Clock` en America/Bogota (FR-008). No se usa `@PastOrPresent`: esa anotación compara con el reloj del sistema y no con el `Clock` inyectado, así que la regla vive en `LoteService.registrar` (`FECHA_INGRESO_FUTURA`). Si la fecha falta o no tiene el formato `yyyy-MM-dd`, la rechaza el binding del body con `FECHA_INGRESO_INVALIDA`.

**Listado.** `GET /lotes` ordena por `fechaIngreso` descendente y desempata por `id` para que la paginación sea estable.

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### POST `/api/v1/lotes` — Registrar lote

| Elemento | Definición |
|---|---|
| Request DTO | `RegistrarLoteRequest` |
| Response DTO | `LoteResponse` |
| Éxito | 201 Created, cabecera `Location: /api/v1/lotes/{id}` |

**Path params**: ninguno.

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe existir y estar en estado DISPONIBLE |
| nombre | string | sí | 1 a 100 caracteres tras recortar espacios; solo letras, números, espacios, guiones y guiones bajos; único sin distinguir mayúsculas |
| fechaIngreso | date | sí | No puede ser futura (zona America/Bogota); puede ser pasada |
| poblacionInicial | integer | sí | Entero mayor que 0; no acepta decimales ni texto |
| costoTotal | integer (64 bits) | sí | Entero mayor que 0, pesos colombianos; no acepta decimales |

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador del lote. |
| nombre | string | Nombre del lote. |
| galponId | UUID | Galpón al que pertenece. |
| fechaIngreso | date | Fecha de ingreso. |
| poblacionInicial | integer | Inmutable. |
| poblacionActual | integer | Se reduce con la mortalidad. |
| costoTotal | integer | Pesos colombianos, sin decimales. |
| desvinculadoEn | string (fecha y hora) | ISO-8601 con desplazamiento. Solo en lotes anteriores (desvinculados); se omite mientras el lote está activo. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `NOMBRE_INVALIDO` | Nombre vacío, mayor a 100 o con caracteres no permitidos |
| 400 | `FECHA_INGRESO_FUTURA` | Fecha posterior a hoy |
| 400 | `POBLACION_INICIAL_INVALIDA` | 0, negativa, decimal o texto |
| 400 | `COSTO_TOTAL_INVALIDO` | 0, negativo, decimal o texto |
| 400 | `GALPON_REQUERIDO` | `galponId` ausente |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |
| 409 | `NOMBRE_DUPLICADO` | Nombre de lote ya existente (sin distinguir mayúsculas) |
| 409 | `GALPON_NO_DISPONIBLE` | El galpón no está DISPONIBLE (también si se manipula la solicitud) |
| 400 | `FECHA_INGRESO_INVALIDA` | `fechaIngreso` ausente o con un formato distinto de `yyyy-MM-dd` |
| 400 | `VALIDACION_FALLIDA` | Dos o más campos del body inválidos a la vez (el detalle va en `fieldErrors`) |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |

Atomicidad: guardar el lote y solicitar DISPONIBLE -> PRODUCTIVO a `GalponEstadoService` (origen `REGISTRO_LOTE`) ocurre en una sola transacción; si el cambio de estado se rechaza, el lote no queda guardado.

Ejemplo de request:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote A1",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Ejemplo de response:

```json
{
  "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "nombre": "Lote A1",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "poblacionActual": 1000,
  "costoTotal": 5000000
}
```

### GET `/api/v1/lotes` — Listar lotes

| Elemento | Definición |
|---|---|
| Request DTO | — |
| Response DTO | `LotePaginaResponse` |
| Éxito | 200 OK |

**Path params**: ninguno.

**Query params**

| Nombre | Tipo | Obligatorio | Por defecto | Regla |
|---|---|---|---|---|
| page | integer | no | 1 | Mayor o igual a 1. Tamaño fijo de 10; orden por fecha de ingreso descendente |

**Body (request)**: sin body.

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| contenido | array | Lista de `LoteResponse` con todos los lotes registrados: activos (sin `desvinculadoEn`) y anteriores (con `desvinculadoEn`). |
| pagina | integer | Página actual (desde 1). |
| tamanio | integer | Elementos por página. |
| totalElementos | integer | Total de resultados. |
| totalPaginas | integer | Total de páginas. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | `page` menor que 1 o no numérico |

Los galpones disponibles que muestra el formulario (FR-002) se obtienen con `GET /api/v1/galpones?estado=DISPONIBLE`.

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `NOMBRE_INVALIDO` | Nombre inválido | El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos. |
| `FECHA_INGRESO_FUTURA` | Fecha de ingreso inválida | La fecha de ingreso no puede ser posterior a hoy. |
| `POBLACION_INICIAL_INVALIDA` | Población inicial inválida | La población inicial debe ser un número entero mayor que cero. |
| `COSTO_TOTAL_INVALIDO` | Costo total inválido | El costo total debe ser un número entero mayor que cero, en pesos colombianos. |
| `GALPON_REQUERIDO` | Galpón requerido | Debes seleccionar un galpón. |
| `GALPON_NO_ENCONTRADO` | Galpón no encontrado | El galpón seleccionado no existe. |
| `NOMBRE_DUPLICADO` | Nombre duplicado | Ya existe un lote con ese nombre. |
| `GALPON_NO_DISPONIBLE` | Galpón no disponible | El galpón seleccionado no está disponible para recibir un lote. |
| `PARAMETRO_INVALIDO` | Parámetro inválido | Revisa los parámetros de la solicitud. |
| `FECHA_INGRESO_INVALIDA` | Fecha de ingreso inválida | La fecha de ingreso es obligatoria y debe tener el formato yyyy-MM-dd. |
| `VALIDACION_FALLIDA` | Validación fallida | Hay campos con datos inválidos. Revisa fieldErrors. |
| `CUERPO_INVALIDO` | Cuerpo inválido | El cuerpo de la solicitud no es un JSON válido. |

Ejemplo `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-disponible",
  "title": "Galpón no disponible",
  "status": 409,
  "detail": "El galpón seleccionado no está disponible para recibir un lote.",
  "instance": "/api/v1/lotes",
  "code": "GALPON_NO_DISPONIBLE"
}
```

## Peticiones y respuestas por escenario

Una petición por escenario del spec (más los casos límite). `POST /api/v1/lotes` no tiene path params ni query params; el body es `application/json`. Al final van las dos consultas que acompañan el formulario y el listado.

**1. `200` — Escenario 5: el formulario solo lista galpones DISPONIBLES**

- **Petición**: `GET /api/v1/galpones?estado=DISPONIBLE`
- **Path params**: ninguno
- **Query params**: `estado=DISPONIBLE`
- **Body**: sin body
- **Nota**: Existe además un galpón `PRODUCTIVO` ("Galpón Norte") que no aparece. Servicio definido en el plan de consultar galpón y lote.

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

**2. `201` — Escenario 1: registro exitoso en un galpón DISPONIBLE**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: `poblacionActual` = `poblacionInicial`. En la misma transacción el galpón pasa de `DISPONIBLE` a `PRODUCTIVO` (origen `REGISTRO_LOTE`).

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote A1",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `201` — cabeceras: `Location: /api/v1/lotes/2fc03a21-84c4-4c83-bb15-6607bb75cb90`:

```json
{
  "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "nombre": "Lote A1",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "poblacionActual": 1000,
  "costoTotal": 5000000
}
```

**3. `409` — Escenario 2: nombre duplicado (otra capitalización)**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: No se crea el lote ni cambia el estado del galpón.

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "lote a1",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-duplicado",
  "title": "Nombre duplicado",
  "status": 409,
  "detail": "Ya existe un lote con ese nombre.",
  "instance": "/api/v1/lotes",
  "code": "NOMBRE_DUPLICADO",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "DUPLICADO",
      "message": "El nombre ya está en uso"
    }
  ]
}
```

**4. `400` — Escenario 3: fecha de ingreso futura**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Hoy es 2026-10-05 (America/Bogota).

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-06",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/fecha-ingreso-futura",
  "title": "Fecha de ingreso inválida",
  "status": 400,
  "detail": "La fecha de ingreso no puede ser posterior a hoy.",
  "instance": "/api/v1/lotes",
  "code": "FECHA_INGRESO_FUTURA"
}
```

**5. `400` — Escenario 4: población inicial que no es un entero positivo**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 0,
  "costoTotal": 5000000
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": -10, "costoTotal": 5000000}`
- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": "cien", "costoTotal": 5000000}`
- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": 10.5, "costoTotal": 5000000}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/poblacion-inicial-invalida",
  "title": "Población inicial inválida",
  "status": 400,
  "detail": "La población inicial debe ser un número entero mayor que cero.",
  "instance": "/api/v1/lotes",
  "code": "POBLACION_INICIAL_INVALIDA",
  "fieldErrors": [
    {
      "field": "poblacionInicial",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**6. `400` — Escenario 5 (costo): costo total que no es un entero positivo**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": -500
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": 1000, "costoTotal": 0}`
- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": 1000, "costoTotal": "mucho"}`
- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2", "fechaIngreso": "2026-10-05", "poblacionInicial": 1000, "costoTotal": 100.75}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/costo-total-invalido",
  "title": "Costo total inválido",
  "status": 400,
  "detail": "El costo total debe ser un número entero mayor que cero, en pesos colombianos.",
  "instance": "/api/v1/lotes",
  "code": "COSTO_TOTAL_INVALIDO",
  "fieldErrors": [
    {
      "field": "costoTotal",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**7. `409` — Galpón que no está DISPONIBLE (también si se manipula la solicitud)**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: "Galpón Sur" está `PRODUCTIVO`; no se crea el lote.

Body de la petición:

```json
{
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-disponible",
  "title": "Galpón no disponible",
  "status": 409,
  "detail": "El galpón seleccionado no está disponible para recibir un lote.",
  "instance": "/api/v1/lotes",
  "code": "GALPON_NO_DISPONIBLE"
}
```

**8. `404` — Galpón inexistente**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón seleccionado no existe.",
  "instance": "/api/v1/lotes",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**9. `400` — Galpón no seleccionado**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-requerido",
  "title": "Galpón requerido",
  "status": 400,
  "detail": "Debes seleccionar un galpón.",
  "instance": "/api/v1/lotes",
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

**10. `400` — Nombre con caracteres no permitidos (también vacío o de más de 100)**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote $1",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "", "fechaIngreso": "2026-10-05", "poblacionInicial": 1000, "costoTotal": 5000000}`
- `{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "fechaIngreso": "2026-10-05", "poblacionInicial": 1000, "costoTotal": 5000000}`
- con `""` o `"A"*101` el `fieldErrors.code` es `OBLIGATORIO` o `LONGITUD_INVALIDA`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/lotes",
  "code": "NOMBRE_INVALIDO",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

**11. `400` — Fecha de ingreso ausente o con formato distinto de `yyyy-MM-dd`**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote B2",
  "fechaIngreso": "05/10/2026",
  "poblacionInicial": 1000,
  "costoTotal": 5000000
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/fecha-ingreso-invalida",
  "title": "Fecha de ingreso inválida",
  "status": 400,
  "detail": "La fecha de ingreso es obligatoria y debe tener el formato yyyy-MM-dd.",
  "instance": "/api/v1/lotes",
  "code": "FECHA_INGRESO_INVALIDA",
  "fieldErrors": [
    {
      "field": "fechaIngreso",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

**12. `400` — Dos campos inválidos a la vez**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Lote B2",
  "fechaIngreso": "2026-10-05",
  "poblacionInicial": 0,
  "costoTotal": 0
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/validacion-fallida",
  "title": "Validación fallida",
  "status": 400,
  "detail": "Hay campos con datos inválidos. Revisa fieldErrors.",
  "instance": "/api/v1/lotes",
  "code": "VALIDACION_FALLIDA",
  "fieldErrors": [
    {
      "field": "poblacionInicial",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    },
    {
      "field": "costoTotal",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**13. `400` — Body que no es un JSON válido**

- **Petición**: `POST /api/v1/lotes`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote B2"
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/lotes",
  "code": "CUERPO_INVALIDO"
}
```

**14. `200` — Listado de lotes (`FR-013`: se muestra el lote recién creado)**

- **Petición**: `GET /api/v1/lotes?page=1`
- **Path params**: ninguno
- **Query params**: `page=1`
- **Body**: sin body
- **Nota**: Tamaño de página fijo de 10; orden por `fechaIngreso` descendente.

Respuesta `200` — cabeceras: `Cache-Control: no-store`:

```json
{
  "contenido": [
    {
      "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
      "nombre": "Lote A1",
      "galponId": "550e8400-e29b-41d4-a716-446655440000",
      "fechaIngreso": "2026-10-05",
      "poblacionInicial": 1000,
      "poblacionActual": 1000,
      "costoTotal": 5000000
    },
    {
      "id": "9b2e5c14-3f7a-4d1e-8a60-1c2d3e4f5a6b",
      "nombre": "Lote Z9",
      "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "fechaIngreso": "2026-08-01",
      "poblacionInicial": 900,
      "poblacionActual": 870,
      "costoTotal": 4200000,
      "desvinculadoEn": "2026-09-20T08:15:00-05:00"
    }
  ],
  "pagina": 1,
  "tamanio": 10,
  "totalElementos": 2,
  "totalPaginas": 1
}
```

**15. `200` — Listado de lotes sin ningún lote registrado**

- **Petición**: `GET /api/v1/lotes?page=1`
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
  "totalPaginas": 0
}
```

**16. `400` — Listado con `page` menor que 1**

- **Petición**: `GET /api/v1/lotes?page=0`
- **Path params**: ninguno
- **Query params**: `page=0`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/lotes",
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

**17. `400` — Listado con `page` no numérico**

- **Petición**: `GET /api/v1/lotes?page=abc`
- **Path params**: ninguno
- **Query params**: `page=abc`
- **Body**: sin body

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/parametro-invalido",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "Revisa los parámetros de la solicitud.",
  "instance": "/api/v1/lotes",
  "code": "PARAMETRO_INVALIDO",
  "fieldErrors": [
    {
      "field": "page",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    }
  ]
}
```

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/RegistrarLoteUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.Lote;
import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarLoteUseCase {

    Lote registrar(Comando comando);

    record Comando(UUID galponId, String nombre, LocalDate fechaIngreso, long poblacionInicial, long costoTotal) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/application/port/in/ListarLotesUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.Pagina;

public interface ListarLotesUseCase {

    /** Lotes del más reciente al más antiguo; `pagina` empieza en 1. */
    Pagina<Lote> listar(int pagina, int tamanio);
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/RegistrarLoteRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La regla "la fecha no es futura" no va aquí: depende de "hoy" en America/Bogota con el Clock inyectado y la
 * valida LoteService.
 */
public record RegistrarLoteRequest(
        @NotNull UUID galponId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @NotNull LocalDate fechaIngreso,
        @NotNull @Positive Long poblacionInicial,
        @NotNull @Positive Long costoTotal) {

    public RegistrarLoteRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/LoteResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** `desvinculadoEn` se omite mientras el lote está activo (la respuesta no envía nulos). */
public record LoteResponse(UUID id, String nombre, UUID galponId, LocalDate fechaIngreso, long poblacionInicial,
                           long poblacionActual, long costoTotal, OffsetDateTime desvinculadoEn) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/LotePaginaResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import java.util.List;

public record LotePaginaResponse(List<LoteResponse> contenido, int pagina, int tamanio, long totalElementos,
                                 int totalPaginas) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/LoteRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.model.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LotePaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LoteResponse;

public final class LoteRestMapper {

    private LoteRestMapper() {
    }

    public static LoteResponse aResponse(Lote l) {
        return new LoteResponse(l.id(), l.nombre(), l.galponId(), l.fechaIngreso(), l.poblacionInicial(),
                l.poblacionActual(), l.costoTotal(),
                l.desvinculadoEn() == null ? null : l.desvinculadoEn().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime());
    }

    public static LotePaginaResponse aPagina(Pagina<Lote> p) {
        return new LotePaginaResponse(p.contenido().stream().map(LoteRestMapper::aResponse).toList(), p.pagina(),
                p.tamanio(), p.totalElementos(), p.totalPaginas());
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/LoteService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.ListarLotesUseCase;
import com.unimag.avicontrol.application.port.in.RegistrarLoteUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.NombreNormalizado;
import com.unimag.avicontrol.domain.model.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.policy.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoteService implements RegistrarLoteUseCase, ListarLotesUseCase {

    private static final String GALPON_NO_DISPONIBLE = "El galpón seleccionado no está disponible para recibir un lote.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final GalponEstadoService estados;
    private final Clock clock;

    public LoteService(GalponRepository galpones, LoteRepository lotes, GalponEstadoService estados, Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.estados = estados;
        this.clock = clock;
    }

    /**
     * Una sola transacción: si el cambio de estado o el guardado del lote fallan, no queda nada (FR-012).
     * Orden de validación: formato -> galpón existe (404) -> nombre único (409) -> galpón DISPONIBLE (409).
     */
    @Override
    @Transactional
    public Lote registrar(RegistrarLoteUseCase.Comando comando) {
        NombreNormalizado nombre = NombreNormalizado.de(comando.nombre());
        if (comando.fechaIngreso().isAfter(ReglasFecha.hoy(clock))) {
            throw new ValidacionNegocioException("FECHA_INGRESO_FUTURA",
                    "La fecha de ingreso no puede ser posterior a hoy.");
        }
        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));
        if (lotes.existsByNombreClave(nombre.clave())) {
            throw ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre.");
        }
        if (galpon.estado() != EstadoGalpon.DISPONIBLE) {
            throw new ConflictoException("GALPON_NO_DISPONIBLE", GALPON_NO_DISPONIBLE);
        }

        // El cambio de estado va primero: toma el bloqueo del galpón y serializa a dos administradores que
        // registren un lote en el mismo galpón a la vez; el segundo ve el estado ya cambiado.
        try {
            estados.solicitarCambio(galpon.id(), EstadoGalpon.DISPONIBLE, EstadoGalpon.PRODUCTIVO,
                    OrigenCambioEstado.REGISTRO_LOTE);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("GALPON_NO_DISPONIBLE", GALPON_NO_DISPONIBLE);
            }
            throw e;
        }
        return lotes.save(Lote.nuevo(galpon.id(), nombre, comando.fechaIngreso(),
                comando.poblacionInicial(), comando.costoTotal()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Lote> listar(int pagina, int tamanio) {
        return lotes.findAll(pagina, tamanio);
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/LoteController.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest;

import com.unimag.avicontrol.application.port.in.ListarLotesUseCase;
import com.unimag.avicontrol.application.port.in.RegistrarLoteUseCase;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LotePaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.LoteResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.RegistrarLoteRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.LoteRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lotes")
public class LoteController {

    private final RegistrarLoteUseCase registrarLote;
    private final ListarLotesUseCase listarLotes;

    public LoteController(RegistrarLoteUseCase registrarLote, ListarLotesUseCase listarLotes) {
        this.registrarLote = registrarLote;
        this.listarLotes = listarLotes;
    }

    @PostMapping
    public ResponseEntity<LoteResponse> registrar(@Valid @RequestBody RegistrarLoteRequest request) {
        Lote lote = registrarLote.registrar(new RegistrarLoteUseCase.Comando(request.galponId(), request.nombre(),
                request.fechaIngreso(), request.poblacionInicial(), request.costoTotal()));
        return ResponseEntity.created(URI.create("/api/v1/lotes/" + lote.id())).body(LoteRestMapper.aResponse(lote));
    }

    @GetMapping
    public LotePaginaResponse listar(@RequestParam(defaultValue = "1") int page) {
        return LoteRestMapper.aPagina(
                listarLotes.listar(ParametrosConsulta.pagina(page), ParametrosConsulta.TAMANIO_PAGINA_FIJO));
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/LotePersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.Pagina;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.LotePersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.LoteJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class LotePersistenceAdapter implements LoteRepository {

    private final LoteJpaRepository jpa;

    public LotePersistenceAdapter(LoteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Lote save(Lote lote) {
        try {
            return LotePersistenceMapper.aDominio(jpa.saveAndFlush(LotePersistenceMapper.aEntidad(lote)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.LOTE_NOMBRE)) {
                throw ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre.");
            }
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.LOTE_ACTIVO_POR_GALPON)) {
                throw new ConflictoException("GALPON_NO_DISPONIBLE",
                        "El galpón seleccionado no está disponible para recibir un lote.");
            }
            throw e;
        }
    }

    @Override
    public Optional<Lote> findById(UUID id) {
        return jpa.findById(id).map(LotePersistenceMapper::aDominio);
    }

    @Override
    public Optional<Lote> findActivoByGalponId(UUID galponId) {
        return jpa.findFirstByGalponIdAndDesvinculadoEnIsNull(galponId).map(LotePersistenceMapper::aDominio);
    }

    @Override
    public boolean existsByGalponId(UUID galponId) {
        return jpa.existsByGalponId(galponId);
    }

    @Override
    public boolean existsByNombreClave(String clave) {
        return jpa.existsByClave(clave);
    }

    @Override
    public List<Lote> findByGalponIdOrderByFechaIngresoDesc(UUID galponId) {
        return jpa.findByGalponIdOrderByFechaIngresoDescIdDesc(galponId).stream()
                .map(LotePersistenceMapper::aDominio).toList();
    }

    @Override
    public Pagina<Lote> findAll(int pagina, int tamanio) {
        var peticion = PageRequest.of(pagina - 1, tamanio,
                Sort.by(Sort.Order.desc("fechaIngreso"), Sort.Order.desc("id")));
        var resultado = jpa.findAll(peticion);
        return new Pagina<>(resultado.getContent().stream().map(LotePersistenceMapper::aDominio).toList(),
                pagina, tamanio, resultado.getTotalElements());
    }
}
```

## Fuera de alcance

- No persiste el estado del galpón: solo remite la solicitud al spec Actualizar estado (FR-010).
- No valida que la población inicial quepa en la capacidad del galpón (el spec no lo pide).
- El historial de lotes por galpón lo cubre el plan de consultar galpón y lote.
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `lote-registrar-request.json` | El request de ejemplo |
| `lote-registrar-response.json` | El response `201` de ejemplo |
| `lote-lista-response.json` | Una página de `GET /lotes` |
| `error-galpon-no-disponible.json` | El `409` de ejemplo |

## Tareas

**Goal**: El administrador registra un lote en un galpon Disponible; el galpon pasa a Productivo en la misma transaccion.

**Independent Test**: api/v1/lotes POST en galpon Disponible devuelve 201 con poblacionActual igual a poblacionInicial y galpon en PRODUCTIVO; si falla el cambio de estado se revierte el lote.

### Tests

- [ ] T029 [P] [US2] Prueba de contrato comparando contra los fixtures `lote-*.json` y `error-galpon-no-disponible.json`: 201, 400 (fecha futura, poblacion 0, costo 0, decimales), 404 galpon, 409 (nombre repetido, galpon no disponible) — clase `LoteControllerTest` (FR-003, FR-004, FR-006 a FR-008, FR-012, FR-014; SC-002 a SC-004)

### Implementation

- [ ] T031 [P] [US2] Crear RegistrarLoteRequest (solo `@NotBlank`, `@NotNull` y `@Positive`; la fecha futura la valida `LoteService` con el `Clock`) y LoteResponse en main/infrastructure/in/rest/dto/ (FR-005 a FR-008)
- [ ] T032 [P] [US2] Crear LoteRestMapper en main/infrastructure/in/rest/mapper/ y LotePersistenceMapper en main/infrastructure/out/persistence/mapper/
- [ ] T033 [P] [US2] Crear RegistrarLoteUseCase en main/application/port/in/
- [ ] T034 [US2] Implementar LoteService.registrar en main/application/usecase/: galpon Disponible, nombre unico, poblacionActual = poblacionInicial, solicitar DISPONIBLE -> PRODUCTIVO con origen REGISTRO_LOTE (FR-003, FR-004, FR-009 a FR-012)
- [ ] T035 [US2] Implementar endpoints POST lotes y GET lotes en LoteController (FR-001, FR-002, FR-011, FR-013)
- [ ] T036 [US2] Pruebas unitarias de LoteService en test/application/: incluye que si GalponEstadoService falla la excepcion se propaga y el lote no se da por registrado (el limite @Transactional lo cubre la regla ArchUnit T019) — clase `LoteServiceTest` (FR-010, FR-012; SC-003)

**Checkpoint**: un galpon puede tener produccion.

## Dependencias

- Necesita un galpón DISPONIBLE (de US1 o creado en la prueba).
- Usa `NombreNormalizado` (T085), `FixtureJson` (T086) y `GalponEstadoService` (T016).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-08: criterio de lote activo (`desvinculado_en IS NULL`).
- P-03: el spec no valida que la población inicial quepa en la capacidad del galpón.
- P-09: ruta, nombres de campo y códigos de error propuestos por este plan.
