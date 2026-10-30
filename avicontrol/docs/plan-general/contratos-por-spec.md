# Contratos por spec que deben coincidir con los demás módulos - AVICONTROL

**Fecha**: 2026-10-05
**Autor**: Módulo 1 - Gestión de Galpones e Infraestructura
**Destinatarios**: Módulo 2 y Módulo 3
**Estado**: propuesta del Módulo 1, pendiente de acordar. Los IDs `P-nn` son los pendientes de clarificación de `plan-modulo-1.md` y de los planes por spec.
**Complementa a**: [acuerdo-contratos-entre-modulos.md](acuerdo-contratos-entre-modulos.md) (reglas comunes, sobre de eventos y detalle de los eventos y de la consulta) y a [arquitectura.md](../arquitectura/arquitectura.md) (convenciones técnicas).

## 1. Cómo usar este documento

Cada uno de los 10 specs del Módulo 1 tiene aquí su contrato (ruta o tópico, params, body, respuesta y códigos de error) y una lista de **lo que debe ser idéntico** en los otros módulos. Los escenarios completos con sus respuestas están en el plan de cada spec, sección *Peticiones y respuestas por escenario*.

| Clase | Qué significa | Specs |
|---|---|---|
| **A. Frontera de entrada** | El Módulo 2 publica y el Módulo 1 consume. Nombres y valores deben coincidir **letra por letra**. | Mortalidad, alerta sanitaria, vaciado sanitario |
| **B. Frontera de lectura** | Los Módulos 2 y 3 llaman por REST al Módulo 1. | Consultar galpón y lote |
| **C. Efecto visible** | Solo los usa el frontend del Módulo 1, pero cambian datos o estados que los otros módulos leen. | Registrar galpón, registrar lote, actualizar estado, editar galpón, actualizar población, alerta de mantenimiento |

Los specs C no obligan a los otros módulos a llamar nada, pero lo que escriben (nombres, estados, población) es lo que luego leen por la clase B.

## 2. Reglas que valen para todos los contratos

| Tema | Regla |
|---|---|
| Formato | JSON UTF-8, campos en `camelCase`, enumeraciones en `MAYUSCULA_CON_GUION_BAJO` |
| Identificadores | UUID como texto |
| Fechas | `yyyy-MM-dd`; instantes ISO-8601 con desplazamiento (`2026-10-05T14:30:00-05:00`); zona de negocio `America/Bogota` |
| Enteros | Sin decimales (`25` sí, `25.0` y `"25"` no) |
| Nulos | No se envían: el campo que no aplica se omite |
| Estados del galpón | `DISPONIBLE`, `VACIADO_SANITARIO`, `PRODUCTIVO`, `EN_COSECHA`, `MANTENIMIENTO`, `AISLAMIENTO` |
| Base REST | `/api/v1` (P-09, P-10) |
| Error REST | RFC 9457: `type`, `title`, `status`, `detail`, `instance`, `code`, `fieldErrors[{field, code, message}]` (opcional). El cliente decide por `code`, no por `detail` |
| Paginación | `page` desde 1; respuesta `{contenido, pagina, tamanio, totalElementos, totalPaginas}`; página vacía es `200` con `totalPaginas: 0`, nunca `404` |
| Caché | Los `GET` responden `Cache-Control: no-store` |
| Kafka | Sobre estándar con `payload`; clave de partición `galponId`; grupo `modulo-1`; idempotencia por `alertaId` |

## 3. Matriz resumen

