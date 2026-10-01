# Implementation Plan: AVICONTROL Módulo 1 - Definición técnica general

**Date**: 2026-09-19
**Spec**: [Casos de uso](../Casos%20de%20uso.md) y los 10 specs del Módulo 1: [registrar galpón](../spec-registrarGalpon/registrar-galpon-spec.md), [editar galpón](../spec-editarGalpon/editar-galpon-spec.md), [registrar lote](../spec-registrarLote/registrar-lote-spec.md), [consultar galpón-lote](../spec-consultar-galpón-lote/consultar-galpón-lote.md), [actualizar estado](../spec-actualizarEstado/actualizar-estados-spec.md), [actualizar población actual](../spec-actualizarPoblaciónActual/actualizarPoblaciónActual.md), [recibir mortalidad del galpón](../spec-recibirMortalidadDelGalpón/recibirMortalidadDelGalpón.md), [recibir alerta sanitaria](../spec-recibirAlertaSanitaria/recibirAlertaSanitaria.md), [recibir vaciado sanitario](../spec-recibirVaciadoSanitario/recibirVaciadoSanitario.md), [generar alerta de mantenimiento](../spec-generarAlertaMantenimiento/generar-alerta-mantenimiento-spec.md)

**Arquitectura y tecnologías**: [000-ArquitecturaYStackTecnologico.md](../plan/000-ArquitecturaYStackTecnologico.md)

## Summary

El Módulo 1 de AVICONTROL gestiona galpones y lotes de una granja avícola mediante los casos de uso descritos en los diez specs: registro y edición, consulta, ciclo de vida por estados, población y mortalidad, alertas sanitarias, vaciado sanitario y mantenimiento. Se implementa con la arquitectura y el stack compartidos enlazados arriba.

La lógica funcional sigue los specs: `Actualizar estado` es el único responsable de persistir las transiciones; `Recibir mortalidad` valida e idempotentemente remite los datos a `Actualizar población actual`, que es quien modifica la población.

## Technical Context

**Performance Goals**: *Propuesto, a validar por el equipo.* Consultas y operaciones de escritura con p95 por debajo de 300 ms; el listado devuelve 10 galpones por página, como pide el spec.
**Constraints**:
- Cada caso de uso que combina validaciones y escrituras debe ser atómico según su spec: registro de lote y cambio de estado; registro de vaciado, desvinculación y solicitud de cambio; validación y registro idempotente de la alerta de mortalidad; y actualización de población.
- Las alertas del Módulo 2 se procesan de forma idempotente por su UUID.
- Nombres de galpón y de lote únicos sin distinguir mayúsculas de minúsculas y sin espacios en los extremos.
- Los campos enteros no aceptan decimales: hay que configurar Jackson para que `10.5` no se convierta en `10`.
- "Hoy" y "fecha futura" se calculan con un `Clock` inyectado y la zona `America/Bogota`.
- La autenticación no está especificada en los specs; queda fuera de este plan.

**Scale/Scope**: una sola granja; del orden de decenas de galpones; un administrador, un técnico de infraestructura y dos integraciones (Módulo 2 y Módulo 3); 10 casos de uso.

## Project Structure

### Documentation (this feature)

```text
avicontrol/docs/
├── plan-general/
│   └── plan-general.md          # Este archivo
├── Casos de uso.md
├── templates/
│   └── spec-template.md
└── spec-<caso-de-uso>/          # Un spec por caso de uso (10 carpetas)
```

### Source Code (repository root)

```text
avicontrol/
├── pom.xml
├── docker-compose.yml                              # PostgreSQL para desarrollo
├── src/
│   ├── main/
│   │   ├── java/com/unimag/avicontrol/
│   │   │   ├── AvicontrolApplication.java
│   │   │   ├── domain/
│   │   │   │   ├── model/                          # Galpon, Lote, historiales, alertas y enums
│   │   │   │   ├── policy/                         # TransicionesPermitidas e invariantes
│   │   │   │   ├── exception/                      # Excepciones del dominio
│   │   │   │   └── port/out/                       # Interfaces de persistencia
│   │   │   ├── application/
│   │   │   │   ├── port/in/                        # Casos de uso expuestos a adaptadores
│   │   │   │   └── usecase/                        # Registrar, consultar, editar, estado, lote y alertas
│   │   │   └── infrastructure/
│   │   │       ├── adapter/in/rest/                # Controladores, DTOs, validación y mappers
│   │   │       ├── adapter/in/scheduling/          # VaciadoSanitarioJob
│   │   │       ├── adapter/out/persistence/
│   │   │       │   ├── entity/                     # Modelos JPA, separados del dominio
│   │   │       │   ├── repository/                 # Spring Data y adaptadores de persistencia
│   │   │       │   └── mapper/                     # JPA <-> dominio
│   │   │       └── config/                         # Spring, Clock, Jackson y planificación
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/                       # V1__..., V2__... (Flyway)
│   └── test/java/com/unimag/avicontrol/
│       ├── domain/                                 # reglas e invariantes del dominio
│       ├── application/                            # casos de uso (Mockito)
│       ├── infrastructure/adapter/in/rest/         # contratos HTTP (MockMvc)
│       ├── infrastructure/adapter/out/persistence/ # repositorios (Testcontainers)
│       ├── support/                                # Clock fijo y datos de prueba
│       └── architecture/                           # Reglas hexagonales con ArchUnit
```

