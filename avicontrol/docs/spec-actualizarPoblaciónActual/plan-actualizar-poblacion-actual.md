# Implementation Plan: Actualizar población actual

**Fecha**: 2026-10-02
**Spec**: [actualizarPoblaciónActual.md](actualizarPoblaciónActual.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US5, prioridad P1)

## Summary

Descuenta pollos de la población actual del lote activo, sin modificar la población inicial y sin superar la población actual. Lo invoca el flujo de mortalidad.

## Contexto específico

- Las tareas T050, T051 y T055 se comparten con [plan-recibir-mortalidad](../spec-recibirMortalidadDelGalpón/plan-recibir-mortalidad.md); aquí solo se listan sus partes de población.
- **Tareas previas del plan general**: T006, T008, T010.

## Clases y adaptadores propios

- `PoblacionResponse` (T050, parte población)
- `ActualizarPoblacionUseCase` (T051, parte población)
- `PoblacionService.descontar` (T052)

## Contrato

> El spec no define un endpoint: la solicitud llega siempre desde el spec Recibir mortalidad. El contrato es el puerto de entrada interno `ActualizarPoblacionUseCase`.

| Elemento | Definición |
|---|---|
| Entrada (comando) | `alertaId` (UUID), `loteId` (UUID), `galponId` (UUID), `cantidadMuertos` (entero mayor que 0). |
| Resultado | `loteId`, `poblacionAnterior`, `poblacionActual`. La población inicial no cambia. |
| Fórmula | `poblacionActual = poblacionActual anterior - cantidadMuertos` (puede quedar en 0, nunca negativa). |

| Caso (spec) | Excepción de dominio |
|---|---|
| El lote no existe | `RecursoNoEncontradoException` |
| El lote no es activo o su `galpon_id` no corresponde al galpón recibido | `ValidacionNegocioException` |
| Cantidad 0, negativa o decimal (el decimal ya lo rechaza Jackson, `ACCEPT_FLOAT_AS_INT = false`) | `ValidacionNegocioException` |
| Cantidad mayor que la población actual | `ValidacionNegocioException` |
| Mismo `alertaId` con lote o cantidad distintos | `ConflictoException` |
| Mismo `alertaId` y mismos datos (reenvío) | No es error: devuelve el resultado original sin descontar de nuevo |

La validación y la persistencia ocurren en una sola transacción; si falla, la población conserva su valor anterior. El `alertaId` y los datos aplicados se guardan en `RegistroProcesamiento`.

## Fuera de alcance

- No recibe ni valida la alerta de Kafka: eso lo hace [plan-recibir-mortalidad](../spec-recibirMortalidadDelGalpón/plan-recibir-mortalidad.md).
- No cambia el estado del galpón ni inicia el vaciado sanitario cuando la población llega a cero (FR-011, FR-018).
- No modifica nombre, fecha de ingreso, costo, población inicial ni `galpon_id` del lote.

## Tareas

**Goal**: El Modulo 1 descuenta de la poblacion actual del lote activo la cantidad indicada, sin tocar la poblacion inicial y sin dejarla en negativo.

**Independent Test**: lote de 1000 pollos, descuento de 25 -> poblacion actual 975 y poblacion inicial 1000; descuento mayor a la poblacion actual es rechazado.

### Implementation

- [ ] T050 [P] [US5] Crear PoblacionResponse en main/infrastructure/in/rest/dto/ (parte población de T050)
- [ ] T051 [P] [US5] Crear ActualizarPoblacionUseCase en main/application/port/in/ (parte población de T051)
- [ ] T052 [US5] Implementar PoblacionService.descontar en main/application/usecase/: lote activo, cantidad menor o igual a poblacion actual, poblacion inicial intacta
- [ ] T055 [US5] Pruebas unitarias de PoblacionService en test/application/ (parte población de T055): descuentos sucesivos (25 + 10 sobre 1000 = 965); cantidad mayor a la poblacion actual rechazada; poblacion en cero sin cambio de estado del galpon — clase `PoblacionServiceTest`

**Checkpoint**: la poblacion actual del lote refleja los descuentos.

## Dependencias

- Necesita un lote activo creado con datos de prueba. Lo usa el spec de mortalidad.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Decisiones pendientes

- T050 crea `PoblacionResponse` en `rest/dto/`, pero el spec no pide un endpoint. Decidir si se expone alguno o si basta el resultado interno; en el segundo caso, mover ese DTO a `application`.
