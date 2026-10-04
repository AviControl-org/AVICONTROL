# Implementation Plan: Actualizar estado del galpón

**Fecha**: 2026-10-02
**Spec**: [actualizar-estados-spec.md](actualizar-estados-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US4, prioridad P1)

## Summary

El administrador cambia el estado de un galpón por las transiciones que le corresponden; cada cambio queda en el historial de estados.

## Contexto específico

- **Tareas previas del plan general**: T015 (`TransicionesPermitidas`), T016 (`GalponEstadoService`).

## Clases y adaptadores propios

- `ActualizarEstadoRequest`, `HistorialEstadoResponse`
- `ActualizarEstadoUseCase`
- `GalponService` (transiciones del administrador)
- `GalponController` (endpoints de estado)

## Contrato

> Las rutas, nombres de campos y códigos de error son **propuestos** en este plan: los specs describen el comportamiento, no el contrato. Formato de error: ver `arquitectura.md` (ProblemDetail con `code` y `fieldErrors`).

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| `GET /api/v1/galpones/{id}/transiciones` | UUID en la ruta. | 200 con `estadoActual` y los estados destino que el administrador puede solicitar. |
| `POST /api/v1/galpones/{id}/estado` | Body: `estadoEsperado` y `estadoDestino`. | 200 con `estadoAnterior`, `estadoNuevo`, `origen` y `fechaHora`. |
| `GET /api/v1/galpones/{id}/historial-estados` | UUID en la ruta. | 200 con lista de `{id, estadoAnterior, estadoNuevo, fechaHora, origen}`. |

```json
// POST /api/v1/galpones/{id}/estado
{ "estadoEsperado": "PRODUCTIVO", "estadoDestino": "EN_COSECHA" }
```

**Matriz de transiciones (FR-002)** — única autoridad para cambiar estado; los demás specs la invocan por `GalponEstadoService`:

| Desde | Hacia | Quién la solicita | Origen registrado |
|---|---|---|---|
| DISPONIBLE | PRODUCTIVO | Registrar lote | `REGISTRO_LOTE` |
| DISPONIBLE | MANTENIMIENTO | Administrador al atender alerta | `ADMINISTRADOR` |
| PRODUCTIVO | EN_COSECHA | Administrador | `ADMINISTRADOR` |
| PRODUCTIVO | AISLAMIENTO | Alerta sanitaria (Módulo 2) | `ALERTA_SANITARIA_MODULO_2` |
| EN_COSECHA | VACIADO_SANITARIO | Recibir vaciado sanitario | `ALERTA_VACIADO_SANITARIO` |
| VACIADO_SANITARIO | DISPONIBLE | Proceso automático | `PROCESO_AUTOMATICO` |
| MANTENIMIENTO | DISPONIBLE | Administrador | `ADMINISTRADOR` |
| AISLAMIENTO | PRODUCTIVO | Alerta sanitaria de reanudación | `ALERTA_SANITARIA_MODULO_2` |
| AISLAMIENTO | EN_COSECHA | Administrador | `ADMINISTRADOR` |

Por el endpoint REST solo se aceptan las tres del administrador. Las demás entran por el puerto interno `solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)`.

| Caso (spec) | Estado HTTP | `code` |
|---|---|---|
| Estado destino fuera del enum | 400 | `ESTADO_INVALIDO` |
| El galpón no existe | 404 | `GALPON_NO_ENCONTRADO` |
| Transición fuera de la matriz, incluida DISPONIBLE -> PRODUCTIVO manual y AISLAMIENTO -> VACIADO_SANITARIO | 409 | `TRANSICION_NO_PERMITIDA` |
| Origen sin autorización para esa transición | 409 | `ORIGEN_NO_AUTORIZADO` |
| El estado vigente ya no es `estadoEsperado` (solicitudes simultáneas) | 409 | `ESTADO_ESPERADO_CAMBIADO` |

El estado vigente se valida dentro de la misma transacción que persiste el cambio (bloqueo pesimista por galpón, T016). El historial guarda galpón, estado anterior, estado nuevo, marca de tiempo y origen; el usuario se omite porque la autenticación no está especificada (FR-008).

## Fuera de alcance

- No usa la población actual como condición para pasar a VACIADO_SANITARIO (FR-011); eso lo decide el flujo de vaciado.
- Los demás specs validan y registran su propio evento, pero no escriben el estado (FR-012).
- El mensaje de confirmación del administrador (FR-006) es del cliente; la API recibe la solicitud ya confirmada.
- Los comportamientos de interfaz del spec (formulario, mensaje de confirmación, redirección al listado) son responsabilidad del cliente; la API solo expone el resultado.

## Tareas

**Goal**: El administrador cambia el estado de un galpon por las transiciones que le corresponden; el historial registra cada cambio.

**Independent Test**: galpon Productivo pasa a En cosecha con la solicitud de actualizacion; historial muestra origen ADMINISTRADOR; pedir Productivo desde Disponible devuelve 409.

### Tests

- [ ] T043 [P] [US4] Prueba de contrato: transiciones disponibles para el admin, aplicar transicion, historial de estados — incluida transicion invalida y estado ya cambiado por concurrencia — clase `GalponControllerTest`

### Implementation

- [ ] T044 [P] [US4] Crear ActualizarEstadoRequest y HistorialEstadoResponse en main/infrastructure/in/rest/dto/
- [ ] T045 [P] [US4] Crear ActualizarEstadoUseCase en main/application/port/in/
- [ ] T046 [US4] Implementar en GalponService las transiciones del administrador (PRODUCTIVO->EN_COSECHA, AISLAMIENTO->EN_COSECHA, MANTENIMIENTO->DISPONIBLE) llamando a GalponEstadoService con origen ADMINISTRADOR
- [ ] T047 [US4] Implementar endpoints de estado en GalponController

**Checkpoint**: el ciclo de vida manual del galpon funciona.

## Dependencias

- Solo del plan general (Foundational).
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- T007: el enum `OrigenCambioEstado` debe incluir `ALERTA_VACIADO_SANITARIO` y `PROCESO_AUTOMATICO`, además de los tres ya previstos.