**Structure Decision**: un único proyecto Maven web, con la organización hexagonal compartida definida en el documento de arquitectura. Los casos de uso del Módulo 1 viven en `application/usecase`; los adaptadores REST, programados y JPA se ubican en `infrastructure/adapter`. No se crea un frontend en este alcance.

## Reglas funcionales transversales

- **Autoridad única de estado.** `ActualizarEstadoUseCase.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)` bloquea la fila del galpón (`SELECT ... FOR UPDATE`) mediante el puerto de persistencia, comprueba el estado vigente y la pareja transición-origen en `TransicionesPermitidas`, actualiza el modelo y escribe `historial_estado`. Si otro proceso cambió el estado antes, rechaza la solicitud (FR-013 de actualizar estado).
- **Matriz de transiciones** (`TransicionesPermitidas`), tomada del spec de actualizar estado:

  | Desde | Hacia | Origen autorizado |
  |---|---|---|
  | Disponible | Productivo | `REGISTRO_LOTE` |
  | Disponible | Mantenimiento | `ADMINISTRADOR` (al atender una alerta de mantenimiento) |
  | Productivo | En cosecha | `ADMINISTRADOR` |
  | Productivo | Aislamiento | `ALERTA_SANITARIA_MODULO_2` |
  | En cosecha | Vaciado sanitario | `VACIADO_SANITARIO` |
  | Aislamiento | Productivo | `ALERTA_SANITARIA_MODULO_2` |
  | Aislamiento | Vaciado sanitario | `VACIADO_SANITARIO` |
  | Mantenimiento | Disponible | `ADMINISTRADOR` |
  | Vaciado sanitario | Disponible | `PROCESO_AUTOMATICO` |

- **Atomicidad.** El registro de lote y el cambio de estado, la recepción del vaciado y la desvinculación, y la validación/remisión de mortalidad con la actualización de población deben respetar los límites transaccionales de sus specs. `Recibir mortalidad` remite los datos a `Actualizar población actual`; este último caso de uso es el único que persiste el descuento. Si falla la operación atómica correspondiente, no quedan cambios parciales.
- **Idempotencia de las alertas del Módulo 2.** Mortalidad y vaciado sanitario guardan el UUID de cada alerta aceptada junto con una huella (SHA-256) de sus datos: mortalidad en `registro_procesamiento` (registro técnico mínimo de FR-019) y vaciado en `alerta_vaciado_sanitario`. Un UUID repetido con los mismos datos devuelve el resultado original sin repetir la remisión ni el efecto; con datos distintos se responde `409 Conflict`. La clave primaria evita duplicados concurrentes. La alerta sanitaria no se guarda (FR-004 de su spec); su cambio se valida por estado vigente en `ActualizarEstadoUseCase`.
- **Unicidad de nombres.** El servicio recorta espacios y compara sin distinguir mayúsculas y minúsculas; la base de datos lo garantiza con un índice único sobre `lower(nombre)`.
- **Lote activo.** Es el lote que referencia al galpón con la fecha de ingreso más reciente y que no ha sido desvinculado (`desvinculado_en IS NULL`). Hay un índice único parcial que permite un solo lote activo por galpón.
- **Fechas.** Un bean `Clock` con zona `America/Bogota` se usa para "hoy", fechas futuras y la edad del lote en días. En las pruebas se reemplaza por un reloj fijo.
- **Proceso automático del vaciado sanitario.** `VaciadoSanitarioJob` (`@Scheduled`) busca los galpones en Vaciado sanitario cuyo periodo configurado (`avicontrol.vaciado-sanitario.dias`) ya se cumplió y solicita `Disponible` con origen `PROCESO_AUTOMATICO`. Si la propiedad no está definida, no hace la transición y lo registra en el log (FR-015 de recibir vaciado sanitario).
- **Manejo de errores.** `GlobalExceptionHandler` responde con `ProblemDetail` (RFC 9457) y agrega la lista de errores por campo cuando la validación falla, para que la interfaz muestre el mensaje junto a cada campo.
- **Jackson estricto.** Se desactiva la conversión de decimales a enteros (`ACCEPT_FLOAT_AS_INT`) para que un aforo de `10.5` se rechace en vez de guardarse como `10`.
- **Tiempo de retiro y costos** pertenecen a los módulos 2 y 3: este módulo solo expone lo que el Módulo 3 necesita consultar (población y costo del lote).

## Data Model

Esquema inicial en PostgreSQL, creado por las migraciones de Flyway (`V1__esquema_inicial.sql`).

