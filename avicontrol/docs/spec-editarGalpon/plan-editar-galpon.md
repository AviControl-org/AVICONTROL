# Implementation Plan: Editar galpón

**Fecha**: 2026-10-02
**Spec**: [editar-galpon-spec.md](editar-galpon-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US9, prioridad P3)

## Summary

El administrador cambia el nombre en cualquier estado y la capacidad solo en MANTENIMIENTO; cada cambio queda en el historial.

**Enfoque técnico:**

1. `PATCH /galpones/{galponId}` recibe `nombre` y/o `capacidad`; un campo ausente no se modifica (FR-003, FR-004).
2. `GalponService.editar` normaliza el nombre con `NombreNormalizado`, valida el formato (FR-005) y la unicidad excluyendo al propio galpón (FR-006); valida que la capacidad sea un entero positivo (FR-007).
3. Si la solicitud trae `capacidad` con un valor distinto del actual, carga el galpón con `findByIdForUpdate` y exige estado MANTENIMIENTO (FR-004, FR-008).
4. Guarda solo los campos que cambiaron y escribe un `HistorialCambioGalpon` por campo (galpón, campo, valor anterior, valor nuevo, marca de tiempo) en la misma transacción (FR-010).
5. Responde `200` con `GalponResponse`. Entre dos pestañas de edición no hay control de versiones: la última confirmación prevalece, como indica el spec. El `409 CONFLICTO_CONCURRENCIA` solo aplica cuando, mientras se procesa esta solicitud, otra operación (por ejemplo un cambio de estado) modifica el mismo galpón y la columna `version` lo detecta.

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T010, T085 (`NombreNormalizado`), T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/EditarGalponUseCase.java                 # T077
│   └── usecase/GalponService.java                        # T078 (método editar)
└── infrastructure/in/rest/
    ├── GalponController.java                             # T079 (PATCH)
    └── dto/EditarGalponRequest.java                      # T076

src/test/java/com/unimag/avicontrol/
├── application/GalponServiceEditarTest.java              # T098
└── infrastructure/in/rest/GalponControllerTest.java      # T075
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `HistorialPersistenceAdapter.java`. Están en la sección *Código de implementación*.

`HistorialCambioGalpon` (T009), `NombreNormalizado` (T085) y `GalponResponse` (plan de registrar galpón) ya existen.

## Decisiones de diseño

**Mismo valor no es un cambio.** Guardar el mismo nombre no es duplicado y no genera historial (edge case del spec). Lo mismo con una capacidad igual a la actual: no se considera un intento de modificarla, así un cliente que envía el formulario completo no recibe `CAPACIDAD_NO_EDITABLE` por un campo que no tocó. Un cambio solo de mayúsculas ("galpón a" -> "Galpón A") sí es un cambio y no choca con el propio galpón.

**Bloqueo solo cuando depende del estado.** El bloqueo pesimista (`findByIdForUpdate`) se toma únicamente si hay que cambiar la capacidad, porque esa regla depende del estado y el estado puede cambiar en paralelo. Editar solo el nombre no lo necesita (FR-003).

**Nombre repetido en carrera.** El índice único de `lower(nombre)` es la defensa final; su violación la traduce el adaptador de persistencia a `ConflictoException` (`NOMBRE_DUPLICADO`).

**Orden de validación.** Tipos del body (`400 CAPACIDAD_INVALIDA` si no es un entero, antes de entrar al servicio) -> galpón existe (`404`) -> sin cambios (`400 SIN_CAMBIOS`) -> formato del nombre y capacidad positiva (`400`) -> nombre único (`409 NOMBRE_DUPLICADO`) -> capacidad editable (`409 CAPACIDAD_NO_EDITABLE`, también si se fuerza la solicitud, FR-008).

**Cuerpo sin cambios.** Un campo con `null` explícito se trata igual que uno ausente (los records de Java no distinguen ambos casos). Un body sin ningún campo, o solo con campos `null`, es `400 SIN_CAMBIOS`; un `null` junto a un campo con valor simplemente se ignora. Un body con valores iguales a los actuales responde `200` sin historial.