| # | Spec | Interfaz | Contraparte | Qué debe coincidir | Pendientes |
|---|---|---|---|---|---|
| 1 | Recibir mortalidad | Kafka `avicontrol.mortalidad` | Módulo 2 publica | Campos del `payload`, `eventType`, clave, `alertaId` | P-05, P-06, P-11 |
| 2 | Recibir alerta sanitaria | Kafka `avicontrol.alerta-sanitaria` | Módulo 2 publica | `accion`, galpón o lote, fecha del día | P-05, P-06, P-13 |
| 3 | Recibir vaciado sanitario | Kafka `avicontrol.vaciado-sanitario` | Módulo 2 publica | Precondición `EN_COSECHA`, `alertaId`, período | P-02, P-05, P-06 |
| 4 | Consultar galpón y lote | REST `GET` | Módulos 2 y 3 consumen | Rutas, query params, forma de `loteActivo`, `incidencias` | P-09, P-10, P-12 |
| 5 | Actualizar población actual | Puerto interno | Ninguna (la invoca mortalidad) | Dueño de `poblacionActual` | P-11, P-16 |
| 6 | Registrar galpón | REST `POST` | Frontend | `nombre`, `capacidad` que luego se leen | P-03 |
| 7 | Registrar lote | REST `POST`, `GET` | Frontend | Campos del lote, `PRODUCTIVO` al registrar | P-08, P-12 |
| 8 | Actualizar estado | REST `PATCH`, `GET` | Frontend | Los seis estados y la matriz de transiciones | P-17 |
| 9 | Editar galpón | REST `PATCH` | Frontend | `nombre`, `capacidad` | P-18 |
| 10 | Alerta de mantenimiento | REST `POST`, `GET`, `PATCH` | Frontend | Estado `MANTENIMIENTO` visible | P-04, P-14, P-15, P-21 |

## 4. Clase A: eventos que publica el Módulo 2

Todos usan el sobre `{eventId, eventType, eventVersion: 1, occurredAt, publishedAt, producer, aggregateType, aggregateId, payload}`. Solo se leen `eventType`, `eventVersion` y `payload`. Valores propuestos de `eventType` (P-06): `MORTALIDAD_REGISTRADA`, `ALERTA_SANITARIA_EMITIDA`, `VACIADO_SANITARIO_SOLICITADO`. Kafka no responde al productor: el resultado de cada mensaje queda en el log del Módulo 1 (P-05).

### 4.1 Recibir mortalidad del galpón

| Elemento | Valor |
|---|---|
| Topic / clave | `avicontrol.mortalidad` / `galponId` |
| Path y query params | No aplican |

`payload`:

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `alertaId` | UUID | sí | Clave de idempotencia; no se reutiliza con datos distintos |
| `galponId` | UUID | sí | Existe y está `PRODUCTIVO` |
| `loteId` | UUID | sí | Es el lote activo de ese galpón |
| `fechaHoraEvento` | instante | sí | No futura y del mismo día de recepción (America/Bogota) |
| `cantidadMuertos` | entero | sí | Mayor que 0, sin decimales |

```json
{
  "alertaId": "9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-05T14:30:00-05:00",
  "cantidadMuertos": 25
}
```

Resultado: se descuenta `cantidadMuertos` de `poblacionActual`; el estado del galpón no cambia (aunque la población llegue a 0). Mismo `alertaId` y mismos datos: se ignora.

**Debe coincidir en el Módulo 2**
- Los cinco nombres de campo y sus tipos; `cantidadMuertos` como entero JSON.
- `galponId` como clave de partición.
- El Módulo 2 es dueño del `alertaId` y lo reenvía idéntico en una reentrega.
- Solo emite mortalidad de galpones `PRODUCTIVO` y de su lote activo (lo ve con la consulta 4.4).
- Una cantidad mayor que la población actual se rechaza: el Módulo 2 debe tener la misma cifra (P-11).

Rechazos: `ALERTA_INCOMPLETA`, `VERSION_NO_SOPORTADA`, `TIPO_EVENTO_NO_SOPORTADO`, `MENSAJE_ILEGIBLE`, `GALPON_NO_ENCONTRADO`, `LOTE_NO_ENCONTRADO`, `LOTE_NO_PERTENECE_AL_GALPON`, `LOTE_NO_ACTIVO`, `GALPON_NO_PRODUCTIVO`, `CANTIDAD_INVALIDA`, `CANTIDAD_EXCEDE_POBLACION`, `FECHA_INVALIDA`, `ALERTA_INCONSISTENTE`.

### 4.2 Recibir alerta sanitaria

| Elemento | Valor |
|---|---|
| Topic / clave | `avicontrol.alerta-sanitaria` / `galponId` (también cuando la alerta nace de un lote) |
| Path y query params | No aplican |