| Tabla | Columnas | Restricciones e índices |
|---|---|---|
| `galpon` | `id uuid PK`, `nombre varchar(100) NOT NULL`, `aforo_maximo bigint NOT NULL`, `estado varchar(30) NOT NULL`, `creado_en timestamptz`, `actualizado_en timestamptz` | `CHECK (aforo_maximo > 0)`; `CHECK (estado IN (...))`; índice único `lower(nombre)` |
| `lote` | `id uuid PK`, `galpon_id uuid FK → galpon NOT NULL`, `nombre varchar(100) NOT NULL`, `fecha_ingreso date NOT NULL`, `poblacion_inicial bigint NOT NULL`, `poblacion_actual bigint NOT NULL`, `costo_total bigint NOT NULL`, `desvinculado_en timestamptz NULL` | `CHECK (poblacion_inicial > 0)`; `CHECK (poblacion_actual >= 0)`; `CHECK (costo_total > 0)`; índice único `lower(nombre)`; índice único parcial `(galpon_id) WHERE desvinculado_en IS NULL` |
| `historial_estado` | `id uuid PK`, `galpon_id uuid FK`, `estado_anterior`, `estado_nuevo`, `origen varchar(40)`, `ocurrido_en timestamptz` | índice `(galpon_id, ocurrido_en DESC)` |
| `historial_cambio_galpon` | `id uuid PK`, `galpon_id uuid FK`, `campo varchar(20)`, `valor_anterior`, `valor_nuevo`, `ocurrido_en timestamptz` | escrito por Editar galpón |
| `alerta_mantenimiento` | `id uuid PK`, `galpon_id uuid FK`, `descripcion varchar(500) NOT NULL`, `severidad NULL`, `tipo NULL`, `tecnico varchar(100)`, `estado varchar(20)`, `creada_en timestamptz` | índice único parcial `(galpon_id) WHERE estado = 'PENDIENTE'` |
| `alerta_vaciado_sanitario` | `id uuid PK` (UUID de la alerta del Módulo 2), `galpon_id uuid`, `lote_id uuid`, `evento_en timestamptz`, `recibida_en timestamptz`, `huella varchar(64)`, `resultado varchar(20)` | Conserva los UUID como referencia histórica aunque el lote se desvincule; su clave primaria da la idempotencia |
| `registro_procesamiento` | `alerta_id uuid PK`, `tipo varchar(30)`, `huella varchar(64)`, `resultado varchar(20)`, `procesado_en timestamptz` | Registro técnico de la mortalidad; no se expone por la API |

**Modelos de dominio**: `Galpon`, `Lote`, `HistorialEstado`, `HistorialCambioGalpon`, `AlertaMantenimiento`, `AlertaVaciadoSanitario`, `RegistroProcesamiento`.
**Entidades JPA**: modelos separados, ubicados en `infrastructure/adapter/out/persistence/entity/` y convertidos mediante mappers de persistencia.
**Enums**: `EstadoGalpon` (DISPONIBLE, PRODUCTIVO, EN_COSECHA, VACIADO_SANITARIO, MANTENIMIENTO, AISLAMIENTO), `OrigenCambioEstado` (ADMINISTRADOR, REGISTRO_LOTE, ALERTA_SANITARIA_MODULO_2, VACIADO_SANITARIO, PROCESO_AUTOMATICO), `EstadoAlertaMantenimiento` (PENDIENTE, ATENDIDA, ELIMINADA), `AccionSanitaria` (AISLAMIENTO, REANUDACION).

La **edad del lote** no se guarda: se calcula al consultar a partir de la fecha de ingreso y el `Clock`.

## API Contracts

Base: `/api/v1`. Todo el cuerpo es JSON. Los identificadores son UUID. Los errores siguen `ProblemDetail`.

**Códigos de estado comunes**

| Código | Cuándo |
|---|---|
| `200 OK` | Consulta o cambio aplicado. También una alerta repetida con los mismos datos (devuelve el resultado original). |
| `201 Created` | Recurso creado, con cabecera `Location`. |
| `400 Bad Request` | Formato o validación de campos: vacío, no entero, decimal, fecha futura, texto demasiado largo. |
| `404 Not Found` | El galpón, el lote o la alerta no existen. |
| `409 Conflict` | Nombre duplicado, estado incompatible, transición no permitida, el estado cambió mientras tanto, alerta pendiente repetida o UUID reutilizado con datos distintos. |

**Formato de error**

```json
{
  "type": "https://avicontrol/errores/validacion",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Revisa los campos marcados.",
  "errores": [
    { "campo": "aforoMaximo", "mensaje": "El aforo máximo debe ser un número entero positivo." }
  ]
}
```

### Administrador

| Método y ruta | Caso de uso | Request | Respuesta correcta | Errores |
|---|---|---|---|---|
| `POST /galpones` | Registrar galpón | `{ "nombre": "Galpón Norte", "aforoMaximo": 1500 }` | `201` `GalponResponse` con estado `DISPONIBLE` | `400` nombre vacío, largo o con caracteres no permitidos, aforo no entero positivo; `409` nombre en uso |
| `PATCH /galpones/{id}` | Editar galpón | `{ "nombre": "Galpón Central" }` o `{ "aforoMaximo": 1500 }` | `200` `GalponResponse` | `400`; `404`; `409` nombre en uso o aforo fuera de Mantenimiento |
| `GET /galpones/{id}/transiciones` | Actualizar estado | | `200` `[ "EN_COSECHA" ]`: estados que el administrador puede pedir desde el estado actual | `404` |
| `POST /galpones/{id}/estado` | Actualizar estado | `{ "estadoDestino": "EN_COSECHA" }` | `200` `GalponResponse` | `404`; `409` transición no permitida para el administrador o estado cambiado |
| `GET /galpones/{id}/historial-estados` | Actualizar estado | | `200` lista de `{ estadoAnterior, estadoNuevo, origen, ocurridoEn }` | `404` |
| `POST /lotes` | Registrar lote | `{ "galponId": "…", "nombre": "Lote C1", "fechaIngreso": "2026-09-18", "poblacionInicial": 1000, "costoTotal": 5000000 }` | `201` `LoteResponse` con `poblacionActual` igual a la inicial; el galpón pasa a `PRODUCTIVO` | `400` fecha futura, población o costo no enteros positivos; `404` galpón; `409` nombre en uso o galpón no disponible |
| `POST /lotes/{id}/actualizar-poblacion` | Actualizar población actual | `{ "alertaId": "…", "galponId": "…", "cantidadMuertos": 25 }` | `200` `{ loteId, poblacionAnterior, poblacionActual }`; una solicitud repetida con los mismos datos devuelve el resultado original | `400` cantidad no entera positiva; `404` lote; `409` lote no activo, cantidad mayor que la población o UUID reutilizado con datos distintos |
| `GET /alertas-mantenimiento?estado=PENDIENTE` | Generar alerta de mantenimiento | | `200` bandeja de alertas | |
| `POST /alertas-mantenimiento/{id}/atender` | Generar alerta y Actualizar estado | | `200`; la alerta queda `ATENDIDA` y el galpón pasa a `MANTENIMIENTO` | `404`; `409` alerta no pendiente o galpón no disponible |

