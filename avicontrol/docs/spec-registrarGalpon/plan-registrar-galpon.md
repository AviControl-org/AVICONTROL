# Implementation Plan: Registrar galpón

**Fecha**: 2026-10-02
**Spec**: [registrar-galpon-spec.md](registrar-galpon-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US1, prioridad P1)

## Summary

El administrador registra un galpón con nombre y capacidad. El sistema asigna UUID y estado inicial DISPONIBLE. Es la base para registrar lotes.

**Enfoque técnico:**

1. `GalponController` recibe `RegistrarGalponRequest`; Bean Validation rechaza campos ausentes y tipos incorrectos (decimal o texto en `capacidad`) antes de llegar al caso de uso (SC-002).
2. `GalponService.registrar` normaliza el nombre con `NombreNormalizado` (T085), comprueba la unicidad por el puerto `GalponRepository` y crea el `Galpon` con UUID y estado DISPONIBLE (FR-005, FR-006).
3. El índice único `lower(nombre)` (T006) es la defensa final ante dos registros simultáneos: su violación se traduce a `NOMBRE_DUPLICADO` (409).
4. Responde `201` con `Location` y `GalponResponse`. El listado donde aparece el galpón nuevo lo expone el spec de consultar.

## Contexto específico

- **Tareas previas del plan general**: T006 (esquema), T008 (modelo Galpon y entidad), T010 (puertos y adaptadores), T012-T013 (excepciones y `GlobalExceptionHandler`), T014 (Jackson estricto), T085 (`NombreNormalizado`), T086 (`FixtureJson`).

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── application/
│   ├── port/in/RegistrarGalponUseCase.java            # T025
│   └── usecase/GalponService.java                      # T026 (método registrar)
└── infrastructure/
    ├── in/rest/
    │   ├── GalponController.java                       # T027 (POST)
    │   ├── dto/RegistrarGalponRequest.java             # T023
    │   ├── dto/GalponResponse.java                     # T023
    │   └── mapper/GalponRestMapper.java                # T024
    └── out/persistence/mapper/GalponPersistenceMapper.java   # T024

src/test/java/com/unimag/avicontrol/
├── application/GalponServiceTest.java                  # T028
└── infrastructure/
    ├── in/rest/GalponControllerTest.java               # T021
    └── out/persistence/GalponPersistenceTest.java      # T022
```

Además de los archivos del árbol, el código completo de este caso de uso incluye: `GalponPersistenceAdapter.java`. Están en la sección *Código de implementación*.

`Galpon`, `GalponEntity`, `GalponRepository` y `NombreNormalizado` ya existen por el plan general (T008, T010, T085).

## Decisiones de diseño

**Unicidad en dos capas.** Primero se consulta por nombre normalizado para responder con un mensaje claro (FR-003, FR-010); el índice único `lower(nombre)` cubre la carrera entre dos administradores. Ambas rutas terminan en `NOMBRE_DUPLICADO`: el adaptador de persistencia captura la `DataIntegrityViolationException` del índice y lanza `ConflictoException`, de modo que ninguna excepción de JPA sale de `infrastructure/out`.

**Validación antes de la base de datos.** El formato (`@NotBlank`, `@Size(max=100)`, `@Pattern` de letras, números, espacios, guiones y guiones bajos) y los enteros se validan antes de llamar al repositorio. Solo el duplicado concurrente puede llegar hasta PostgreSQL (SC-002). El nombre se recorta antes de validar, así que un nombre de solo espacios es inválido (FR-002).

**Enteros estrictos.** `capacidad` es `Long`. Jackson rechaza `10.5` y `"abc"` (`ACCEPT_FLOAT_AS_INT=false`, T014) y el handler lo traduce a `CAPACIDAD_INVALIDA` con el campo en `fieldErrors`.

**Una transacción.** `registrar` se ejecuta en una sola transacción de `application`; si falla el guardado no queda nada (la regla ArchUnit T019 verifica dónde vive `@Transactional`).

## Contrato

> Rutas, nombres de campo y códigos de error **propuestos** por este plan: los specs describen el comportamiento, no el contrato (P-09). Convenciones comunes (formato, nulos, fechas, paginación y formato de error): [arquitectura.md](../arquitectura/arquitectura.md#convenciones-de-contrato).

### POST `/api/v1/galpones` — Registrar galpón

| Elemento | Definición |
|---|---|
| Request DTO | `RegistrarGalponRequest` |
| Response DTO | `GalponResponse` |
| Éxito | 201 Created, cabecera `Location: /api/v1/galpones/{id}` |

**Path params**: ninguno.

**Query params**: ninguno.

**Body (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| nombre | string | sí | 1 a 100 caracteres tras recortar espacios; solo letras, números, espacios, guiones y guiones bajos; único sin distinguir mayúsculas |
| capacidad | integer (64 bits) | sí | Entero mayor que 0; no acepta decimales ni texto |

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
| 400 | `NOMBRE_INVALIDO` | Nombre vacío, solo espacios, mayor a 100 o con caracteres no permitidos |
| 400 | `CAPACIDAD_INVALIDA` | Capacidad 0, negativa, decimal o texto |
| 409 | `NOMBRE_DUPLICADO` | Nombre ya existente (sin distinguir mayúsculas, tras recortar espacios) |
| 500 | `ERROR_INTERNO` | Falla de base de datos: mensaje genérico, no se crea el galpón |
| 400 | `VALIDACION_FALLIDA` | Dos o más campos del body inválidos a la vez (el detalle va en `fieldErrors`) |
| 400 | `CUERPO_INVALIDO` | El body no es un JSON válido |

Ejemplo de request:

```json
{
  "nombre": "Galpón Norte",
  "capacidad": 1500
}
```

Ejemplo de response:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "DISPONIBLE"
}
```