`payload`:

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `alertaId` | UUID | sí | Lo genera el Módulo 2; el Módulo 1 no la guarda (P-13) |
| `accion` | texto | sí | `AISLAMIENTO` o `REANUDACION` |
| `galponId` | UUID | uno de los dos | Galpón afectado |
| `loteId` | UUID | uno de los dos | Lote activo; si vienen ambos deben coincidir |
| `fechaHora` | instante | sí | Del día actual |
| `tipoEnfermedad` | texto | con `AISLAMIENTO` | Texto libre |
| `descripcion` | texto | con `AISLAMIENTO` | Texto libre |
| `gravedad` | texto | no | Texto libre |

```json
{
  "alertaId": "5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa",
  "accion": "AISLAMIENTO",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "fechaHora": "2026-10-05T09:15:00-05:00",
  "tipoEnfermedad": "Sospecha de Newcastle",
  "descripcion": "Mortalidad atípica y signos respiratorios"
}
```

Resultado: `AISLAMIENTO` mueve `PRODUCTIVO -> AISLAMIENTO`; `REANUDACION` mueve `AISLAMIENTO -> PRODUCTIVO`. No se guarda la alerta ni se tocan datos del lote.

**Debe coincidir en el Módulo 2**
- Los dos únicos valores de `accion`, escritos así.
- Enviar `tipoEnfermedad` y `descripcion` en todo `AISLAMIENTO`.
- No reenviar `AISLAMIENTO` sobre un galpón ya aislado: se rechaza como `ACCION_INCOMPATIBLE` (rechazo esperado, no falla).
- `REANUDACION` solo cuando el galpón está en `AISLAMIENTO`.

Rechazos: `ALERTA_INCOMPLETA`, `VERSION_NO_SOPORTADA`, `TIPO_EVENTO_NO_SOPORTADO`, `MENSAJE_ILEGIBLE`, `ACCION_INVALIDA`, `GALPON_O_LOTE_REQUERIDO`, `DATOS_AISLAMIENTO_INCOMPLETOS`, `FECHA_INVALIDA`, `GALPON_NO_ENCONTRADO`, `LOTE_NO_ENCONTRADO`, `LOTE_NO_ACTIVO`, `LOTE_NO_PERTENECE_AL_GALPON`, `ACCION_INCOMPATIBLE`.

### 4.3 Recibir vaciado sanitario

| Elemento | Valor |
|---|---|
| Topic / clave | `avicontrol.vaciado-sanitario` / `galponId` |
| Path y query params | No aplican |

`payload`:

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `alertaId` | UUID | sí | Clave de idempotencia; no se reutiliza con datos distintos |
| `galponId` | UUID | sí | Existe y está `EN_COSECHA` |
| `loteId` | UUID | sí | Pertenece a ese galpón |
| `fechaHoraEvento` | instante | sí | No futura y del mismo día de recepción |

```json
{
  "alertaId": "0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47",
  "galponId": "550e8400-e29b-41d4-a716-446655440000",
  "loteId": "2fc03a21-84c4-4c83-bb15-6607bb75cb90",
  "fechaHoraEvento": "2026-10-05T16:00:00-05:00"
}
```

Resultado, en una sola transacción: se guarda la alerta aceptada, se desvincula el lote y el galpón pasa `EN_COSECHA -> VACIADO_SANITARIO`. Pasado el período configurado, un proceso del Módulo 1 lo devuelve a `DISPONIBLE` sin avisar.

**Debe coincidir en el Módulo 2**
- Emite solo después de ver el galpón en `EN_COSECHA` (lo cambia el administrador del Módulo 1).
- Tras aceptarse, la consulta 4.4 ya no devuelve lote activo: no debe enviar más mortalidad ni alertas de ese lote.
- El Módulo 2 se entera del regreso a `DISPONIBLE` solo consultando el estado (el Módulo 1 no publica eventos).
- El período de vaciado se cuenta desde la recepción de la alerta aceptada y no es un dato del mensaje (P-02).

Rechazos: `ALERTA_INCOMPLETA`, `VERSION_NO_SOPORTADA`, `TIPO_EVENTO_NO_SOPORTADO`, `MENSAJE_ILEGIBLE`, `GALPON_NO_ENCONTRADO`, `LOTE_NO_ENCONTRADO`, `LOTE_NO_PERTENECE_AL_GALPON`, `GALPON_NO_EN_COSECHA`, `FECHA_INVALIDA`, `ALERTA_INCONSISTENTE`.

## 5. Clase B: consultas REST que usan los Módulos 2 y 3