La confirmación ("¿Estás seguro de…?") de Editar galpón y Actualizar estado es un paso de la interfaz: la API recibe la solicitud ya confirmada.

### Técnico de infraestructura

| Método y ruta | Caso de uso | Request | Respuesta correcta | Errores |
|---|---|---|---|---|
| `GET /galpones?estado=DISPONIBLE` | Generar alerta de mantenimiento | | `200` galpones que se pueden reportar | |
| `POST /alertas-mantenimiento` | Generar alerta de mantenimiento | `{ "galponId": "…", "descripcion": "Reparar sistema de ventilación", "severidad": "ALTA", "tipo": "VENTILACION", "tecnico": "…" }` | `201` alerta `PENDIENTE`; el estado del galpón no cambia | `400` descripción vacía o de más de 500 caracteres; `404`; `409` galpón no disponible o ya tiene una alerta pendiente |

### Consulta (Administrador, Módulo 2 y Módulo 3)

| Método y ruta | Caso de uso | Respuesta correcta | Errores |
|---|---|---|---|
| `GET /galpones?nombre=&estado=&sort=nombre,asc&page=0` | Consultar galpón y/o lote | `200` página de 10 `{ id, nombre, aforoMaximo, estado }`. Si la búsqueda no encuentra nada, devuelve todos los galpones y `"sinCoincidencias": true`. Orden por `nombre`, `aforoMaximo` o `estado`. | `400` criterio de orden no válido |
| `GET /galpones/{id}` | Consultar galpón y/o lote | `200` `{ galpon, loteActivo }`. `loteActivo` trae `id, nombre, poblacionInicial, poblacionActual, fechaIngreso, edadDias, costoTotal`, o `null` si no hay lotes; los campos inválidos llegan como `null` y la interfaz muestra "No disponible". | `404` |
| `GET /galpones/{id}/lotes` | Consultar galpón y/o lote | `200` lote activo y lotes anteriores | `404` |
| `GET /lotes?activos=true` | Registrar lote (listado) | `200` lotes activos, del ingreso más reciente al más antiguo | |

### Integración con el Módulo 2

Prefijo `/api/v1/integracion/modulo-2`. El Módulo 2 envía y el Módulo 1 responde con el resultado o el motivo del rechazo.

| Método y ruta | Caso de uso | Request | Respuesta correcta | Errores |
|---|---|---|---|---|
| `POST /alertas-sanitarias` | Recibir alerta sanitaria | `{ "accion": "AISLAMIENTO", "galponId": "…", "loteId": "…", "fechaHora": "2026-09-18T09:41:00-05:00", "tipoEnfermedad": "Respiratoria (sospecha)", "descripcion": "…", "gravedad": "MEDIA" }`. Debe traer `galponId` o `loteId`; para `REANUDACION`, tipo y descripción no son obligatorios. | `200` `{ galponId, estadoAnterior, estadoNuevo }` | `400` datos faltantes o fecha distinta de hoy; `404` galpón o lote; `409` lote no activo o acción incompatible con el estado |
| `POST /mortalidad` | Recibir mortalidad y remitir solicitud validada | `{ "alertaId": "…", "galponId": "…", "loteId": "…", "fechaHoraEvento": "…", "cantidadMuertos": 25 }` | `200` `{ alertaId, resultado: "RECIBIDA" }`; una alerta repetida con los mismos datos devuelve el resultado original sin remitirla otra vez. La población no cambia en este endpoint. | `400` datos faltantes, cantidad no entera positiva o fecha futura/de otro día; `404`; `409` galpón no productivo, lote no activo o UUID reutilizado con datos distintos |
| `POST /vaciado-sanitario` | Recibir vaciado sanitario | `{ "alertaId": "…", "galponId": "…", "loteId": "…", "fechaHoraEvento": "…" }` | `200` `{ galponId, estadoNuevo: "VACIADO_SANITARIO" }`; alerta repetida: el mismo resultado | `400`; `404`; `409` galpón no está en cosecha, lote no relacionado o UUID con datos distintos |

## Tareas

Rutas relativas a `avicontrol/`. El código va en `src/main/java/com/unimag/avicontrol/` (abreviado `main/`) y las pruebas en `src/test/java/com/unimag/avicontrol/` (abreviado `test/`). `[P]` indica que la tarea se puede hacer en paralelo con las demás `[P]` de su bloque porque toca archivos distintos.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dejar lista la configuración necesaria para desarrollar la aplicación modular.

