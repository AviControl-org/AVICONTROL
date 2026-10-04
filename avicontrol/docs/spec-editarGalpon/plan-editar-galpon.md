# Implementation Plan: Editar galpón

**Fecha**: 2026-10-02
**Spec**: [editar-galpon-spec.md](editar-galpon-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US9, prioridad P3)

## Summary

El administrador cambia el nombre en cualquier estado y la capacidad solo en MANTENIMIENTO; cada cambio queda en el historial.

## Contexto específico

- **Tareas previas del plan general**: T006, T009, T010.

## Clases y adaptadores propios

- `EditarGalponRequest`
- `EditarGalponUseCase`
- `GalponService.editar`
- `GalponController` (PATCH)

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `PATCH /api/v1/galpones/{id}` | Body con `nombre` y/o `capacidad`; ambos opcionales. | 200 con `GalponResponse` actualizado. |

```json
// Request: cambiar solo el nombre
{ "nombre": "Galpón Norte" }

// Request: cambiar capacidad (solo con el galpón en MANTENIMIENTO)
{ "capacidad": 1500 }
```

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Nombre vacío, solo espacios, mayor a 100 o con caracteres no permitidos | 400 | `NOMBRE_INVALIDO` |
| Capacidad 0, negativa, decimal o texto | 400 | `CAPACIDAD_INVALIDA` |
| Body sin ningún campo | 400 | `SIN_CAMBIOS` |
| El galpón no existe | 404 | `GALPON_NO_ENCONTRADO` |
| Nombre ya usado por **otro** galpón (sin distinguir mayúsculas; guardar el mismo nombre no es duplicado) | 409 | `NOMBRE_DUPLICADO` |
| Cambiar capacidad con el galpón fuera de MANTENIMIENTO (también si se fuerza la solicitud) | 409 | `CAPACIDAD_NO_EDITABLE` |

Se escribe un registro en `HistorialCambioGalpon` por cada campo que realmente cambió (galpón, campo, valor anterior, valor nuevo, marca de tiempo). El nombre puede editarse en cualquier estado. Sin bloqueo optimista en la edición: cada solicitud lee el galpón vigente y la última confirmación prevalece, como indica el spec.

## Fuera de alcance

- No cambia el estado del galpón ni la población de ningún lote.
- El mensaje de confirmación (FR-009) y la carga del formulario (FR-002) son del cliente; el cliente obtiene los datos actuales con `GET /api/v1/galpones/{id}`.
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: El administrador cambia el nombre en cualquier estado y la capacidad solo en Mantenimiento; ambos cambios quedan en el historial.

**Independent Test**: cambiar nombre devuelve 200 con registro en historial; cambiar capacidad con galpon Disponible devuelve 409.

### Tests

- [ ] T075 [P] [US9] Prueba de contrato PATCH galpon: guardar el mismo nombre no es duplicado; cambiar capacidad fuera de Mantenimiento devuelve 409 — clase `GalponControllerTest`

### Implementation

- [ ] T076 [P] [US9] Crear EditarGalponRequest en main/infrastructure/in/rest/dto/ (ambos campos opcionales)
- [ ] T077 [P] [US9] Crear EditarGalponUseCase en main/application/port/in/
- [ ] T078 [US9] Implementar GalponService.editar en main/application/usecase/: unicidad excluyendo el propio galpon, capacidad solo en MANTENIMIENTO, un registro en HistorialCambioGalponRepository por campo cambiado
- [ ] T079 [US9] Implementar endpoint PATCH galpon en GalponController

**Checkpoint**: los 10 casos de uso del Modulo 1 funcionan de forma independiente.

## Dependencias

- Ninguna entre historias.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Edge case del spec: si existiera un lote asociado a un galpón en MANTENIMIENTO, "se puede validar" que se impida editar la capacidad. Decidir si se implementa; la propuesta es rechazarlo con 409.