Todos son `GET`, sin body, con `Cache-Control: no-store`. Errores comunes: `400 PARAMETRO_INVALIDO` (UUID o número mal formado; `fieldErrors` nombra el parámetro), `404 GALPON_NO_ENCONTRADO`, `500 ERROR_CONSULTA`.

### 5.1 Listar galpones: `GET /api/v1/galpones`

| Query param | Tipo | Por defecto | Regla |
|---|---|---|---|
| `nombre` | texto | | Búsqueda parcial, sin distinguir mayúsculas |
| `estado` | texto | | Uno de los seis estados |
| `orden` | texto | `nombre,asc` | `campo,dirección`: `nombre`, `capacidad` o `estado`; `asc` o `desc` |
| `page` | entero | 1 | Mayor o igual a 1; tamaño fijo 10 |

Respuesta `200`: página con `contenido[{id, nombre, capacidad, estado}]`, `sinCoincidencias` y, solo cuando `nombre` no coincide con nada, `mensaje` (en ese caso `contenido` trae todos los galpones).

### 5.2 Detalle del galpón: `GET /api/v1/galpones/{galponId}`

Respuesta `200`: `{id, nombre, capacidad, estado, loteActivo?, mensaje?, incidencias?}`. `loteActivo` es `{id, nombre, poblacionInicial, poblacionActual, fechaIngreso, edadDias, costoTotal}`.

### 5.3 Historial de lotes: `GET /api/v1/galpones/{galponId}/lotes`

Respuesta `200`: `{galponId, loteActivo?, lotesAnteriores[], mensaje?, incidencias?}`; los anteriores van del más reciente al más antiguo.

**Debe coincidir en los Módulos 2 y 3**
- Las tres rutas, `nombre`, `estado`, `orden` y `page` escritos así; no existe `size` en estos listados (tamaño fijo 10).
- Un galpón sin lote activo es `200` **sin** el campo `loteActivo`; no se debe esperar `null`.
- `incidencias[{codigo, mensaje}]` informa campos faltantes (`CAMPO_NO_DISPONIBLE`); el cliente muestra "No disponible" y no falla.
- `edadDias` la calcula el Módulo 1 con la fecha de hoy en America/Bogota (P-12); los otros módulos no la recalculan.
- `costoTotal` es entero en pesos colombianos; `poblacionActual` es la cifra vigente (P-11).
- Tratan como estados válidos solo los seis de la sección 2.

## 6. Clase C: specs de uso del frontend con efecto visible

### 6.1 Actualizar población actual (puerto interno)

No hay REST. `ActualizarPoblacionUseCase.actualizar({alertaId, loteId, galponId, cantidadMuertos})` devuelve `{loteId, poblacionAnterior, poblacionActual}` o rechaza con `LOTE_NO_ENCONTRADO`, `LOTE_NO_PERTENECE_AL_GALPON`, `LOTE_NO_ACTIVO`, `CANTIDAD_INVALIDA`, `CANTIDAD_EXCEDE_POBLACION` o `ALERTA_INCONSISTENTE`.

**Debe coincidir con otros módulos**: el Módulo 1 es el único dueño de `poblacionActual` y `poblacionInicial` no cambia nunca (P-11). Si otro módulo necesitara consultar solo la población, habría que abrir un servicio nuevo (P-16, P-20).

### 6.2 Registrar galpón: `POST /api/v1/galpones`

Body: `{nombre (1-100, letras/números/espacios/guiones, único sin distinguir mayúsculas), capacidad (entero > 0)}`. Respuesta `201` con `Location: /api/v1/galpones/{id}`: `{id, nombre, capacidad, estado: "DISPONIBLE"}`.
Errores: `400 NOMBRE_INVALIDO`, `400 CAPACIDAD_INVALIDA`, `409 NOMBRE_DUPLICADO`, `400 VALIDACION_FALLIDA`, `400 CUERPO_INVALIDO`, `500 ERROR_INTERNO`.

**Debe coincidir**: todo galpón nace `DISPONIBLE`; `nombre` y `capacidad` son los mismos que ven las consultas 5.1 y 5.2. La capacidad frente a la población inicial no se valida (P-03).

### 6.3 Registrar lote: `POST /api/v1/lotes` y `GET /api/v1/lotes`