- [ ] T001 Crear los paquetes del árbol de `Project Structure` en `main/` y los paquetes de pruebas de dominio, aplicación, adaptadores de entrada/salida y arquitectura en `test/`
- [ ] T002 Actualizar `pom.xml`: agregar `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, Flyway para PostgreSQL, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-postgresql` y ArchUnit; quitar `spring-boot-starter-restclient` y su dependencia de pruebas
- [ ] T003 [P] Crear `docker-compose.yml` con PostgreSQL para desarrollo y documentar cómo levantarlo en el `README.md`
- [ ] T004 [P] Completar `src/main/resources/application.properties` con la configuración base y crear `application-test.properties`
- [ ] T005 Verificar la resolución de los artefactos con `mvnw dependency:tree` y confirmar los nombres de Spring Boot 4.1.1 y Testcontainers

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura común que todas las historias necesitan.

**⚠️ CRITICAL**: Ninguna historia de usuario empieza hasta terminar esta fase.

- [ ] T006 Escribir `src/main/resources/db/migration/V1__esquema_inicial.sql` con las siete tablas, sus `CHECK`, índices únicos por `lower(nombre)` e índices parciales del Data Model
- [ ] T007 [P] Crear los enums de dominio `EstadoGalpon`, `OrigenCambioEstado`, `EstadoAlertaMantenimiento` y `AccionSanitaria` en `main/domain/model/`
- [ ] T008 [P] Crear los modelos de dominio `Galpon` y `Lote` en `main/domain/model/`, sin bloqueo optimista no definido en los specs
- [ ] T009 [P] Crear los modelos de dominio de historiales, alertas y procesamiento en `main/domain/model/`
- [ ] T010 Crear los puertos de persistencia en `main/domain/port/out/` y sus adaptadores JPA (entidades de persistencia separadas, Spring Data repositories y mappers) en `main/infrastructure/adapter/out/persistence/`, incluido el bloqueo pesimista requerido para cambios de estado (depende de T008 y T009)
- [ ] T011 [P] Crear las excepciones de negocio en `main/domain/exception/`
- [ ] T012 [P] Crear `GlobalExceptionHandler` y DTOs de error en `main/infrastructure/adapter/in/rest/` para traducir errores a `ProblemDetail` con errores por campo
- [ ] T013 [P] Crear en `main/infrastructure/config/` el bean `Clock` con zona `America/Bogota` y la clase `@ConfigurationProperties` de `avicontrol.*`
- [ ] T014 Crear `TransicionesPermitidas` en `main/domain/policy/` con la matriz de transiciones y orígenes
- [ ] T015 Crear el puerto de entrada y `ActualizarEstadoUseCase` en `main/application/`: bloquea el galpón mediante el puerto de salida, valida estado vigente y transición, actualiza el modelo y registra `historial_estado` (depende de T010 y T014)
- [ ] T016 [P] Pruebas unitarias de `TransicionesPermitidas` y `ActualizarEstadoUseCase` en `test/domain/` y `test/application/`: nueve transiciones válidas, prohibidas y estado esperado que ya cambió
- [ ] T017 [P] Crear en `test/support/` el soporte de pruebas de repositorio con PostgreSQL y un `Clock` fijo
- [ ] T018 [P] Escribir en `test/architecture/` las reglas ArchUnit para la dirección `infrastructure → application → domain` y el aislamiento del dominio

**Checkpoint**: El esquema migra, las entidades validan contra él y la autoridad de estado funciona. Las historias pueden empezar.

---

## Phase 3: User Story 1 - Registrar galpón (Priority: P1)

**Goal**: El administrador registra un galpón con nombre y aforo máximo y queda Disponible.

**Independent Test**: `POST /galpones` con datos válidos devuelve `201` y estado `DISPONIBLE`; un nombre repetido con otra capitalización devuelve `409`.

### Tests for User Story 1

- [ ] T020 [P] [US1] Prueba de contrato de `GalponController` en `test/infrastructure/adapter/in/rest/`: `201`, `400` (nombre vacío, largo, caracteres no permitidos, aforo `0`, `-5`, `10.5`, `"abc"`) y `409`
- [ ] T021 [P] [US1] Prueba del adaptador JPA en `test/infrastructure/adapter/out/persistence/`: el índice único rechaza "galpón norte" frente a "Galpón Norte"

### Implementation for User Story 1

- [ ] T022 [P] [US1] Crear `RegistrarGalponRequest` y `GalponResponse` en `main/infrastructure/adapter/in/rest/dto/` con las validaciones declarativas
- [ ] T023 [P] [US1] Crear los mappers REST/dominio de galpón en `main/infrastructure/adapter/in/rest/mapper/`
- [ ] T024 [US1] Definir el puerto de entrada e implementar `RegistrarGalponUseCase`: recortar espacios, comprobar unicidad mediante el puerto de salida y crear el dominio con estado `DISPONIBLE`
- [ ] T025 [US1] Implementar `POST /galpones` en el adaptador `GalponController`, invocando el puerto de entrada y devolviendo `Location`
- [ ] T026 [US1] Pruebas unitarias de `RegistrarGalponUseCase` en `test/application/`

**Checkpoint**: Registrar galpón funciona de extremo a extremo.

---

## Phase 4: User Story 2 - Consultar galpón y/o lote (Priority: P1)