### Textos fijos de error

| `code` | `title` | `detail` (mensaje fijo) |
|---|---|---|
| `NOMBRE_INVALIDO` | Nombre inválido | El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos. |
| `CAPACIDAD_INVALIDA` | Capacidad inválida | La capacidad debe ser un número entero mayor que cero. |
| `NOMBRE_DUPLICADO` | Nombre duplicado | Ya existe un galpón con ese nombre. |
| `ERROR_INTERNO` | Error interno | No fue posible completar la operación. Intenta de nuevo. |
| `VALIDACION_FALLIDA` | Validación fallida | Hay campos con datos inválidos. Revisa fieldErrors. |
| `CUERPO_INVALIDO` | Cuerpo inválido | El cuerpo de la solicitud no es un JSON válido. |

Ejemplo `400` (capacidad decimal):

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-invalida",
  "title": "Capacidad inválida",
  "status": 400,
  "detail": "La capacidad debe ser un número entero mayor que cero.",
  "instance": "/api/v1/galpones",
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

Ejemplo `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-duplicado",
  "title": "Nombre duplicado",
  "status": 409,
  "detail": "Ya existe un galpón con ese nombre.",
  "instance": "/api/v1/galpones",
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

## Peticiones y respuestas por escenario

Una petición por escenario del spec (más los casos límite). Todas son `POST /api/v1/galpones`, sin path params ni query params; el body se envía como `application/json`.

**1. `201` — Escenario 1: registro exitoso**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: El galpón nace en estado `DISPONIBLE` y recibe su UUID; no se envían `id` ni `estado`.

Body de la petición:

```json
{
  "nombre": "Galpón Norte",
  "capacidad": 1500
}
```

Respuesta `201` — cabeceras: `Location: /api/v1/galpones/550e8400-e29b-41d4-a716-446655440000`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Galpón Norte",
  "capacidad": 1500,
  "estado": "DISPONIBLE"
}
```

**2. `409` — Escenario 2: nombre duplicado (sin distinguir mayúsculas)**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: Ya existe "Galpón Norte"; no se crea el galpón.

Body de la petición:

```json
{
  "nombre": "galpón norte",
  "capacidad": 800
}
```

Respuesta `409`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-duplicado",
  "title": "Nombre duplicado",
  "status": 409,
  "detail": "Ya existe un galpón con ese nombre.",
  "instance": "/api/v1/galpones",
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

**3. `400` — Escenario 3: capacidad que no es un entero positivo**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón Sur",
  "capacidad": -5
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"nombre": "Galpón Sur", "capacidad": 0}`
- `{"nombre": "Galpón Sur", "capacidad": 10.5}`
- `{"nombre": "Galpón Sur", "capacidad": "abc"}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-invalida",
  "title": "Capacidad inválida",
  "status": 400,
  "detail": "La capacidad debe ser un número entero mayor que cero.",
  "instance": "/api/v1/galpones",
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