Body del `POST`: `{galponId, nombre, fechaIngreso (yyyy-MM-dd, no futura), poblacionInicial (entero > 0), costoTotal (entero > 0, pesos)}`. Respuesta `201` con `Location`: `{id, nombre, galponId, fechaIngreso, poblacionInicial, poblacionActual, costoTotal}`; `poblacionActual` nace igual a `poblacionInicial`.
`GET /api/v1/lotes?page=1`: página de lotes por fecha de ingreso descendente.
Errores: `400 NOMBRE_INVALIDO`, `400 FECHA_INGRESO_FUTURA`, `400 FECHA_INGRESO_INVALIDA`, `400 POBLACION_INICIAL_INVALIDA`, `400 COSTO_TOTAL_INVALIDO`, `400 GALPON_REQUERIDO`, `404 GALPON_NO_ENCONTRADO`, `409 NOMBRE_DUPLICADO`, `409 GALPON_NO_DISPONIBLE`, `400 VALIDACION_FALLIDA`, `400 CUERPO_INVALIDO`, `400 PARAMETRO_INVALIDO`, `500 ERROR_INTERNO`.

**Debe coincidir**: registrar un lote deja el galpón en `PRODUCTIVO` (origen `REGISTRO_LOTE`); desde ese momento el Módulo 2 puede enviar mortalidad y alertas sanitarias. El `id` del lote es el `loteId` que el Módulo 2 usa en sus eventos.

### 6.4 Actualizar estado del galpón

| Servicio | Contrato |
|---|---|
| `GET /api/v1/galpones/{galponId}/transiciones` | `{galponId, estadoActual, destinosPermitidos[]}` |
| `PATCH /api/v1/galpones/{galponId}/estado` | Body `{estadoEsperado, estadoDestino}`; `200` `{galponId, estadoAnterior, estadoNuevo, origen, fechaHora}` |
| `GET /api/v1/galpones/{galponId}/historial-estados?page=1&size=20` | Página de `{id, galponId, estadoAnterior, estadoNuevo, fechaHora, origen}`, más reciente primero; `size` 1 a 100 |

Errores del `PATCH`: `400 ESTADO_INVALIDO`, `400 VALIDACION_FALLIDA`, `400 CUERPO_INVALIDO`, `400 PARAMETRO_INVALIDO`, `404 GALPON_NO_ENCONTRADO`, `409 TRANSICION_NO_PERMITIDA`, `409 ORIGEN_NO_AUTORIZADO`, `409 ESTADO_ESPERADO_CAMBIADO`.

El administrador solo pide `PRODUCTIVO -> EN_COSECHA`, `AISLAMIENTO -> EN_COSECHA` y `MANTENIMIENTO -> DISPONIBLE`. El resto de las transiciones las piden otros casos de uso con su propio origen:

| Origen (`origen`) | Transición |
|---|---|
| `REGISTRO_LOTE` | `DISPONIBLE -> PRODUCTIVO` |
| `ADMINISTRADOR` | `DISPONIBLE -> MANTENIMIENTO` (al atender una alerta), y las tres anteriores |
| `ALERTA_SANITARIA_MODULO_2` | `PRODUCTIVO -> AISLAMIENTO`, `AISLAMIENTO -> PRODUCTIVO` |
| `ALERTA_VACIADO_SANITARIO` | `EN_COSECHA -> VACIADO_SANITARIO` |
| `PROCESO_AUTOMATICO` | `VACIADO_SANITARIO -> DISPONIBLE` |

**Debe coincidir**: esta tabla es el ciclo de vida que el Módulo 2 debe respetar al emitir eventos (sección 2.2 del acuerdo). Los valores de `origen` son propuestos (P-17).

### 6.5 Editar galpón: `PATCH /api/v1/galpones/{galponId}`

Body (al menos un campo): `{nombre?, capacidad?}`. `200` con el galpón actualizado `{id, nombre, capacidad, estado}`. El `null` se trata como campo ausente (P-23).
Errores: `400 NOMBRE_INVALIDO`, `400 CAPACIDAD_INVALIDA`, `400 SIN_CAMBIOS`, `400 VALIDACION_FALLIDA`, `400 CUERPO_INVALIDO`, `400 PARAMETRO_INVALIDO`, `404 GALPON_NO_ENCONTRADO`, `409 NOMBRE_DUPLICADO`, `409 CAPACIDAD_NO_EDITABLE`, `409 CONFLICTO_CONCURRENCIA`.