**Historial.** Un registro por campo realmente cambiado; si cambian nombre y capacidad en la misma solicitud, son dos registros con la misma marca de tiempo.

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### PATCH `/api/v1/galpones/{galponId}` — Editar galpón

| Elemento | Definición |
|---|---|
| Request DTO | `EditarGalponRequest` |
| Response DTO | `GalponResponse` |
| Éxito | 200 OK |

**Path params**

| Nombre | Tipo | Obligatorio | Regla |
|---|---|---|---|
| galponId | UUID | sí | Debe ser un UUID válido |

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| nombre | string | no | 1 a 100 caracteres tras recortar espacios; solo letras, números, espacios, guiones y guiones bajos; único sin distinguir mayúsculas (excluyendo el propio galpón); editable en cualquier estado |
| capacidad | integer (64 bits) | no | Entero mayor que 0; solo editable con el galpón en MANTENIMIENTO |

**Body (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador asignado al crear. |
| nombre | string | Nombre del galpón. |
| capacidad | integer | Capacidad entera mayor que 0. |
| estado | string | Uno de: DISPONIBLE, VACIADO_SANITARIO, PRODUCTIVO, EN_COSECHA, MANTENIMIENTO, AISLAMIENTO. |

**Errores**

| HTTP | `code` | Caso (spec) |
|---|---|---|
| 400 | `NOMBRE_INVALIDO` | Vacío, solo espacios, mayor a 100 o con caracteres no permitidos |
| 400 | `CAPACIDAD_INVALIDA` | 0, negativa, decimal o texto |
| 400 | `SIN_CAMBIOS` | Body sin ningún campo con valor (los campos `null` cuentan como ausentes) |
| 400 | `PARAMETRO_INVALIDO` | `galponId` no es un UUID |
| 404 | `GALPON_NO_ENCONTRADO` | El galpón no existe |
| 409 | `NOMBRE_DUPLICADO` | Nombre usado por **otro** galpón (guardar el mismo nombre no es duplicado) |
| 409 | `CAPACIDAD_NO_EDITABLE` | Cambiar capacidad con el galpón fuera de MANTENIMIENTO (también si se fuerza la solicitud) |
| 400 | `VALIDACION_FALLIDA` | Dos o más campos del body inválidos a la vez (el detalle va en `fieldErrors`) |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |
| 409 | `CONFLICTO_CONCURRENCIA` | Otra operación modificó el galpón mientras esta solicitud se procesaba (detectado con `version`) |

Un campo ausente no se modifica. Se escribe un registro en `HistorialCambioGalpon` por cada campo que realmente cambió (galpón, campo, valor anterior, valor nuevo, marca de tiempo). Entre pestañas la última confirmación prevalece (el spec no pide bloqueo optimista); `CONFLICTO_CONCURRENCIA` se reserva para una modificación simultánea de otra operación sobre el mismo galpón durante la transacción.

Ejemplo de request:

```json
{
  "capacidad": 1500
}
```

Ejemplo de response:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "MANTENIMIENTO"
}
```

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `NOMBRE_INVALIDO` | Nombre inválido | El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos. |
| `CAPACIDAD_INVALIDA` | Capacidad inválida | La capacidad debe ser un número entero mayor que cero. |
| `SIN_CAMBIOS` | Sin cambios | Indica al menos un campo para modificar: nombre o capacidad. |
| `PARAMETRO_INVALIDO` | Parámetro inválido | Revisa los parámetros de la solicitud. |
| `GALPON_NO_ENCONTRADO` | Galpón no encontrado | El galpón no existe. |
| `NOMBRE_DUPLICADO` | Nombre duplicado | Ya existe un galpón con ese nombre. |
| `CAPACIDAD_NO_EDITABLE` | Capacidad no editable | La capacidad solo se puede modificar cuando el galpón está en mantenimiento. |
| `VALIDACION_FALLIDA` | Validación fallida | Hay campos con datos inválidos. Revisa fieldErrors. |
| `CUERPO_INVALIDO` | Cuerpo inválido | El cuerpo de la solicitud no es un JSON válido. |
| `CONFLICTO_CONCURRENCIA` | Conflicto de concurrencia | Otro usuario modificó el registro al mismo tiempo. Actualiza la información e inténtalo de nuevo. |

Ejemplo `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-no-editable",
  "title": "Capacidad no editable",
  "status": 409,
  "detail": "La capacidad solo se puede modificar cuando el galpón está en mantenimiento.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "CAPACIDAD_NO_EDITABLE"
}
```

## Peticiones y respuestas por escenario

Todas son `PATCH /api/v1/galpones/{galponId}` sin query params; el body es `application/json` y cada campo es opcional. Los números de escenario son los del spec.

**1. `200` — Escenario 1 (historia 1): cambiar el nombre**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Antes se llamaba "Galpón A". Se escribe una fila de historial: campo `nombre`, valor anterior "Galpón A", valor nuevo "Galpón Norte".

Body de la petición:

```json
{
  "nombre": "Galpón Norte"
}
```

Respuesta `200`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "DISPONIBLE"
}
```

