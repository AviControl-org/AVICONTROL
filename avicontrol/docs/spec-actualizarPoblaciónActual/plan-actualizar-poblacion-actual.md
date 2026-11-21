# Implementation Plan: Actualizar población actual

**Fecha**: 2026-10-02
**Spec**: [actualizarPoblaciónActual.md](actualizarPoblaciónActual.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md) (historia US5, prioridad P1)

## Summary

Descuenta pollos de la población actual del lote activo, sin modificar la población inicial y sin superar la población actual. Lo invoca el flujo de mortalidad.

**Enfoque técnico:**

1. El spec de mortalidad llama a `ActualizarPoblacionUseCase.actualizar(comando)` dentro de su misma transacción; no hay servicio REST (FR-001).
2. `PoblacionService.descontar` carga el lote, exige que sea el activo del galpón recibido y que la cantidad sea un entero positivo no mayor que la población actual (FR-003 a FR-006).
3. Aplica `Lote.descontar(cantidad)` (FR-007), que no toca la población inicial ni los demás atributos (FR-009, FR-010), y guarda el lote.
4. Escribe el `RegistroProcesamiento` con el `alertaId` y los datos aplicados en la misma transacción (FR-015, FR-017). Un mismo `alertaId` con los mismos datos devuelve el resultado original; con datos distintos se rechaza (FR-013, FR-014).

## Contexto específico

- Las tareas T050, T051 y T055 se comparten con [plan-recibir-mortalidad](../spec-recibirMortalidadDelGalpón/plan-recibir-mortalidad.md); aquí solo se listan sus partes de población.
- **Tareas previas del plan general**: T006, T008, T010.

## Estructura del proyecto

```text
src/main/java/com/unimag/avicontrol/
├── domain/model/Lote.java                                # ajuste: descontar(cantidad) (T052)
└── application/
    ├── port/in/
    │   ├── ActualizarPoblacionUseCase.java               # T051 (parte población)
    │   ├── ActualizarPoblacionCommand.java               # T051
    │   └── ActualizarPoblacionResultado.java             # T051
    └── usecase/PoblacionService.java                      # T052

src/test/java/com/unimag/avicontrol/
├── domain/model/LotePoblacionTest.java                    # T099
└── application/PoblacionServiceTest.java                  # T055 (parte población)
```

## Decisiones de diseño

**Un solo escritor del registro.** `PoblacionService` es el único que escribe `RegistroProcesamiento`; `MortalidadService` solo lo consulta para decidir si ignora un duplicado o rechaza una inconsistencia. Así no hay dos filas ni dos transacciones para el mismo `alertaId`.

**Concurrencia entre dos mortalidades del mismo lote.** `LoteEntity` tiene `@Version` (T008). Si dos descuentos chocan, el segundo falla con `OptimisticLockingFailureException`, que es un error técnico reintentable: el consumer lo reintenta y recalcula sobre el valor nuevo (P-05). No se usa bloqueo pesimista del lote.

**Cero y negativo.** `Lote.descontar` permite que la población llegue a cero y nunca produce un valor negativo (FR-012). Llegar a cero no cambia el estado del galpón ni inicia el vaciado (FR-011, FR-018).

**Atomicidad.** Validar, descontar y registrar es una sola operación; si algo falla, la población conserva su valor anterior (FR-015, FR-016, FR-019).

## Contrato

> El spec no define un servicio REST: la solicitud llega siempre desde el spec Recibir mortalidad. El contrato es el puerto de entrada interno `ActualizarPoblacionUseCase`.

### Puerto interno — `ActualizarPoblacionUseCase.actualizar(comando)`

| Elemento | Definición |
|---|---|
| Request | `ActualizarPoblacionCommand` |
| Response | `ActualizarPoblacionResultado` |
| Path / query params | No aplica (no es HTTP) |

**Comando (request)**

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| alertaId | UUID | sí | Clave de idempotencia |
| loteId | UUID | sí | El lote debe existir y estar activo |
| galponId | UUID | sí | Debe ser el galpón del lote |
| cantidadMuertos | integer | sí | Entero mayor que 0 y no mayor que la población actual |

