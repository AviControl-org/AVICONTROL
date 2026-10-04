# Implementation Plan: Registrar lote

**Fecha**: 2026-10-02
**Spec**: [registrar-lote-spec.md](registrar-lote-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US2, prioridad P1)

## Summary

El administrador registra un lote en un galpón DISPONIBLE. El lote queda con población actual igual a la inicial y el galpón pasa a PRODUCTIVO en la misma transacción.

## Contexto específico

- **Tareas previas del plan general**: T006, T008, T010, T016 (`GalponEstadoService`).

## Clases y adaptadores propios

- `RegistrarLoteRequest`, `LoteResponse`
- `LoteRestMapper`, `LotePersistenceMapper`
- `RegistrarLoteUseCase`
- `LoteService.registrar`
- `LoteController` (POST y GET)

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `POST /api/v1/lotes` | Body: `galponId` (UUID obligatorio), `nombre`, `fechaIngreso` (no futura, zona America/Bogota), `poblacionInicial` (entero mayor que 0) y `costoTotal` (entero mayor que 0, pesos colombianos, sin decimales). | 201 con `Location` y `LoteResponse`. |
| `GET /api/v1/lotes` | Query: `page`, `size` (10). Orden por fecha de ingreso descendente. | 200 con página de `LoteResponse`. |

Los galpones disponibles para el formulario (FR-002) se obtienen con `GET /api/v1/galpones?estado=DISPONIBLE`, definido en el plan de consultar galpón y lote.

```json
// Request
{ "galponId": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Lote A1",
  "fechaIngreso": "2026-10-02", "poblacionInicial": 1000, "costoTotal": 5000000 }

// Response 201
{ "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90", "nombre": "Lote A1",
  "galponId": "550e8400-e29b-41d4-a716-446655440000", "fechaIngreso": "2026-10-02",
  "poblacionInicial": 1000, "poblacionActual": 1000, "costoTotal": 5000000 }
```

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Nombre vacío, mayor a 100 o con caracteres no permitidos | 400 | `NOMBRE_INVALIDO` |
| Fecha de ingreso futura | 400 | `FECHA_INGRESO_FUTURA` |
| Población inicial 0, negativa, decimal o texto | 400 | `POBLACION_INICIAL_INVALIDA` |
| Costo total 0, negativo, decimal o texto | 400 | `COSTO_TOTAL_INVALIDO` |
| `galponId` ausente | 400 | `GALPON_REQUERIDO` |
| El galpón no existe | 404 | `GALPON_NO_ENCONTRADO` |
| Nombre de lote ya existente (sin distinguir mayúsculas) | 409 | `NOMBRE_DUPLICADO` |
| El galpón no está DISPONIBLE (también si se manipula la solicitud) | 409 | `GALPON_NO_DISPONIBLE` |

Atomicidad: guardar el lote y la solicitud DISPONIBLE -> PRODUCTIVO a `GalponEstadoService` (origen `REGISTRO_LOTE`) ocurren en una sola transacción. Si el cambio de estado se rechaza, el lote no queda guardado.

## Fuera de alcance

- No persiste el estado del galpón: solo remite la solicitud al spec Actualizar estado (FR-010).
- No valida que la población inicial quepa en la capacidad del galpón (el spec no lo pide).
- Listado de lotes con filtros o historial por galpón: lo cubre el plan de consultar galpón y lote.
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: El administrador registra un lote en un galpon Disponible; el galpon pasa a Productivo en la misma transaccion.

**Independent Test**: api/v1/lotes POST en galpon Disponible devuelve 201 con poblacionActual igual a poblacionInicial y galpon en PRODUCTIVO; si falla el cambio de estado se revierte el lote.

### Tests

- [ ] T029 [P] [US2] Prueba de contrato: 201, 400 (fecha futura, poblacion 0, costo 0, decimales), 404 galpon, 409 (nombre repetido, galpon no disponible) — clase `LoteControllerTest`

### Implementation

- [ ] T031 [P] [US2] Crear RegistrarLoteRequest (@PastOrPresent, @Positive) y LoteResponse en main/infrastructure/in/rest/dto/
- [ ] T032 [P] [US2] Crear LoteRestMapper en main/infrastructure/in/rest/mapper/ y LotePersistenceMapper en main/infrastructure/out/persistence/mapper/
- [ ] T033 [P] [US2] Crear RegistrarLoteUseCase en main/application/port/in/
- [ ] T034 [US2] Implementar LoteService.registrar en main/application/usecase/: galpon Disponible, nombre unico, poblacionActual = poblacionInicial, solicitar DISPONIBLE -> PRODUCTIVO con origen REGISTRO_LOTE
- [ ] T035 [US2] Implementar endpoints POST lotes y GET lotes en LoteController
- [ ] T036 [US2] Pruebas unitarias de LoteService en test/application/: incluye que si GalponEstadoService falla la excepcion se propaga y el lote no se da por registrado (el limite @Transactional lo cubre la regla ArchUnit T019) — clase `LoteServiceTest`

**Checkpoint**: un galpon puede tener produccion.

## Dependencias

- Necesita un galpón DISPONIBLE (de US1 o creado en la prueba).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Pregunta abierta 3 del plan general: ningún spec valida que la población inicial no supere la capacidad del galpón.
