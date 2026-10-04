# Implementation Plan: Consultar galpón y lote

**Fecha**: 2026-10-02
**Spec**: [consultar-galpón-lote.md](consultar-galpón-lote.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US3, prioridad P1)

## Summary

Administrador, Operario, Módulo 2 y Módulo 3 consultan el listado de galpones, el detalle con su lote activo y el historial de lotes.

## Contexto específico

- **Tareas previas del plan general**: T006, T008, T010, T013, T014.

## Clases y adaptadores propios

- `GalponResumenResponse`, `GalponDetalleResponse`, `PaginaResponse`
- `LoteRestMapper` (ajuste: `edadDias`)
- `ConsultarGalponUseCase`
- `GalponService` (listado y detalle)
- `GalponController` (GET)

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `GET /api/v1/galpones` | Query opcional: `nombre` (búsqueda parcial sin distinguir mayúsculas; se recortan espacios), `estado`, `orden` (`nombre`, `capacidad` o `estado`, con `asc` o `desc`; por defecto `nombre,asc`) y `page` (desde 0). Tamaño fijo de 10. | 200 con página de `{id, nombre, capacidad, estado}`, `totalElementos`, `totalPaginas` y `sinCoincidencias`. |
| `GET /api/v1/galpones/{id}` | UUID en la ruta. | 200 con el galpón y `loteActivo` (o `null`). |
| `GET /api/v1/galpones/{id}/lotes` | UUID en la ruta. | 200 con el lote activo y los lotes anteriores. |

```json
// GET /api/v1/galpones/{id} con lote activo
{
  "id": "550e8400-e29b-41d4-a716-446655440000", "nombre": "Galpón Norte", "capacidad": 1500, "estado": "PRODUCTIVO",
  "loteActivo": { "id": "2fc03a21-84c4-4c83-bb15-6607bb75cb90", "nombre": "Lote A1",
    "poblacionInicial": 1000, "poblacionActual": 975, "fechaIngreso": "2026-09-15",
    "edadDias": 17, "costoTotal": 5000000 },
  "mensaje": null, "camposNoDisponibles": []
}
```

Reglas de la respuesta:
- Sin coincidencias en la búsqueda: 200 con **todos** los galpones y `sinCoincidencias: true`.
- Galpón sin lote: 200 con `loteActivo: null` y `mensaje: "No existen lotes registrados para este galpón"`.
- Campo del lote vacío o inválido: se devuelve `null` y se lista en `camposNoDisponibles`; el cliente muestra "No disponible" y el resto de los datos sigue válido.
- Lote con UUID inválido: no se incluye como lote consultable y se informa en `advertencias`.
- Conservar búsqueda, filtros y orden ante un error es responsabilidad del cliente: la API no guarda estado.

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Criterio de orden, estado o página inválidos | 400 | `PARAMETRO_INVALIDO` |
| El galpón no existe | 404 | `GALPON_NO_ENCONTRADO` |
| Error técnico al recuperar los datos | 500 | `ERROR_CONSULTA` (mensaje específico) |

## Fuera de alcance

- Solo lectura: no crea ni modifica galpones ni lotes.
- La diferencia de permisos entre administrador y operario queda pendiente en el spec (FR-022); el plan general deja la autenticación fuera del alcance.
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: Administrador, Operario, Modulo 2 y Modulo 3 consultan el listado de galpones, el detalle con el lote activo y el historial de lotes.

**Independent Test**: con 15 galpones el listado devuelve 10 paginados; busqueda sin resultados devuelve todos con sinCoincidencias true; detalle sin lote devuelve loteActivo null.

### Tests

- [ ] T037 [P] [US3] Prueba de contrato: listado de galpones, detalle de galpon con lote activo e historial de lotes — incluidos 404 y criterio de orden invalido — clase `GalponControllerTest`
- [ ] T038 [P] [US3] Prueba de persistencia: busqueda parcial sin mayusculas, filtro por estado, paginacion y lote activo (desvinculado_en IS NULL) — clase `GalponPersistenceTest`

### Implementation

- [ ] T039 [P] [US3] Crear GalponResumenResponse, GalponDetalleResponse y PaginaResponse en main/infrastructure/in/rest/dto/; actualizar LoteRestMapper con calculo de edadDias usando Clock
- [ ] T040 [P] [US3] Crear ConsultarGalponUseCase en main/application/port/in/
- [ ] T041 [US3] Implementar en GalponService: listado paginado (10), busqueda parcial con reintento sin filtro, filtro por estado, orden permitido; detalle con lote activo (desvinculado_en IS NULL) e historial
- [ ] T042 [US3] Implementar endpoints GET galpones en GalponController

**Checkpoint**: la consulta sirve para verificar las historias siguientes.

## Dependencias

- Ninguna entre historias. Sirve para verificar los resultados de las demás.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- Pregunta abierta 4 del plan general: confirmar si el Operario también ve alertas de mantenimiento.
- Lote activo: FR-013 del spec dice "el de fecha de ingreso más reciente"; el plan (T038, T041) usa `desvinculado_en IS NULL` porque el spec de vaciado sanitario desvincula el lote. Con el criterio del spec, un galpón en vaciado seguiría mostrando su último lote como activo. Confirmar cuál se mantiene.
- Edad en días: el spec no dice si el día de ingreso cuenta como día 1. Definir y fijarlo en una prueba (por ejemplo ingreso hoy = 0 o 1).