**Resultado (response)**

| Campo | Tipo | Descripción |
|---|---|---|
| loteId | UUID | Lote actualizado. |
| poblacionAnterior | integer | Antes del descuento. |
| poblacionActual | integer | `poblacionAnterior - cantidadMuertos`; puede ser 0, nunca negativa. |

**Errores**

| Excepción de dominio | Caso (spec) |
|---|---|
| `RecursoNoEncontradoException` | El lote no existe |
| `ValidacionNegocioException` | Lote no activo o `galpon_id` distinto del recibido; cantidad 0, negativa o decimal (el decimal ya lo rechaza Jackson, `ACCEPT_FLOAT_AS_INT = false`); cantidad mayor que la población actual |
| `ConflictoException` | Mismo `alertaId` con lote o cantidad distintos |
| (sin error) | Mismo `alertaId` y mismos datos: devuelve el resultado original sin descontar de nuevo |

**Códigos de error del puerto**

| Excepción | `code` | Mensaje fijo |
|---|---|---|
| `RecursoNoEncontradoException` | `LOTE_NO_ENCONTRADO` | El lote de la alerta no existe. |
| `ValidacionNegocioException` | `LOTE_NO_PERTENECE_AL_GALPON` | El lote no corresponde al galpón de la alerta. |
| `ValidacionNegocioException` | `LOTE_NO_ACTIVO` | El lote no es el lote activo del galpón. |
| `ValidacionNegocioException` | `CANTIDAD_INVALIDA` | La cantidad de muertos debe ser un entero positivo. |
| `ValidacionNegocioException` | `CANTIDAD_EXCEDE_POBLACION` | La cantidad de muertos supera la población actual del lote. |
| `ConflictoException` | `ALERTA_INCONSISTENTE` | El alertaId ya fue procesado con datos distintos. |

La validación y la persistencia son una sola transacción; si falla, la población conserva su valor anterior. El `alertaId` y los datos aplicados se guardan en `RegistroProcesamiento`.

## Peticiones y respuestas por escenario

Este spec no tiene servicio REST ni mensaje propio: lo invoca el spec de mortalidad dentro de su transacción. Cada escenario muestra el comando (request) y el resultado o la excepción (response). Sin path ni query params.

**1. Descuento válido**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: En una transacción: el lote baja de 500 a 475 pollos, `poblacion_inicial` no cambia y queda la fila de `registro_procesamiento` con el `alertaId`.

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 25
}
```

Resultado:

```json
{
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "poblacionAnterior": 500,
  "poblacionActual": 475
}
```

**2. Descuento que deja la población en cero**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: Llegar a cero no cambia el estado del galpón ni inicia el vaciado.

Argumentos:

```json
{
  "alertaId": "3e7d1c40-8b52-4a96-b0f1-6d2c9e4a7b13",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 475
}
```

Resultado:

```json
{
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "poblacionAnterior": 475,
  "poblacionActual": 0
}
```

**3. Mismo `alertaId` con los mismos datos (reentrega)**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: Devuelve el resultado original: no descuenta de nuevo ni escribe otra fila.

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 25
}
```

Resultado:

```json
{
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "poblacionAnterior": 500,
  "poblacionActual": 475
}
```

**4. Rechazo: Lote inexistente**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "d9b2d63d-a233-4123-847a-3f2c0c1e6b55",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 25
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "RecursoNoEncontradoException",
  "codigo": "LOTE_NO_ENCONTRADO",
  "mensaje": "El lote de la alerta no existe."
}
```

**5. Rechazo: El lote no es el de ese galpón**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  "cantidadMuertos": 25
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ValidacionNegocioException",
  "codigo": "LOTE_NO_PERTENECE_AL_GALPON",
  "mensaje": "El lote no corresponde al galpón de la alerta."
}
```