**Goal**: Administrador, Módulo 2 y Módulo 3 consultan el listado de galpones, el detalle con el lote activo y el historial de lotes.

**Independent Test**: Con 15 galpones, `GET /galpones` devuelve 10 ordenados por nombre; una búsqueda sin resultados devuelve todos con `sinCoincidencias: true`; el detalle de un galpón sin lotes trae `loteActivo: null`.

### Tests for User Story 2

- [ ] T027 [P] [US2] Prueba de contrato del adaptador REST para `GET /galpones`, `GET /galpones/{id}` y `GET /galpones/{id}/lotes`, incluido `404` y criterio de orden inválido
- [ ] T028 [P] [US2] Prueba del adaptador de persistencia: búsqueda parcial sin distinguir mayúsculas, filtro por estado, paginación y lote activo por galpón

### Implementation for User Story 2

- [ ] T029 [P] [US2] Crear `GalponResumenResponse`, `GalponDetalleResponse`, `LoteResponse` y `PaginaResponse` en `main/infrastructure/adapter/in/rest/dto/`
- [ ] T030 [P] [US2] Crear el mapper de respuesta de lote, calculando `edadDias` con el `Clock` recibido por aplicación
- [ ] T031 [US2] Implementar `ConsultarGalponLoteUseCase`: paginación de 10, búsqueda parcial, filtro por estado y orden permitido
- [ ] T032 [US2] Implementar en el caso de uso de consulta el detalle con lote activo y el historial de lotes
- [ ] T033 [US2] Implementar los tres `GET` en el adaptador REST `GalponController`

**Checkpoint**: La consulta funciona sola y sirve para verificar las historias siguientes.

---

## Phase 5: User Story 3 - Actualizar estado (Priority: P1)

**Goal**: El administrador cambia el estado de un galpón solo por las transiciones que le corresponden.

**Independent Test**: Un galpón Productivo pasa a En cosecha con `POST /galpones/{id}/estado` y queda en el historial con origen `ADMINISTRADOR`; pedir Productivo desde Disponible devuelve `409`.

### Tests for User Story 3

- [ ] T034 [P] [US3] Prueba de contrato del adaptador REST para `GET /galpones/{id}/transiciones`, `POST /galpones/{id}/estado` y `GET /galpones/{id}/historial-estados`

### Implementation for User Story 3

- [ ] T035 [P] [US3] Crear `ActualizarEstadoRequest` y `HistorialEstadoResponse` en `main/infrastructure/adapter/in/rest/dto/`
- [ ] T036 [US3] Implementar las transiciones manuales en `ActualizarEstadoUseCase`, validando el origen `ADMINISTRADOR` con `TransicionesPermitidas`
- [ ] T037 [US3] Implementar los tres endpoints en el adaptador REST `GalponController`

**Checkpoint**: El ciclo de vida manual funciona; las demás transiciones llegan con sus historias.

---

## Phase 6: User Story 4 - Registrar lote (Priority: P1)

**Goal**: El administrador registra un lote en un galpón Disponible y el galpón pasa a Productivo en la misma transacción.

**Independent Test**: `POST /lotes` en un galpón Disponible devuelve `201`, `poblacionActual` igual a la inicial y el galpón queda Productivo; si falla el cambio de estado no queda lote guardado.

### Tests for User Story 4

- [ ] T038 [P] [US4] Prueba de contrato del adaptador REST para `POST /lotes` y `GET /lotes`: `201`, validaciones, `404` y `409`
- [ ] T039 [P] [US4] Prueba unitaria de `RegistrarLoteUseCase`: la fecha de ingreso pasada se acepta y el cambio de estado se solicita con origen `REGISTRO_LOTE`

### Implementation for User Story 4

- [ ] T040 [P] [US4] Crear `RegistrarLoteRequest` en `main/infrastructure/adapter/in/rest/dto/` con `@PastOrPresent` y `@Positive`
- [ ] T041 [US4] Implementar `RegistrarLoteUseCase`: galpón Disponible, nombre único, población actual igual a la inicial y solicitud de transición autorizada a `ActualizarEstadoUseCase`
- [ ] T042 [US4] Implementar `POST /lotes` y `GET /lotes?activos=true` en el adaptador REST `LoteController`
- [ ] T043 [US4] Pruebas unitarias de `RegistrarLoteUseCase` en `test/application/`

**Checkpoint**: Un galpón puede tener producción.

---

## Phase 7: User Story 5 - Recibir mortalidad y remitir actualización de población (Priority: P1)

**Goal**: El Módulo 1 valida la alerta del Módulo 2 y la remite una sola vez a `Actualizar población actual`; ese caso de uso descuenta la cantidad del lote activo sin modificar la población inicial ni el estado del galpón.

**Independent Test**: Una alerta válida de 25 pollos sobre una población de 1.000 se remite y deja la población actual en 975 mediante `Actualizar población actual`; reenviar la alerta no produce otro descuento; 101 muertos sobre 100 se rechaza.

### Tests for User Story 5

- [ ] T044 [P] [US5] Prueba de contrato de los endpoints REST de recepción y actualización de población con los rechazos de ambos specs
- [ ] T045 [P] [US5] Pruebas unitarias: descuentos sucesivos (25 y 10 sobre 1.000 dan 965), población en cero sin cambio de estado, remisión única de alertas repetidas y rechazo de UUID con datos distintos

