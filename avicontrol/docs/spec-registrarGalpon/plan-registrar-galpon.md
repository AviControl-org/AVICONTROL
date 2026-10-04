# Implementation Plan: Registrar galpón

**Fecha**: 2026-10-02
**Spec**: [registrar-galpon-spec.md](registrar-galpon-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US1, prioridad P1)

## Summary

El administrador registra un galpón con nombre y capacidad. El sistema asigna UUID y estado inicial DISPONIBLE. Es la base para registrar lotes.

## Contexto específico

- **Tareas previas del plan general**: T006 (esquema), T008 (modelo Galpon y entidad), T010 (puertos y adaptadores), T012-T013 (excepciones y `GlobalExceptionHandler`), T014 (Jackson estricto).

## Clases y adaptadores propios

- `RegistrarGalponRequest`, `GalponResponse` (DTO REST)
- `GalponRestMapper`, `GalponPersistenceMapper`
- `RegistrarGalponUseCase` (puerto de entrada)
- `GalponService.registrar`
- `GalponController` (POST)

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `POST /api/v1/galpones` | Body: `nombre` (obligatorio, máx. 100, solo letras, números, espacios, guiones y guiones bajos; se recortan espacios) y `capacidad` (entero mayor que 0, sin decimales). | 201 con cabecera `Location` y `GalponResponse`. |

```json
// Request
{ "nombre": "Galpón Norte", "capacidad": 1500 }

// Response 201
{ "id": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Galpón Norte", "capacidad": 1500, "estado": "DISPONIBLE" }
```

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Nombre vacío, solo espacios, más de 100 caracteres o con caracteres no permitidos | 400 | `NOMBRE_INVALIDO` |
| Capacidad 0, negativa, decimal o texto | 400 | `CAPACIDAD_INVALIDA` |
| Nombre ya existente (sin distinguir mayúsculas, tras recortar espacios) | 409 | `NOMBRE_DUPLICADO` |
| Falla de base de datos | 500 | `ERROR_INTERNO` (mensaje genérico, nada se guarda) |

La capacidad se guarda como entero de 64 bits (edge case del spec sobre valores muy grandes).

## Fuera de alcance

- No crea lotes ni cambia el estado del galpón después del registro; el estado inicial siempre es DISPONIBLE.
- El listado donde aparece el galpón nuevo lo implementa [plan-consultar-galpon-lote](../spec-consultar-galpón-lote/plan-consultar-galpon-lote.md).
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: El administrador registra un galpon con nombre y capacidad; queda en estado Disponible.

**Independent Test**: api/v1/galpones POST con datos validos devuelve 201 con estado DISPONIBLE; nombre repetido con otra capitalizacion devuelve 409.

### Tests

- [ ] T021 [P] [US1] Prueba de contrato (MockMvc): 201, 400 (nombre vacio; capacidad 0, -5, 10.5 o texto), 409 nombre duplicado con diferente capitalizacion — clase `GalponControllerTest`
- [ ] T022 [P] [US1] Prueba de persistencia: indice lower(nombre) rechaza "galpon norte" vs "Galpon Norte" — clase `GalponPersistenceTest`

### Implementation

- [ ] T023 [P] [US1] Crear RegistrarGalponRequest (@NotBlank, @Size(max=100), @Pattern, @Positive) y GalponResponse en main/infrastructure/in/rest/dto/
- [ ] T024 [P] [US1] Crear GalponRestMapper en main/infrastructure/in/rest/mapper/ y GalponPersistenceMapper en main/infrastructure/out/persistence/mapper/
- [ ] T025 [P] [US1] Crear RegistrarGalponUseCase en main/application/port/in/
- [ ] T026 [US1] Implementar GalponService.registrar en main/application/usecase/: recortar espacios, unicidad por puerto de salida, estado inicial DISPONIBLE
- [ ] T027 [US1] Implementar endpoint POST en GalponController con cabecera Location
- [ ] T028 [US1] Pruebas unitarias de GalponService.registrar en test/application/ — clase `GalponServiceTest`

**Checkpoint**: registrar galpon funciona de extremo a extremo.

## Dependencias

- Ninguna entre historias. Lo requieren US2 (registrar lote) y los demás specs que necesitan galpones existentes.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.