**Debe coincidir**: la capacidad solo se edita con el galpón en `MANTENIMIENTO` (en otro estado responde `409 CAPACIDAD_NO_EDITABLE`; con un lote asociado, P-18); los otros módulos ven el nombre y la capacidad vigentes por 5.1 y 5.2, nunca copias.

### 6.6 Alertas de mantenimiento: `/api/v1/alertas-mantenimiento`

| Servicio | Contrato |
|---|---|
| `POST` | Body `{galponId, descripcion (1-500), tecnicoId, severidad?, tipo?}`; `201` con `Location`: `{id, galponId, descripcion, fechaHora, tecnicoId, estado: "PENDIENTE", severidad?, tipo?}` |
| `GET ?estado=PENDIENTE&galponId=&page=1&size=20` | Página de alertas, más recientes primero; `estado`: `PENDIENTE`, `ATENDIDA` o `ELIMINADA`; `size` 1 a 100 |
| `PATCH /{alertaId}` | Body `{estado: "ATENDIDA"}`; `200` `{alerta: {id, estado}, galpon: {id, estado: "MANTENIMIENTO"}}` |

Errores: `400 DESCRIPCION_INVALIDA`, `400 TECNICO_REQUERIDO`, `400 GALPON_REQUERIDO`, `404 GALPON_NO_ENCONTRADO`, `404 ALERTA_NO_ENCONTRADA`, `409 GALPON_NO_DISPONIBLE`, `409 ALERTA_ACTIVA_EXISTENTE`, `409 ALERTA_NO_PENDIENTE`, `409 ESTADO_ESPERADO_CAMBIADO`, `400 ESTADO_INVALIDO`, `400 VALIDACION_FALLIDA`, `400 CUERPO_INVALIDO`, `400 PARAMETRO_INVALIDO`.

**Debe coincidir**: generar la alerta no cambia el estado del galpón; atenderla lo pasa a `MANTENIMIENTO`. `DISPONIBLE <-> MANTENIMIENTO` no genera eventos hacia otros módulos. `tecnicoId` es texto libre mientras no exista el Módulo de usuarios (P-04, P-14), y `severidad` y `tipo` no tienen catálogo (P-15).

## 7. Qué debe confirmar cada módulo

### Módulo 2
1. Aceptar los tres tópicos, la clave `galponId` y el sobre con `eventVersion: 1` (P-05, P-06).
2. Fijar los valores de `eventType` (los de la sección 4 son propuestos).
3. Usar los nombres y tipos exactos de los `payload` de la sección 4 y reenviar con el mismo `alertaId`.
4. Respetar el ciclo de vida de la sección 6.4: mortalidad solo en `PRODUCTIVO`, `REANUDACION` solo en `AISLAMIENTO`, vaciado solo en `EN_COSECHA`.
5. Decidir si necesita respuesta a los rechazos (tópico de respuesta, P-05) o le basta el log.
6. Leer galpones y lotes únicamente por la sección 5.

### Módulo 3
1. Usar las rutas, query params y la forma de respuesta de la sección 5, incluido el `200` sin `loteActivo`.
2. Tratar `incidencias` y `mensaje` como informativos, no como error.
3. Confirmar si necesita algún servicio que ningún spec cubre (P-20).

### Los tres módulos
1. Base `/api/v1` y topología (P-10).
2. Formato de error RFC 9457 y decisión por `code`.
3. Fechas con desplazamiento y zona `America/Bogota`; enteros sin decimales; nulos omitidos.
4. Autenticación y roles, que hoy ningún spec define (P-01).

## 8. Preguntas abiertas que bloquean el acuerdo

| ID | Qué hay que decidir con los otros módulos |
|---|---|
| P-05 | Reintentos, dead-letter y si el Módulo 1 responde los rechazos al Módulo 2 |
| P-06 | Formato del mensaje y valores de `eventType` |
| P-09, P-10 | Rutas, nombres de campo y topología entre módulos |
| P-11 | Quién es dueño de la población actual |
| P-12 | Cómo se calcula la edad del lote |
| P-13 | Identificador de la alerta sanitaria (UUID o funcional) |
| P-20 | Servicios que el Módulo 2 pide y ningún spec cubre |