### Implementation for User Story 5

- [ ] T046 [P] [US5] Crear DTOs REST `MortalidadRequest`, `ActualizarPoblacionRequest` y `PoblacionResponse`
- [ ] T047 [US5] Implementar `ActualizarPoblacionActualUseCase`: validar lote activo y cantidad no mayor que la población actual, descontar solo la población actual y conservar intacta la inicial
- [ ] T048 [US5] Implementar `RecibirMortalidadUseCase`: validar galpón Productivo, lote activo y fecha del día; registrar idempotencia y devolver al flujo administrativo los datos validados para que, tras la confirmación definida en el spec, `ActualizarPoblacionActualUseCase` aplique el descuento
- [ ] T049 [US5] Implementar los adaptadores REST para recibir la alerta del Módulo 2 y para que el administrador solicite actualizar la población
- [ ] T050 [US5] Pruebas unitarias de `ActualizarPoblacionActualUseCase` y `RecibirMortalidadUseCase`

**Checkpoint**: La alerta se valida y remite una sola vez; la población cambia exclusivamente mediante `Actualizar población actual`.

---

## Phase 8: User Story 6 - Recibir alerta sanitaria (Priority: P1)

**Goal**: El Módulo 2 aísla un galpón Productivo o lo reanuda desde Aislamiento, sin que el Módulo 1 guarde la alerta.

**Independent Test**: Una alerta de aislamiento para un galpón Productivo lo deja en Aislamiento con origen `ALERTA_SANITARIA_MODULO_2`; una reanudación lo devuelve a Productivo; un lote histórico devuelve `409`.

### Tests for User Story 6

- [ ] T051 [P] [US6] Prueba de contrato del adaptador REST de alertas sanitarias: aislamiento sin tipo o descripción, fecha inválida y acción incompatible con el estado
- [ ] T052 [P] [US6] Prueba unitaria de `RecibirAlertaSanitariaUseCase`: resolver galpón por lote activo sin modificar el lote

### Implementation for User Story 6

- [ ] T053 [P] [US6] Crear `AlertaSanitariaRequest` en el adaptador REST con validación condicional según la acción
- [ ] T054 [US6] Implementar `RecibirAlertaSanitariaUseCase`: resolver el galpón directamente o por el lote activo y solicitar el cambio a `ActualizarEstadoUseCase`
- [ ] T055 [US6] Implementar el endpoint de alerta sanitaria en el adaptador REST de integración

**Checkpoint**: El galpón refleja la situación sanitaria.

---

## Phase 9: User Story 7 - Recibir vaciado sanitario (Priority: P1)

**Goal**: Al terminar la cosecha el galpón pasa a Vaciado sanitario, el lote se desvincula y, al cumplirse el periodo, vuelve solo a Disponible.

**Independent Test**: Una alerta para un galpón En cosecha lo deja en Vaciado sanitario, con el lote desvinculado y la alerta guardada; con el periodo cumplido, el proceso automático lo deja Disponible.

### Tests for User Story 7

- [ ] T056 [P] [US7] Prueba de contrato del adaptador REST de vaciado sanitario con los rechazos del spec
- [ ] T057 [P] [US7] Pruebas unitarias de `RecibirVaciadoSanitarioUseCase` y del caso de uso de finalización: idempotencia, desvinculación y periodo sin configurar

### Implementation for User Story 7

- [ ] T058 [P] [US7] Crear `VaciadoSanitarioRequest` en el adaptador REST de integración
- [ ] T059 [US7] Implementar `RecibirVaciadoSanitarioUseCase`: guardar la alerta con sus UUID, desvincular el lote y solicitar a `ActualizarEstadoUseCase` la transición autorizada a Vaciado sanitario
- [ ] T060 [US7] Implementar el caso de uso `FinalizarPeriodosVaciadoUseCase` y su adaptador de entrada `VaciadoSanitarioJob` en `infrastructure/adapter/in/scheduling/`; activar planificación en configuración
- [ ] T061 [US7] Implementar el endpoint de vaciado sanitario en el adaptador REST de integración

**Checkpoint**: El ciclo completo del galpón se cierra y vuelve a Disponible.

---

## Phase 10: User Story 8 - Generar alerta de mantenimiento (Priority: P1)

**Goal**: El técnico reporta una falla en un galpón Disponible y el administrador la atiende, lo que pasa el galpón a Mantenimiento.

**Independent Test**: El técnico crea una alerta Pendiente sin cambiar el estado; una segunda alerta para el mismo galpón devuelve `409`; al atenderla el galpón queda en Mantenimiento.

### Tests for User Story 8

- [ ] T062 [P] [US8] Prueba de contrato del adaptador REST para generar, listar y atender alertas de mantenimiento
- [ ] T063 [P] [US8] Prueba del adaptador de persistencia: el índice parcial impide dos alertas pendientes para el mismo galpón

### Implementation for User Story 8

- [ ] T064 [P] [US8] Crear DTOs REST de alerta de mantenimiento y sus mappers entre DTO y aplicación/dominio
- [ ] T065 [US8] Implementar `GenerarAlertaMantenimientoUseCase` y `AtenderAlertaMantenimientoUseCase`, delegando el cambio de estado a `ActualizarEstadoUseCase`
- [ ] T066 [US8] Implementar el adaptador REST de alertas de mantenimiento

**Checkpoint**: Todas las transiciones de la matriz tienen un camino.