**6. Rechazo: El lote ya no es el activo del galpón (histórico)**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "9a1f4c70-2d3b-4a55-8e61-0b7f3c9d2e18",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 25
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ValidacionNegocioException",
  "codigo": "LOTE_NO_ACTIVO",
  "mensaje": "El lote no es el lote activo del galpón."
}
```

**7. Rechazo: Cantidad cero**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: Igual con una cantidad negativa (`-3`). Una cantidad decimal ni llega aquí: la rechaza Jackson al leer el mensaje.

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 0
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ValidacionNegocioException",
  "codigo": "CANTIDAD_INVALIDA",
  "mensaje": "La cantidad de muertos debe ser un entero positivo."
}
```

**8. Rechazo: Cantidad mayor que la población actual**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: La población conserva su valor anterior.

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 501
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ValidacionNegocioException",
  "codigo": "CANTIDAD_EXCEDE_POBLACION",
  "mensaje": "La cantidad de muertos supera la población actual del lote."
}
```

**9. Rechazo: Mismo `alertaId` con datos distintos**

- **Llamada**: `ActualizarPoblacionUseCase.actualizar(comando)`
- **Nota**: El registro original no se sobrescribe.

Argumentos:

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "cantidadMuertos": 30
}
```

Se rechaza con la excepción de negocio (no hay HTTP; el consumer la registra en el log):

```json
{
  "excepcion": "ConflictoException",
  "codigo": "ALERTA_INCONSISTENTE",
  "mensaje": "El alertaId ya fue procesado con datos distintos."
}
```

## Código de implementación

Código completo de este caso de uso, tal como compila en el proyecto (paquete base `com.unimag.avicontrol`). Las clases compartidas por varios casos de uso (`GalponService`, `GalponController`, los mappers y los adaptadores) muestran aquí solo los métodos de este plan; el resto está en el plan de cada caso de uso.

**`src/main/java/com/unimag/avicontrol/application/port/in/ActualizarPoblacionUseCase.java`**

```java
package com.unimag.avicontrol.application.port.in;

public interface ActualizarPoblacionUseCase {

    ActualizarPoblacionResultado actualizar(ActualizarPoblacionCommand comando);
}
```

**`src/main/java/com/unimag/avicontrol/application/port/in/ActualizarPoblacionCommand.java`**

```java
package com.unimag.avicontrol.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record ActualizarPoblacionCommand(UUID alertaId, UUID loteId, UUID galponId, long cantidadMuertos,
                                         Instant fechaHoraEvento) {
}
```

**`src/main/java/com/unimag/avicontrol/application/port/in/ActualizarPoblacionResultado.java`**

```java
package com.unimag.avicontrol.application.port.in;

import java.util.UUID;

/** Si `duplicado` es true, el `alertaId` ya estaba aplicado: las cifras son la población vigente. */
public record ActualizarPoblacionResultado(UUID loteId, long poblacionAnterior, long poblacionActual,
                                           boolean duplicado) {
}
```

**`src/main/java/com/unimag/avicontrol/application/usecase/PoblacionService.java`**

```java
package com.unimag.avicontrol.application.usecase;

import com.unimag.avicontrol.application.port.in.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.ActualizarPoblacionResultado;
import com.unimag.avicontrol.application.port.in.ActualizarPoblacionUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.Lote;
import com.unimag.avicontrol.domain.model.RegistroProcesamiento;
import com.unimag.avicontrol.domain.port.out.LoteRepository;
import com.unimag.avicontrol.domain.port.out.RegistroProcesamientoRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Único escritor de RegistroProcesamiento; MortalidadService solo lo consulta. */
@Service
public class PoblacionService implements ActualizarPoblacionUseCase {

    private final LoteRepository lotes;
    private final RegistroProcesamientoRepository registros;
    private final Clock clock;

    public PoblacionService(LoteRepository lotes, RegistroProcesamientoRepository registros, Clock clock) {
        this.lotes = lotes;
        this.registros = registros;
        this.clock = clock;
    }

    /**
     * Valida, descuenta y registra en una sola transacción: si algo falla, la población conserva su valor (FR-015).
     * Dos descuentos simultáneos del mismo lote chocan por @Version y el segundo se reintenta (P-05).
     */
    @Override
    @Transactional
    public ActualizarPoblacionResultado actualizar(ActualizarPoblacionCommand comando) {
        Lote lote = lotes.findById(comando.loteId()).orElseThrow(() ->
                new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));

        var previo = registros.findById(comando.alertaId());
        if (previo.isPresent()) {
            boolean mismosDatos = previo.get().mismosDatos(comando.galponId(), comando.loteId(),
                    comando.cantidadMuertos(), comando.fechaHoraEvento());
            if (!mismosDatos) {
                throw new ConflictoException("ALERTA_INCONSISTENTE",
                        "El alertaId ya fue procesado con datos distintos.");
            }
            return new ActualizarPoblacionResultado(lote.id(), lote.poblacionActual(), lote.poblacionActual(), true);
        }

        if (!lote.galponId().equals(comando.galponId())) {
            throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                    "El lote no corresponde al galpón de la alerta.");
        }
        if (!lote.activo()) {
            throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no es el lote activo del galpón.");
        }

        Lote actualizado = lotes.save(lote.descontar(comando.cantidadMuertos()));
        registros.save(new RegistroProcesamiento(comando.alertaId(), comando.galponId(), comando.loteId(),
                comando.cantidadMuertos(), comando.fechaHoraEvento(), clock.instant()));
        return new ActualizarPoblacionResultado(lote.id(), lote.poblacionActual(), actualizado.poblacionActual(), false);
    }
}
```

