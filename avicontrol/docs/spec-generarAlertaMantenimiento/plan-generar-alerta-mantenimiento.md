# Implementation Plan: Generar alerta de mantenimiento

**Fecha**: 2026-10-02
**Spec**: [generar-alerta-mantenimiento-spec.md](generar-alerta-mantenimiento-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US8, prioridad P1)

## Summary

El técnico reporta una falla en un galpón DISPONIBLE; el administrador atiende la alerta y el galpón pasa a MANTENIMIENTO.

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T016, T018.

## Clases y adaptadores propios

- `GenerarAlertaMantenimientoRequest`, `AlertaMantenimientoResponse`
- `AlertaMantenimientoRestMapper`
- `AlertaMantenimientoUseCase`
- `AlertaMantenimientoService.generar` y `atender`
- `AlertaMantenimientoController`

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `POST /api/v1/alertas-mantenimiento` | Body: `galponId` (UUID), `descripcion` (obligatoria, no vacía, máx. 500), `severidad` y `tipo` (opcionales, texto), `tecnicoId` (provisional). | 201 con `AlertaMantenimientoResponse`, estado PENDIENTE. |
| `GET /api/v1/alertas-mantenimiento` | Query opcional: `estado` (por defecto PENDIENTE), `page`, `size`. | 200 con página de alertas (bandeja y historial del administrador). |
| `POST /api/v1/alertas-mantenimiento/{id}/atender` | UUID en la ruta. | 200 con la alerta en ATENDIDA y el estado del galpón (MANTENIMIENTO). |

```json
// Request
{ "galponId": "550e8400-e29b-41d4-a716-446655440000", "descripcion": "Reparar sistema de ventilación",
  "severidad": "Alta", "tipo": "Ventilación", "tecnicoId": "tecnico-01" }

// Response 201
{ "id": "7a4f9e10-2b3c-4d5e-8f60-1a2b3c4d5e6f", "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "descripcion": "Reparar sistema de ventilación", "fechaHora": "2026-10-02T10:20:00-05:00",
  "estado": "PENDIENTE", "severidad": "Alta", "tipo": "Ventilación" }
```

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Descripción vacía, solo espacios o mayor a 500 | 400 | `DESCRIPCION_INVALIDA` |
| El galpón no existe | 404 | `GALPON_NO_ENCONTRADO` |
| El galpón no está DISPONIBLE | 409 | `GALPON_NO_DISPONIBLE` |
| Ya hay una alerta PENDIENTE para ese galpón (también en concurrencia, por el índice parcial) | 409 | `ALERTA_ACTIVA_EXISTENTE` |
| Atender una alerta que no está PENDIENTE | 409 | `ALERTA_NO_PENDIENTE` |
| Alerta inexistente al atender | 404 | `ALERTA_NO_ENCONTRADA` |

La fecha y hora la captura el servidor con el `Clock`. Generar la alerta no cambia el estado del galpón; atenderla la marca ATENDIDA y solicita DISPONIBLE -> MANTENIMIENTO con origen `ADMINISTRADOR`, en la misma transacción.

## Fuera de alcance

- No cambia el estado del galpón al generar la alerta (FR-008).
- El estado ELIMINADA existe en la entidad pero el spec no define quién ni cómo elimina una alerta; queda sin flujo en este plan.
- El técnico no consulta sus alertas anteriores; el historial es del administrador.
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: El tecnico reporta una falla en un galpon Disponible; el administrador la atiende y el galpon pasa a Mantenimiento.

**Independent Test**: tecnico crea alerta Pendiente sin cambiar estado; segunda alerta para el mismo galpon devuelve 409; al atender la alerta el galpon queda en MANTENIMIENTO.

### Tests

- [ ] T069 [P] [US8] Prueba de contrato: crear alerta de mantenimiento, listar alertas pendientes, atender alerta — clase `AlertaMantenimientoControllerTest`
- [ ] T070 [P] [US8] Prueba de persistencia: indice parcial impide dos alertas pendientes para el mismo galpon en concurrencia — clase `AlertaMantenimientoPersistenceTest`

### Implementation

- [ ] T071 [P] [US8] Crear GenerarAlertaMantenimientoRequest, AlertaMantenimientoResponse en main/infrastructure/in/rest/dto/ y AlertaMantenimientoRestMapper en main/infrastructure/in/rest/mapper/
- [ ] T072 [P] [US8] Crear AlertaMantenimientoUseCase en main/application/port/in/
- [ ] T073 [US8] Implementar AlertaMantenimientoService.generar y AlertaMantenimientoService.atender en main/application/usecase/: generar valida galpon Disponible, descripcion no vacia hasta 500 caracteres, una sola alerta pendiente por galpon; atender marca la alerta ATENDIDA y solicita DISPONIBLE -> MANTENIMIENTO con origen ADMINISTRADOR
- [ ] T074 [US8] Implementar AlertaMantenimientoController en main/infrastructure/in/rest/

**Checkpoint**: todas las transiciones de la matriz tienen su camino implementado.

## Dependencias

- Solo del plan general (Foundational).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Autenticación y roles (técnico, administrador) están fuera del alcance del plan general (pregunta abierta 1).
- Identificar al técnico (spec: "la asocia al galpón y al técnico"): sin autenticación se acepta `tecnicoId` en el body de forma provisional. Cambiarlo cuando se defina la autenticación.
- Valores de `severidad` y `tipo`: el spec solo da ejemplos ("Alta", "Ventilación"). Se tratan como texto libre opcional hasta que se definan catálogos.