---

## Phase 11: User Story 9 - Editar galpón (Priority: P3)

**Goal**: El administrador cambia el nombre de un galpón en cualquier estado y el aforo solo en Mantenimiento, con historial.

**Independent Test**: Cambiar el nombre de "Galpón C" a "Galpón Central" devuelve `200` y deja un registro en el historial; cambiar el aforo de un galpón Disponible devuelve `409`.

### Tests for User Story 9

- [ ] T067 [P] [US9] Prueba de contrato del adaptador REST para `PATCH /galpones/{id}`: el mismo nombre no es duplicado; aforo fuera de Mantenimiento devuelve `409`

### Implementation for User Story 9

- [ ] T068 [P] [US9] Crear `EditarGalponRequest` en el adaptador REST con ambos campos opcionales
- [ ] T069 [US9] Implementar `EditarGalponUseCase`: unicidad que excluye al propio galpón, aforo solo en Mantenimiento y un registro de historial por campo cambiado
- [ ] T070 [US9] Implementar `PATCH /galpones/{id}` en el adaptador REST `GalponController`

**Checkpoint**: Los 10 casos de uso del Módulo 1 funcionan de forma independiente.

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a varias historias.

- [ ] T071 [P] Revisar que cada escenario de aceptación de los 10 specs tenga su prueba y completar las que falten
- [ ] T073 [P] Documentar en `README.md` cómo levantar el proyecto, ejecutar las pruebas y los endpoints para los módulos 2 y 3
- [ ] T074 [P] Agregar registros (log) de los cambios de estado y de las alertas rechazadas
- [ ] T075 Revisar el manejo de errores de base de datos no disponible: mensaje general y ningún cambio a medias

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **User Stories (Phase 3 a 11)**: dependen de Foundational.
- **Polish (Phase 12)**: depende de las historias que se vayan a entregar.

### User Story Dependencies

- **US1 Registrar galpón**: sin dependencias entre historias.
- **US2 Consultar**: usa galpones; en las pruebas se crean con datos de prueba, sin depender de US1.
- **US3 Actualizar estado**: solo de Foundational (`ActualizarEstadoUseCase`).
- **US4 Registrar lote**: de Foundational; se prueba con galpones creados en la prueba.
- **US5 Mortalidad y población, US6 Alerta sanitaria, US7 Vaciado sanitario**: necesitan un lote activo; se prueban con datos de prueba, sin depender de US4.
- **US8 Alerta de mantenimiento**: solo de Foundational.
- **US9 Editar galpón**: sin dependencias entre historias.

Con varios integrantes, después de la Fase 2 las historias pueden repartirse en paralelo. Con una sola persona, el orden sugerido es el de las fases.

### Within Each User Story

- Modelos y puertos del dominio/aplicación antes que adaptadores.
- Caso de uso antes que sus adaptadores de entrada y salida.
- La historia completa y probada antes de pasar a la siguiente.
- Las pruebas de contrato se pueden escribir primero y ejecutar al terminar la implementación.

## Open Questions

Decisiones que los specs no cierran. El plan propone una opción; hay que confirmarlas con el equipo antes de implementar la historia afectada.

1. **Autenticación y roles.** Ningún spec la define (actualizar estado FR-008, consultar FR-022). Propuesta: fuera de alcance por ahora; con Spring Security se agregaría como una fase aparte.
2. **Confirmación de Actualizar población actual.** El spec separa la recepción de mortalidad de la actualización, que procesa el administrador. Confirmar si el flujo de interfaz requiere una confirmación explícita; en cualquier caso, la recepción no descuenta directamente ni crea una solicitud pendiente fuera de lo definido por el spec.
3. **Periodo del vaciado sanitario.** Cuántos días dura; mientras no se defina, el proceso automático no cambia el estado.
4. **Historial de lotes después del vaciado.** Al desvincular el lote se pierde la relación activa. El plan conserva `galpon_id` en el lote y marca `desvinculado_en`, de modo que el historial de Consultar sigue funcionando. Confirmar que esa es la interpretación correcta de "eliminar la relación activa".
5. **Lote activo.** Consultar lo define como "el de ingreso más reciente"; población y mortalidad como "el que referencia actualmente al galpón". El plan los une: el de ingreso más reciente que no está desvinculado.
6. **Disponible → Mantenimiento.** El plan solo lo permite al atender una alerta pendiente (`/alertas-mantenimiento/{id}/atender`). Confirmar si el administrador puede pedirlo también sin alerta.
7. **Población inicial frente al aforo máximo.** Ningún spec impide registrar más pollos que el aforo del galpón. Confirmar si debe validarse.
8. **Operario de granja.** Consultar menciona operarios, pero no aparecen en los casos de uso.
9. **Orden por estado.** Confirmar si el orden de los estados es alfabético o el del ciclo de vida.
10. **Meta de rendimiento.** La cifra de p95 de este plan es una propuesta por validar.

## Notes

- `[P]` = puede hacerse en paralelo con otras tareas `[P]` del mismo bloque.
- `[USn]` enlaza la tarea con su historia para la trazabilidad.
- Cada historia debe poder completarse y probarse por separado.
- Hacer commit después de cada tarea o grupo lógico de tareas.
- Detenerse en cada checkpoint para validar la historia.
- Evitar tareas vagas, conflictos en el mismo archivo y dependencias entre historias que rompan su independencia.