**2. `409` — Escenario 2: nombre usado por otro galpón**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Guardar el mismo nombre que ya tiene el galpón no es duplicado.

Body de la petición:

```json
{
  "nombre": "Galpón B"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-duplicado",
  "title": "Nombre duplicado",
  "status": 409,
  "detail": "Ya existe un galpón con ese nombre.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
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

**3. `400` — Escenario 3: nombre vacío o solo espacios**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": ""
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"nombre": "   "}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "NOMBRE_INVALIDO",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "LONGITUD_INVALIDA",
      "message": "Longitud fuera del rango permitido"
    }
  ]
}
```

**4. `400` — Nombre de más de 100 caracteres**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "NOMBRE_INVALIDO",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "LONGITUD_INVALIDA",
      "message": "Longitud fuera del rango permitido"
    }
  ]
}
```

**5. `400` — Nombre con caracteres no permitidos**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón #1"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
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

**6. `200` — Escenario 5 (historia 2): cambiar la capacidad con el galpón en MANTENIMIENTO**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Capacidad anterior 1000. Una fila de historial: campo `capacidad`, "1000" -> "1500".

Body de la petición:

```json
{
  "capacidad": 1500
}
```

Respuesta `200`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "MANTENIMIENTO"
}
```

**7. `200` — Nombre y capacidad en la misma solicitud**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Dos filas de historial con la misma marca de tiempo.

Body de la petición:

```json
{
  "nombre": "Galpón Norte B",
  "capacidad": 1800
}
```

Respuesta `200`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte B",
  "capacidad": 1800,
  "estado": "MANTENIMIENTO"
}
```

**8. `409` — Escenario 6: capacidad con el galpón fuera de MANTENIMIENTO (también si se fuerza la solicitud)**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: El galpón está `DISPONIBLE`; no se guarda nada.

Body de la petición:

```json
{
  "capacidad": 1500
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-no-editable",
  "title": "Capacidad no editable",
  "status": 409,
  "detail": "La capacidad solo se puede modificar cuando el galpón está en mantenimiento.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "CAPACIDAD_NO_EDITABLE"
}
```

**9. `400` — Escenario 7: capacidad que no es un entero positivo**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "capacidad": 0
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"capacidad": -5}`
- `{"capacidad": 10.5}`
- `{"capacidad": "abc"}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-invalida",
  "title": "Capacidad inválida",
  "status": 400,
  "detail": "La capacidad debe ser un número entero mayor que cero.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "CAPACIDAD_INVALIDA",
  "fieldErrors": [
    {
      "field": "capacidad",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**10. `400` — Body sin ningún campo con valor**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{}
```

También produce esta misma respuesta (cambia solo el body):

- `{"nombre": null, "capacidad": null}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/sin-cambios",
  "title": "Sin cambios",
  "status": 400,
  "detail": "Indica al menos un campo para modificar: nombre o capacidad.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "SIN_CAMBIOS"
}
```

**11. `200` — Mismos valores que ya tiene el galpón (no es un cambio)**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: No hay error, no se escribe historial y no se exige `MANTENIMIENTO`.

Body de la petición:

```json
{
  "nombre": "Galpón Norte",
  "capacidad": 1500
}
```