**4. `400` — Escenario 3b: capacidad ausente**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón Sur"
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/capacidad-invalida",
  "title": "Capacidad inválida",
  "status": 400,
  "detail": "La capacidad debe ser un número entero mayor que cero.",
  "instance": "/api/v1/galpones",
  "code": "CAPACIDAD_INVALIDA",
  "fieldErrors": [
    {
      "field": "capacidad",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**5. `400` — Escenario 4: nombre vacío**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "",
  "capacidad": 1500
}
```

También produce esta misma respuesta (cambia solo el body):

- `{"nombre": "   ", "capacidad": 1500}`
- `{"capacidad": 1500}`

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones",
  "code": "NOMBRE_INVALIDO",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    }
  ]
}
```

**6. `400` — Edge case: nombre de más de 100 caracteres**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "capacidad": 1500
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones",
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

**7. `400` — Edge case: nombre con caracteres no permitidos**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "Galpón #1",
  "capacidad": 1500
}
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-invalido",
  "title": "Nombre inválido",
  "status": 400,
  "detail": "El nombre es obligatorio, tiene máximo 100 caracteres y solo admite letras, números, espacios, guiones y guiones bajos.",
  "instance": "/api/v1/galpones",
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

**8. `400` — Dos campos inválidos a la vez**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición:

```json
{
  "nombre": "",
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
  "instance": "/api/v1/galpones",
  "code": "VALIDACION_FALLIDA",
  "fieldErrors": [
    {
      "field": "nombre",
      "code": "OBLIGATORIO",
      "message": "Es obligatorio"
    },
    {
      "field": "capacidad",
      "code": "ENTERO_POSITIVO",
      "message": "Debe ser un entero mayor que cero"
    }
  ]
}
```

**9. `400` — Body que no es un JSON válido**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo

Body de la petición (texto crudo):

```
{"nombre": "Galpón Norte", "capacidad": 1500
```

Respuesta `400`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/cuerpo-invalido",
  "title": "Cuerpo inválido",
  "status": 400,
  "detail": "El cuerpo de la solicitud no es un JSON válido.",
  "instance": "/api/v1/galpones",
  "code": "CUERPO_INVALIDO"
}
```

**10. `500` — Falla de base de datos**

- **Petición**: `POST /api/v1/galpones`
- **Path params**: ninguno
- **Query params**: ninguno
- **Body**: ver abajo
- **Nota**: No se crea el galpón y no queda ningún cambio parcial.

Body de la petición:

```json
{
  "nombre": "Galpón Oeste",
  "capacidad": 1200
}
```

Respuesta `500`:

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/error-interno",
  "title": "Error interno",
  "status": 500,
  "detail": "No fue posible completar la operación. Intenta de nuevo.",
  "instance": "/api/v1/galpones",
  "code": "ERROR_INTERNO"
}
```

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/RegistrarGalponUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

import com.unimag.avicontrol.domain.model.Galpon;

public interface RegistrarGalponUseCase {

    Galpon registrar(Comando comando);