## Fuera de alcance

- No recibe ni valida la alerta de Kafka: eso lo hace [plan-recibir-mortalidad](../spec-recibirMortalidadDelGalpón/plan-recibir-mortalidad.md).
- No cambia el estado del galpón ni inicia el vaciado sanitario cuando la población llega a cero (FR-011, FR-018).
- No modifica nombre, fecha de ingreso, costo, población inicial ni `galpon_id` del lote.

## Tareas

**Goal**: El Modulo 1 descuenta de la poblacion actual del lote activo la cantidad indicada, sin tocar la poblacion inicial y sin dejarla en negativo.

**Independent Test**: lote de 1000 pollos, descuento de 25 -> poblacion actual 975 y poblacion inicial 1000; descuento mayor a la poblacion actual es rechazado.

### Implementation

- [ ] T050 [P] [US5] Sin código propio en este plan: el spec no pide servicio REST, así que no hay `PoblacionResponse`; el resultado del caso de uso es `ActualizarPoblacionResultado` (T051) (FR-002; P-16)
- [ ] T051 [P] [US5] Crear ActualizarPoblacionUseCase en main/application/port/in/ (parte población de T051) (FR-001, FR-002)
- [ ] T052 [US5] Implementar PoblacionService.descontar en main/application/usecase/: lote activo, cantidad menor o igual a poblacion actual, poblacion inicial intacta (FR-003 a FR-009, FR-012, FR-015)
- [ ] T055 [US5] Pruebas unitarias de PoblacionService en test/application/ (parte población de T055): descuentos sucesivos (25 + 10 sobre 1000 = 965); cantidad mayor a la poblacion actual rechazada; poblacion en cero sin cambio de estado del galpon — clase `PoblacionServiceTest` (FR-006, FR-007, FR-009, FR-012 a FR-014; SC-001 a SC-006)
- [ ] T099 [P] [US5] Prueba unitaria de dominio `Lote.descontar`: 1000 - 25 = 975 con población inicial intacta; descontar toda la población deja 0; descontar más que la población actual lanza excepción de negocio; cantidad 0 o negativa rechazada — clase `LotePoblacionTest` (FR-005 a FR-007, FR-009, FR-012; SC-002, SC-003, SC-006)

**Checkpoint**: la poblacion actual del lote refleja los descuentos.

## Dependencias

- Necesita un lote activo creado con datos de prueba. Lo usa el spec de mortalidad.
- Orden interno: modelo y entidad -> puerto de entrada -> caso de uso -> adaptador de entrada (REST o Kafka) -> pruebas.

## Pendientes de clarificación

- P-16: el spec no pide servicio REST, por eso el plan no crea `PoblacionResponse` y el resultado es `ActualizarPoblacionResultado` en `application`. Si el equipo decide exponerlo por REST, se agrega el DTO en `rest/dto/`.
- P-05: política de reintentos para el choque de versión del lote.