Respuesta `200`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "DISPONIBLE"
}
```

**12. `404` — Galpón inexistente (FR-013)**

- **Petición**: `PATCH /api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Path params**: `galponId=d9b2d63d-a233-4123-847a-3f2c0c1e6b55`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón Norte"
}
```

Respuesta `404`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/galpon-no-encontrado",
  "title": "Galpón no encontrado",
  "status": 404,
  "detail": "El galpón no existe.",
  "instance": "/api/v1/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "code": "GALPON_NO_ENCONTRADO"
}
```

**13. `400` — `galponId` que no es un UUID**

- **Petición**: `PATCH /api/v1/galpones/abc`
- **Path params**: `galponId=abc`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón Norte"
}
```

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

**14. `400` — Dos campos inválidos a la vez**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón #1",
  "capacidad": 0
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/validacion-fallida",
  "title": "Validación fallida",
  "status": 400,
  "detail": "Hay campos con datos inválidos. Revisa fieldErrors.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "VALIDACION_FALLIDA",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "FORMATO_INVALIDO",
      "message": "Formato no válido"
    },
    {
      "field": "capacidad",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**15. `400` — Body que no es un JSON válido**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"nombre": 
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "CUERPO_INVALIDO"
}
```

**16. `409` — Otra confirmación modificó el galpón al mismo tiempo**