    record Comando(String nombre, long capacidad) {
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/RegistrarGalponRequest.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrarGalponRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @NotNull @Positive Long capacidad) {

    /** El nombre se recorta antes de validar: un nombre de solo espacios queda vacío y es inválido (FR-002). */
    public RegistrarGalponRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/dto/GalponResponse.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.dto;

import com.unimag.avicontrol.domain.model.EstadoGalpon;
import java.util.UUID;

public record GalponResponse(UUID id, String nombre, long capacidad, EstadoGalpon estado) {
}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/in/rest/mapper/GalponRestMapper.java`**

```java
package com.unimag.avicontrol.infrastructure.in.rest.mapper;

import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponResponse;

public final class GalponRestMapper {

    private static final String SIN_COINCIDENCIAS =
            "No se encontraron galpones con ese nombre. Se muestran todos los galpones.";
    private static final String SIN_LOTES = "No existen lotes registrados para este galpón";

    private GalponRestMapper() {
    }

    public static GalponResponse aResponse(Galpon galpon) {
        return new GalponResponse(galpon.id(), galpon.nombre(), galpon.capacidad(), galpon.estado());
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

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
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.model.NombreNormalizado;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import java.time.Clock;
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

    @Override
    @Transactional
    public Galpon registrar(RegistrarGalponUseCase.Comando comando) {
        NombreNormalizado nombre = NombreNormalizado.de(comando.nombre());
        if (comando.capacidad() <= 0) {
            throw new ValidacionNegocioException("CAPACIDAD_INVALIDA",
                    "La capacidad debe ser un número entero mayor que cero.");
        }
        if (galpones.existsByNombreClave(nombre.clave())) {
            throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
        }
        // Si dos registros llegan a la vez, el índice único lower(nombre) lo rechaza y el adaptador
        // de persistencia lo traduce a este mismo NOMBRE_DUPLICADO.
        return galpones.save(Galpon.nuevo(nombre, comando.capacidad()));
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
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.GalponResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.RegistrarGalponRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.GalponRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping
    public ResponseEntity<GalponResponse> registrar(@Valid @RequestBody RegistrarGalponRequest request) {
        Galpon galpon = registrarGalpon.registrar(
                new RegistrarGalponUseCase.Comando(request.nombre(), request.capacidad()));
        return ResponseEntity.created(URI.create("/api/v1/galpones/" + galpon.id()))
                .body(GalponRestMapper.aResponse(galpon));
    }

    // (métodos de otros casos de uso: ver el plan de cada uno)

}
```

**`src/main/java/com/unimag/avicontrol/infrastructure/out/persistence/adapter/GalponPersistenceAdapter.java`**

```java
package com.unimag.avicontrol.infrastructure.out.persistence.adapter;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.Galpon;
import com.unimag.avicontrol.domain.port.out.GalponRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.GalponPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.GalponJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class GalponPersistenceAdapter implements GalponRepository {

    private final GalponJpaRepository jpa;

    public GalponPersistenceAdapter(GalponJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Galpon save(Galpon galpon) {
        try {
            // saveAndFlush: la violación del índice único debe aparecer aquí, no al confirmar la transacción.
            return GalponPersistenceMapper.aDominio(jpa.saveAndFlush(GalponPersistenceMapper.aEntidad(galpon)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.GALPON_NOMBRE)) {
                throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
            }
            throw e;
        }
    }

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

    // (métodos de otros casos de uso: ver el plan de cada uno)
}
```

## Fuera de alcance

- No crea lotes ni cambia el estado después del registro; el estado inicial siempre es DISPONIBLE.
- El listado donde aparece el galpón nuevo lo implementa [plan-consultar-galpon-lote](../spec-consultar-galpón-lote/plan-consultar-galpon-lote.md).
- Los comportamientos de interfaz del spec (formulario, confirmación, redirección al listado) son del cliente; la API solo expone el resultado.

## Fixtures

En `src/test/resources/contratos/`:

| Archivo | Contenido |
|---|---|
| `galpon-registrar-request.json` | El request de ejemplo del contrato |
| `galpon-registrar-response.json` | El response `201` de ejemplo |
| `error-capacidad-invalida.json` | El `400` de ejemplo |
| `error-nombre-duplicado.json` | El `409` de ejemplo |

`GalponControllerTest` envía el request y compara la respuesta contra estos archivos.

## Tareas

**Goal**: El administrador registra un galpon con nombre y capacidad; queda en estado Disponible.

**Independent Test**: api/v1/galpones POST con datos validos devuelve 201 con estado DISPONIBLE; nombre repetido con otra capitalizacion devuelve 409.

### Tests

- [ ] T021 [P] [US1] Prueba de contrato (MockMvc) comparando contra los fixtures `galpon-registrar-*.json` y `error-*.json`: 201, 400 (nombre vacio; capacidad 0, -5, 10.5 o texto), 409 nombre duplicado con diferente capitalizacion — clase `GalponControllerTest` (FR-002, FR-003, FR-004, FR-006, FR-010; SC-001, SC-002, SC-005)
- [ ] T022 [P] [US1] Prueba de persistencia: indice lower(nombre) rechaza "galpon norte" vs "Galpon Norte" — clase `GalponPersistenceTest` (FR-003; SC-002)

### Implementation

- [ ] T023 [P] [US1] Crear RegistrarGalponRequest (@NotBlank, @Size(max=100), @Pattern, @Positive) y GalponResponse en main/infrastructure/in/rest/dto/ (FR-002, FR-004)
- [ ] T024 [P] [US1] Crear GalponRestMapper en main/infrastructure/in/rest/mapper/ y GalponPersistenceMapper en main/infrastructure/out/persistence/mapper/
- [ ] T025 [P] [US1] Crear RegistrarGalponUseCase en main/application/port/in/
- [ ] T026 [US1] Implementar GalponService.registrar en main/application/usecase/: recortar espacios, unicidad por puerto de salida, estado inicial DISPONIBLE (FR-002 a FR-007)
- [ ] T027 [US1] Implementar endpoint POST en GalponController con cabecera Location (FR-001, FR-005, FR-006)
- [ ] T028 [US1] Pruebas unitarias de GalponService.registrar en test/application/ — clase `GalponServiceTest` (FR-002 a FR-006; SC-001, SC-004)

**Checkpoint**: registrar galpon funciona de extremo a extremo.

## Dependencias

- Ninguna entre historias. Lo requieren US2 (registrar lote) y los demás specs que necesitan galpones existentes.
- Usa `NombreNormalizado` (T085) y `FixtureJson` (T086) del plan general.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-09: ruta, nombres de campo y códigos de error propuestos por este plan.
- P-03: el spec no relaciona la capacidad con la población de futuros lotes.