- **Petición**: `PATCH /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`
- **Path params**: `galponId=550e8400-e29b-41d4-a716-446655440000`
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón Norte"
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/conflicto-concurrencia",
  "title": "Conflicto de concurrencia",
  "status": 409,
  "detail": "Otro usuario modificó el registro al mismo tiempo. Actualiza la información e inténtalo de nuevo.",
  "instance": "/api/v1/galpones/550e8400-e29b-41d4-a716-446655440000",
  "code": "CONFLICTO_CONCURRENCIA"
}
```

**Escenarios 4 (historia 1) y 8 (historia 2): cancelar la confirmación.** Son del cliente: al cancelar no se envía ninguna petición, así que no hay request ni response (FR-009, FR-011).

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/EditarGalponUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.Galpon;
import java.util.UUID;

public interface EditarGalponUseCase {

    Galpon editar(UUID galponId, Comando comando);

    /** Un campo en null significa "no modificar". */
    record Comando(String nombre, Long capacidad) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/EditarGalponRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Ambos campos son opcionales: un campo ausente (o null) no se modifica. */
public record EditarGalponRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @Positive Long capacidad) {

    public EditarGalponRequest {
        nombre = nombre == null ? null : nombre.strip();
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
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.EstadoGalpon;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.HistorialCambioGalpon;
import com.unimag.avicontrol.domain.model.NombreNormalizado;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
import java.time.Instant;
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
    public Galpon editar(UUID galponId, EditarGalponUseCase.Comando comando) {
        Galpon actual = galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        if (comando.nombre() == null && comando.capacidad() == null) {
            throw new ValidacionNegocioException("SIN_CAMBIOS",
                    "Indica al menos un campo para modificar: nombre o capacidad.");
        }
        NombreNormalizado nombre = comando.nombre() == null ? null : NombreNormalizado.de(comando.nombre());
        if (comando.capacidad() != null && comando.capacidad() <= 0) {
            throw new ValidacionNegocioException("CAPACIDAD_INVALIDA",
                    "La capacidad debe ser un número entero mayor que cero.");
        }

        // Mismo valor no es un cambio: no es duplicado, no genera historial y no exige MANTENIMIENTO.
        boolean cambiaNombre = nombre != null && !nombre.valor().equals(actual.nombre());
        if (cambiaNombre && galpones.existsByNombreClaveAndIdNot(nombre.clave(), galponId)) {
            throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
        }
        boolean cambiaCapacidad = comando.capacidad() != null && comando.capacidad() != actual.capacidad();
        if (cambiaCapacidad) {
            // La regla depende del estado, que puede cambiar en paralelo: se relee bajo bloqueo (FR-008).
            actual = galpones.findByIdForUpdate(galponId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
            cambiaCapacidad = comando.capacidad() != actual.capacidad();
            if (cambiaCapacidad && actual.estado() != EstadoGalpon.MANTENIMIENTO) {
                throw new ConflictoException("CAPACIDAD_NO_EDITABLE",
                        "La capacidad solo se puede modificar cuando el galpón está en mantenimiento.");
            }
        }
        if (!cambiaNombre && !cambiaCapacidad) {
            return actual;
        }

        Instant ahora = clock.instant();
        Galpon editado = actual;
        if (cambiaNombre) {
            historialCambios.save(new HistorialCambioGalpon(UUID.randomUUID(), galponId, "nombre",
                    actual.nombre(), nombre.valor(), ahora));
            editado = editado.conNombre(nombre);
        }
        if (cambiaCapacidad) {
            historialCambios.save(new HistorialCambioGalpon(UUID.randomUUID(), galponId, "capacidad",
                    String.valueOf(actual.capacidad()), String.valueOf(comando.capacidad()), ahora));
            editado = editado.conCapacidad(comando.capacidad());
        }
        return galpones.save(editado);
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
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.EditarGalponRequest;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponResponse;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.GalponRestMapper;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PatchMapping("/{galponId}")
    public GalponResponse editar(@PathVariable UUID galponId, @Valid @RequestBody EditarGalponRequest request) {
        Galpon galpon = editarGalpon.editar(galponId,
                new EditarGalponUseCase.Comando(request.nombre(), request.capacidad()));
        return GalponRestMapper.aResponse(galpon);
    }
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

- No cambia el estado del galpón ni la población de ningún lote.
- Los datos actuales para el formulario (FR-002) se obtienen con `GET /api/v1/galpones/{galponId}`.
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `galpon-editar-capacidad-request.json` | El request de ejemplo |
| `galpon-editar-nombre-request.json` | Un request que solo cambia el nombre |
| `galpon-editar-response.json` | El response `200` de ejemplo |
| `error-capacidad-no-editable.json` | El `409` de ejemplo |

## Tareas

**Goal**: El administrador cambia el nombre en cualquier estado y la capacidad solo en Mantenimiento; ambos cambios quedan en el historial.

**Independent Test**: cambiar nombre devuelve 200 con registro en historial; cambiar capacidad con galpon Disponible devuelve 409.

### Tests

- [ ] T075 [P] [US9] Prueba de contrato PATCH galpon comparando contra los fixtures `galpon-editar-*.json` y `error-capacidad-no-editable.json`: guardar el mismo nombre no es duplicado; cambiar capacidad fuera de Mantenimiento devuelve 409 — clase `GalponControllerTest` (FR-004 a FR-008, FR-013; SC-003)
- [ ] T098 [P] [US9] Pruebas unitarias de `GalponService.editar` (Mockito) en test/application/: mismo nombre sin error ni historial; capacidad igual a la actual fuera de MANTENIMIENTO sin error; capacidad distinta fuera de MANTENIMIENTO rechazada; nombre duplicado de otro galpón rechazado; nombre y capacidad cambiados generan dos registros de historial — clase `GalponServiceEditarTest` (FR-003, FR-004, FR-006, FR-008, FR-010; SC-002 a SC-004)

### Implementation

- [ ] T076 [P] [US9] Crear EditarGalponRequest en main/infrastructure/in/rest/dto/ (ambos campos opcionales) (FR-005, FR-007)
- [ ] T077 [P] [US9] Crear EditarGalponUseCase en main/application/port/in/
- [ ] T078 [US9] Implementar GalponService.editar en main/application/usecase/: unicidad excluyendo el propio galpon, capacidad solo en MANTENIMIENTO, un registro en HistorialCambioGalponRepository por campo cambiado (FR-003 a FR-006, FR-008, FR-010; SC-002 a SC-004)
- [ ] T079 [US9] Implementar PATCH /api/v1/galpones/{galponId} en GalponController (FR-001, FR-002)

**Checkpoint**: los 10 casos de uso del Modulo 1 funcionan de forma independiente.

## Dependencias

- Ninguna entre historias.
- Usa `NombreNormalizado` (T085) y `FixtureJson` (T086) del plan general.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-18: edge case del spec: si existiera un lote asociado a un galpón en MANTENIMIENTO, "se puede validar" que se impida editar la capacidad. Propuesta: rechazarlo con 409. Decidir si se implementa.
