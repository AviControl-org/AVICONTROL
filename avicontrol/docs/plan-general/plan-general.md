# Implementation Plan: AVICONTROL Módulo 1 - Definición técnica y arquitectura general

**Date**: 2026-09-19
**Spec**: [Casos de uso](../Casos%20de%20uso.md) y los 10 specs del Módulo 1: [registrar galpón](../spec-registrarGalpon/registrar-galpon-spec.md), [editar galpón](../spec-editarGalpon/editar-galpon-spec.md), [registrar lote](../spec-registrarLote/registrar-lote-spec.md), [consultar galpón-lote](../spec-consultar-galpón-lote/consultar-galpón-lote.md), [actualizar estado](../spec-actualizarEstado/actualizar-estados-spec.md), [actualizar población actual](../spec-actualizarPoblaciónActual/actualizarPoblaciónActual.md), [recibir mortalidad del galpón](../spec-recibirMortalidadDelGalpón/recibirMortalidadDelGalpón.md), [recibir alerta sanitaria](../spec-recibirAlertaSanitaria/recibirAlertaSanitaria.md), [recibir vaciado sanitario](../spec-recibirVaciadoSanitario/recibirVaciadoSanitario.md), [generar alerta de mantenimiento](../spec-generarAlertaMantenimiento/generar-alerta-mantenimiento-spec.md)

## Summary

El Módulo 1 de AVICONTROL gestiona los galpones de una granja avícola y los lotes de pollos que alojan: registro y edición de galpones, registro de lotes, consulta, ciclo de vida por estados, descuento de mortalidad, alertas sanitarias, vaciado sanitario y alertas de mantenimiento.

Este plan define **cómo** se construye: un **monolito** en **Java 21 y Spring Boot** con arquitectura **n-layers** (`api`, `service`, `repository`, `entity`, con `mapper` y DTOs como piezas de apoyo), **PostgreSQL** como base de datos, **sin multi-tenencia y enfocado en una sola granja**. Las decisiones que lo sostienen:

- **Java con Spring**, porque es el lenguaje y framework más sólido del equipo entre todos los módulos.
- **n-layers**, porque las dependencias entre capas son unidireccionales y las responsabilidades no se solapan.
- **Monolito de una sola granja**, porque los specs exigen operaciones atómicas entre varios casos de uso y una sola base de datos las resuelve con una transacción.
- **`Actualizar estado` como única autoridad** para persistir un cambio de estado del galpón; los demás casos de uso le remiten la solicitud.

Las justificaciones completas, el modelo de datos, los contratos de API y la estrategia de pruebas están más abajo, en `Architecture & Design Decisions`, `Data Model`, `API Contracts` y `Testing Strategy`.

## Technical Context

**Language/Version**: Java 21 (LTS) con Spring Boot 4.1.1, tal como está en el `pom.xml` actual.
**Primary Dependencies**: `spring-boot-starter-webmvc` (API REST), `spring-boot-starter-data-jpa` (persistencia), `spring-boot-starter-validation` (validación de DTOs), controlador JDBC de PostgreSQL, Flyway (migraciones), Lombok. Los nombres exactos de los starters de Boot 4 se confirman en la tarea T002.
**Storage**: PostgreSQL. Identificadores UUID, valores monetarios en pesos colombianos como `BIGINT` sin decimales, fechas y horas como `timestamptz`.
**Testing**: JUnit 5 y Mockito (unitarias), `@DataJpaTest` con Testcontainers para PostgreSQL (repositorios), MockMvc (contratos de API), `@SpringBootTest` (flujos atómicos y concurrencia) y ArchUnit (reglas de arquitectura).
**Target Platform**: JVM 21 en servidor Linux o contenedor Docker, una sola instancia. Desarrollo en Windows con PostgreSQL en Docker Compose.
**Project Type**: single. Un único módulo Maven que expone una API REST (proyecto web sin frontend en este plan). La interfaz queda fuera de alcance; los mockups están en `docs/mockups`.
**Performance Goals**: *Propuesto, a validar por el equipo.* Consultas y operaciones de escritura con p95 por debajo de 300 ms; el listado devuelve 10 galpones por página, como pide el spec.
**Constraints**:
- Toda operación que combine varios pasos debe ser atómica (registrar lote más cambio de estado, recepción de vaciado sanitario, descuento de mortalidad).
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
│   │   │   ├── api/                                # Capa de presentación
│   │   │   │   ├── GalponController.java
│   │   │   │   ├── LoteController.java
│   │   │   │   ├── AlertaMantenimientoController.java
│   │   │   │   ├── IntegracionModulo2Controller.java   # sanitaria, mortalidad, vaciado
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   └── job/
│   │   │   │       └── VaciadoSanitarioJob.java        # entrada no HTTP (proceso automático)
│   │   │   ├── dto/                                # requests y responses (records), usados por api y service
│   │   │   ├── service/                            # Capa de negocio
│   │   │   │   ├── GalponService.java
│   │   │   │   ├── GalponEstadoService.java        # Actualizar estado (única autoridad)
│   │   │   │   ├── TransicionesPermitidas.java     # matriz de transiciones y orígenes
│   │   │   │   ├── LoteService.java
│   │   │   │   ├── PoblacionService.java           # Actualizar población actual
│   │   │   │   ├── MortalidadService.java          # Recibir mortalidad del galpón
│   │   │   │   ├── AlertaSanitariaService.java
│   │   │   │   ├── VaciadoSanitarioService.java
│   │   │   │   └── AlertaMantenimientoService.java
│   │   │   ├── repository/                         # Capa de acceso a datos
│   │   │   ├── entity/                             # Modelo de dominio y enums
│   │   │   ├── mapper/                             # entity <-> dto
│   │   │   ├── exception/                          # errores de negocio
│   │   │   └── config/                             # Clock, planificador, Jackson
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/                       # V1__..., V2__... (Flyway)
│   └── test/java/com/unimag/avicontrol/
│       ├── api/                                    # contratos (MockMvc)
│       ├── service/                                # unitarias (Mockito)
│       ├── repository/                             # Testcontainers
│       ├── integracion/                            # flujos atómicos y concurrencia
│       ├── arquitectura/                           # ArchUnit
│       └── soporte/                                # base de Testcontainers, datos de prueba
```

**Structure Decision**: un único proyecto Maven (`avicontrol/`) organizado **por capa**, que es la organización que describe una arquitectura n-layers. Los nombres de las clases de dominio siguen a los specs (`Galpon`, `Lote`) sin tildes; los paquetes de las capas van en inglés (`api`, `service`, `repository`, `entity`, `mapper`, `dto`). Los procesos automáticos (`api/job`) se tratan como otro punto de entrada de la capa `api`: llaman al servicio y no contienen reglas de negocio.

## Architecture & Design Decisions

### Justificación de las decisiones

| Decisión | Justificación | Alternativa descartada y por qué |
|---|---|---|
| **Java 21 con Spring Boot** | Es el lenguaje y framework más fuerte del equipo entre todos los módulos, de modo que cualquier integrante puede leer, revisar y mantener el Módulo 1. Java 21 es LTS. Spring aporta de serie lo que piden los specs: transacciones declarativas (`@Transactional`), validación, JPA y planificación de tareas. | Otros stacks: habría que aprender un framework nuevo y el equipo quedaría repartido entre lenguajes. |
| **Arquitectura n-layers** | Las dependencias entre capas van en una sola dirección (`api → service → repository → entity`) y cada capa tiene una responsabilidad que no se solapa con las demás. Las reglas de negocio de los specs quedan concentradas en `service` y se pueden probar sin HTTP ni base de datos. Es la arquitectura que el equipo ya conoce. | Hexagonal o clean architecture: aíslan mejor el dominio, pero agregan puertos, adaptadores e interfaces que no se justifican para un solo módulo con una sola base de datos. |
| **Monolito** | Los specs exigen operaciones atómicas entre varios casos de uso: registrar un lote y cambiar el estado del galpón; recibir el vaciado sanitario, desvincular el lote y cambiar el estado. En un monolito con una sola base de datos se resuelven con una transacción local, sin coordinación distribuida. Un solo artefacto que desplegar y operar. | Microservicios: cada operación atómica se volvería una transacción distribuida (sagas o compensaciones), con más infraestructura y más puntos de falla para un equipo pequeño. |
| **Sin multi-tenencia, una sola granja** | El alcance es una granja. Sin `tenant_id` las consultas, índices y restricciones de unicidad son más simples: el nombre de galpón es único en todo el sistema, como dicen los specs. | Multi-tenencia: habría que filtrar todo por granja y aislar los datos, sin que ningún spec lo pida. Si más adelante hiciera falta, se agrega una columna de granja y se amplían los índices únicos. |
| **PostgreSQL** | Ya está en el `pom.xml`. Tiene tipo UUID nativo, índices únicos por expresión (`lower(nombre)`) e índices parciales (una alerta pendiente por galpón). | H2 u otra base en memoria: sirve para pruebas rápidas, pero no reproduce esos índices ni la concurrencia real. |
| **Flyway para el esquema** | El esquema queda versionado en el repositorio y es igual en todos los equipos. Las restricciones que exigen los specs (unicidad, `CHECK`) quedan escritas en SQL. | `ddl-auto=update` de Hibernate: no es reproducible y no crea índices por expresión ni parciales. |
| **Mappers manuales** | Una clase por entidad con métodos `toResponse` y `toEntity`. Es fácil de leer y explicar, y no requiere configurar el orden de procesadores de anotaciones con Lombok. | MapStruct: menos código repetido, pero agrega configuración que no compensa con pocas entidades. |
| **`Actualizar estado` como única autoridad** | Lo exige el spec de actualizar estado (FR-012): un solo servicio valida la matriz de transiciones, el origen autorizado y el estado vigente, y escribe el historial. Así no hay dos lugares que cambien el estado de forma distinta. | Que cada caso de uso cambie el estado directamente: duplicaría la validación y rompería el historial. |

### Capas y responsabilidades

```text
Administrador · Técnico de infraestructura · Módulo 2 · Módulo 3 · Proceso automático
                 │ HTTP + JSON                                  │ @Scheduled
        ┌────────▼──────────────────────────────────────────────▼─────┐
        │ api          Controllers, GlobalExceptionHandler, job        │
        ├──────────────────────────────────────────────────────────────┤
        │ service      Reglas de negocio, @Transactional, mappers      │
        ├──────────────────────────────────────────────────────────────┤
        │ repository   Interfaces JpaRepository y consultas            │
        ├──────────────────────────────────────────────────────────────┤
        │ entity       @Entity y enums del dominio                     │
        └──────────────────────────────────────────────────────────────┘
        dto, mapper y exception: piezas de apoyo que cruzan las fronteras
```

| Paquete | Responsabilidad | Puede depender de | No debe |
|---|---|---|---|
| `api` | Recibir la petición, validar el formato (`@Valid`), llamar a un servicio y devolver la respuesta con su código HTTP. `GlobalExceptionHandler` traduce excepciones a respuestas de error. | `service`, `dto`, `exception` | Tener reglas de negocio ni acceder a repositorios o entidades. |
| `service` | Aplicar las reglas de cada spec, abrir y cerrar la transacción, coordinar repositorios y llamar a `GalponEstadoService` cuando haga falta cambiar un estado. | `repository`, `entity`, `mapper`, `dto`, `exception` | Conocer HTTP (`ResponseEntity`, códigos de estado). |
| `repository` | Leer y escribir en la base de datos. Consultas derivadas y `@Query` cuando haga falta. | `entity` | Tener reglas de negocio. |
| `entity` | Representar las tablas y los enums del dominio. | Nada del proyecto | Salir por la API. |
| `mapper` | Convertir entre entidad y DTO. | `entity`, `dto` | Consultar la base de datos. |
| `dto` | Records inmutables de entrada y salida con anotaciones de validación. | Nada del proyecto | Contener lógica. |
| `exception` | Excepciones de negocio (`RecursoNoEncontradoException`, `ConflictoException`, `TransicionInvalidaException`, `ValidacionNegocioException`). | Nada del proyecto | |

**Reglas de la arquitectura** (se comprueban automáticamente con ArchUnit, tarea T018):

1. Las dependencias van en una sola dirección: `api → service → repository → entity`. Ningún controlador usa un repositorio.
2. Las entidades no salen del servicio: la API solo recibe y devuelve DTOs.
3. El mapeo se hace en el servicio: el controlador entrega el request y recibe el response.
4. `@Transactional` solo se declara en la capa `service`.
5. Solo `GalponEstadoService` escribe el estado de un galpón y el historial de estados.

### Diseño transversal

- **Autoridad única de estado.** `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)` bloquea la fila del galpón (`SELECT ... FOR UPDATE`), comprueba que el estado vigente sea el esperado y que la pareja transición-origen esté en `TransicionesPermitidas`, actualiza el estado y escribe `historial_estado`. Si otro proceso cambió el estado antes, rechaza la solicitud (FR-013 de actualizar estado).
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

- **Atomicidad.** Los servicios que combinan pasos (registrar lote, recibir vaciado sanitario, actualizar población) usan una sola transacción; `GalponEstadoService` participa en ella con la propagación por defecto. Si un paso falla, se revierte todo.
- **Idempotencia de las alertas del Módulo 2.** Mortalidad y vaciado sanitario guardan el UUID de cada alerta aplicada junto con una huella (SHA-256) de sus datos: mortalidad en `registro_procesamiento` (el registro técnico mínimo de FR-019) y vaciado en `alerta_vaciado_sanitario`. Si llega el mismo UUID con los mismos datos, se devuelve el resultado original sin repetir el efecto; si llega con datos distintos, se responde `409 Conflict`. La clave primaria sobre el UUID evita duplicados cuando llegan dos copias a la vez. La alerta sanitaria no se guarda (FR-004 de su spec): su protección frente a repeticiones es la validación del estado vigente en `GalponEstadoService`.
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
| `galpon` | `id uuid PK`, `nombre varchar(100) NOT NULL`, `aforo_maximo bigint NOT NULL`, `estado varchar(30) NOT NULL`, `version bigint NOT NULL`, `creado_en timestamptz`, `actualizado_en timestamptz` | `CHECK (aforo_maximo > 0)`; `CHECK (estado IN (...))`; índice único `lower(nombre)` |
| `lote` | `id uuid PK`, `galpon_id uuid FK → galpon NOT NULL`, `nombre varchar(100) NOT NULL`, `fecha_ingreso date NOT NULL`, `poblacion_inicial bigint NOT NULL`, `poblacion_actual bigint NOT NULL`, `costo_total bigint NOT NULL`, `desvinculado_en timestamptz NULL`, `version bigint NOT NULL` | `CHECK (poblacion_inicial > 0)`; `CHECK (poblacion_actual >= 0)`; `CHECK (costo_total > 0)`; índice único `lower(nombre)`; índice único parcial `(galpon_id) WHERE desvinculado_en IS NULL` |
| `historial_estado` | `id uuid PK`, `galpon_id uuid FK`, `estado_anterior`, `estado_nuevo`, `origen varchar(40)`, `ocurrido_en timestamptz` | índice `(galpon_id, ocurrido_en DESC)` |
| `historial_cambio_galpon` | `id uuid PK`, `galpon_id uuid FK`, `campo varchar(20)`, `valor_anterior`, `valor_nuevo`, `ocurrido_en timestamptz` | escrito por Editar galpón |
| `alerta_mantenimiento` | `id uuid PK`, `galpon_id uuid FK`, `descripcion varchar(500) NOT NULL`, `severidad NULL`, `tipo NULL`, `tecnico varchar(100)`, `estado varchar(20)`, `creada_en timestamptz` | índice único parcial `(galpon_id) WHERE estado = 'PENDIENTE'` |
| `alerta_vaciado_sanitario` | `id uuid PK` (UUID de la alerta del Módulo 2), `galpon_id uuid`, `lote_id uuid`, `evento_en timestamptz`, `recibida_en timestamptz`, `huella varchar(64)`, `resultado varchar(20)` | Conserva los UUID como referencia histórica aunque el lote se desvincule; su clave primaria da la idempotencia |
| `registro_procesamiento` | `alerta_id uuid PK`, `tipo varchar(30)`, `huella varchar(64)`, `resultado varchar(20)`, `procesado_en timestamptz` | Registro técnico de la mortalidad; no se expone por la API |

**Entidades JPA**: `Galpon`, `Lote`, `HistorialEstado`, `HistorialCambioGalpon`, `AlertaMantenimiento`, `AlertaVaciadoSanitario`, `RegistroProcesamiento`.
**Enums**: `EstadoGalpon` (DISPONIBLE, PRODUCTIVO, EN_COSECHA, VACIADO_SANITARIO, MANTENIMIENTO, AISLAMIENTO), `OrigenCambioEstado` (ADMINISTRADOR, REGISTRO_LOTE, ALERTA_SANITARIA_MODULO_2, VACIADO_SANITARIO, PROCESO_AUTOMATICO), `EstadoAlertaMantenimiento` (PENDIENTE, ATENDIDA, ELIMINADA), `AccionSanitaria` (AISLAMIENTO, REANUDACION).

La **edad del lote** no se guarda: se calcula al consultar a partir de la fecha de ingreso y el `Clock`.

## Dependencies (pom.xml)

El `pom.xml` actual ya tiene Spring Boot 4.1.1, Java 21, JPA, PostgreSQL y Lombok. Para una API web hay que agregar y quitar lo siguiente:

| Cambio | Artefacto | Para qué |
|---|---|---|
| Agregar | `spring-boot-starter-webmvc` | Controladores REST y Jackson. En Boot 4 reemplaza a `spring-boot-starter-web`. |
| Agregar | `spring-boot-starter-validation` | `@Valid`, `@NotBlank`, `@Positive`, `@Size` en los DTOs. |
| Agregar | `spring-boot-starter-flyway` y `org.flywaydb:flyway-database-postgresql` | Migraciones versionadas del esquema. |
| Agregar (test) | `spring-boot-starter-webmvc-test` | MockMvc para las pruebas de contrato. |
| Agregar (test) | `spring-boot-testcontainers` y el módulo PostgreSQL de Testcontainers | Pruebas contra un PostgreSQL real. |
| Agregar (test) | `com.tngtech.archunit:archunit-junit5` | Comprobar las reglas de capas. |
| Quitar | `spring-boot-starter-restclient` y `spring-boot-starter-restclient-test` | Sirven para que este módulo **llame** a otros servicios. En este diseño son los módulos 2 y 3 quienes llaman al Módulo 1. Se vuelven a agregar si aparece una llamada saliente. |
| Mantener | `spring-boot-starter-data-jpa`, `postgresql` (runtime), `lombok`, `spring-boot-starter-data-jpa-test` | Ya presentes. |

Bloque de dependencias propuesto (las versiones las gestiona el parent de Spring Boot; ArchUnit necesita versión explícita y los nombres exactos de los artefactos de Boot 4 y Testcontainers se confirman en T002):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Pruebas -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.tngtech.archunit</groupId>
        <artifactId>archunit-junit5</artifactId>
        <version>${archunit.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Configuración base (`application.properties`):

```properties
spring.application.name=avicontrol
spring.datasource.url=jdbc:postgresql://localhost:5432/avicontrol
spring.datasource.username=${DB_USER:avicontrol}
spring.datasource.password=${DB_PASSWORD:avicontrol}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jackson.deserialization.accept-float-as-int=false
spring.jackson.deserialization.fail-on-unknown-properties=true
avicontrol.zona-horaria=America/Bogota
avicontrol.vaciado-sanitario.dias=
```

`ddl-auto=validate` hace que Hibernate compruebe que las entidades coinciden con el esquema de Flyway, sin modificarlo. `avicontrol.vaciado-sanitario.dias` queda vacío a propósito hasta que el equipo defina el periodo.

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
| `POST /mortalidad` | Recibir mortalidad del galpón y Actualizar población actual | `{ "alertaId": "…", "galponId": "…", "loteId": "…", "fechaHoraEvento": "…", "cantidadMuertos": 25 }` | `200` `{ loteId, poblacionAnterior, poblacionActual }`; si la alerta ya se procesó con los mismos datos, el mismo resultado | `400` datos faltantes, cantidad no entera positiva, fecha futura o de otro día; `404`; `409` galpón no productivo, lote no activo, cantidad mayor que la población o UUID con datos distintos |
| `POST /vaciado-sanitario` | Recibir vaciado sanitario | `{ "alertaId": "…", "galponId": "…", "loteId": "…", "fechaHoraEvento": "…" }` | `200` `{ galponId, estadoNuevo: "VACIADO_SANITARIO" }`; alerta repetida: el mismo resultado | `400`; `404`; `409` galpón no está en cosecha, lote no relacionado o UUID con datos distintos |

## Testing Strategy

| Tipo | Qué cubre | Herramientas | Dónde |
|---|---|---|---|
| Unitarias | Reglas de cada servicio (validaciones, matriz de transiciones, cálculo de población, idempotencia) con repositorios simulados y un `Clock` fijo. | JUnit 5, Mockito, AssertJ | `test/.../service` |
| Repositorio | Consultas propias, índices únicos por `lower(nombre)`, índices parciales y `CHECK` del esquema real. | `@DataJpaTest` con Testcontainers PostgreSQL | `test/.../repository` |
| Contrato | Rutas, códigos de estado, forma del JSON y del `ProblemDetail`, rechazo de decimales en campos enteros. | `@WebMvcTest`, MockMvc | `test/.../api` |
| Integración | Flujos completos y atómicos: registrar lote y cambio de estado, vaciado sanitario con desvinculación, reversión cuando falla un paso, alertas repetidas, dos solicitudes simultáneas sobre el mismo galpón. | `@SpringBootTest` con Testcontainers | `test/.../integracion` |
| Arquitectura | Las reglas de capas de este plan. | ArchUnit | `test/.../arquitectura` |

Cada escenario de aceptación de los specs debe tener al menos una prueba que lo cubra. Objetivo propuesto de cobertura: 80 % de líneas en `service`, medido con JaCoCo.

## Tareas

Rutas relativas a `avicontrol/`. El código va en `src/main/java/com/unimag/avicontrol/` (abreviado `main/`) y las pruebas en `src/test/java/com/unimag/avicontrol/` (abreviado `test/`). `[P]` indica que la tarea se puede hacer en paralelo con las demás `[P]` de su bloque porque toca archivos distintos.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dejar el proyecto listo para desarrollar la API web.

- [ ] T001 Crear los paquetes `api`, `api/job`, `dto`, `service`, `repository`, `entity`, `mapper`, `exception` y `config` en `main/`, y los de pruebas en `test/`
- [ ] T002 Actualizar `pom.xml` según la sección Dependencies: agregar webmvc, validation, Flyway, pruebas web, Testcontainers y ArchUnit; quitar restclient. Confirmar los nombres de artefacto de Boot 4.1.1 con `mvnw dependency:tree`
- [ ] T003 [P] Crear `docker-compose.yml` con PostgreSQL para desarrollo y documentar cómo levantarlo en el `README.md`
- [ ] T004 [P] Completar `src/main/resources/application.properties` con la configuración base y crear `application-test.properties`
- [ ] T005 [P] Agregar el plugin de JaCoCo al `pom.xml` para medir la cobertura

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura común que todas las historias necesitan.

**⚠️ CRITICAL**: Ninguna historia de usuario empieza hasta terminar esta fase.

- [ ] T006 Escribir `src/main/resources/db/migration/V1__esquema_inicial.sql` con las siete tablas, sus `CHECK`, índices únicos por `lower(nombre)` e índices parciales del Data Model
- [ ] T007 [P] Crear los enums `EstadoGalpon`, `OrigenCambioEstado`, `EstadoAlertaMantenimiento` y `AccionSanitaria` en `main/entity/`
- [ ] T008 [P] Crear las entidades `Galpon` y `Lote` (con `@Version`) en `main/entity/`
- [ ] T009 [P] Crear las entidades `HistorialEstado`, `HistorialCambioGalpon`, `AlertaMantenimiento`, `AlertaVaciadoSanitario` y `RegistroProcesamiento` en `main/entity/`
- [ ] T010 Crear los repositorios en `main/repository/`, incluido `GalponRepository.findByIdForUpdate` con bloqueo pesimista (depende de T008 y T009)
- [ ] T011 [P] Crear las excepciones de negocio en `main/exception/`
- [ ] T012 [P] Crear `GlobalExceptionHandler` en `main/api/` que traduzca excepciones y errores de validación a `ProblemDetail` con la lista de errores por campo
- [ ] T013 [P] Crear en `main/config/` el bean `Clock` con zona `America/Bogota` y la clase `@ConfigurationProperties` de `avicontrol.*`
- [ ] T014 Crear `TransicionesPermitidas` en `main/service/` con la matriz de transiciones y orígenes
- [ ] T015 Crear `GalponEstadoService.solicitarCambio(galponId, estadoEsperado, estadoDestino, origen)` en `main/service/`: bloquea el galpón, valida estado vigente y transición, actualiza el estado y escribe `historial_estado` (depende de T010 y T014)
- [ ] T016 [P] Pruebas unitarias de `TransicionesPermitidas` y `GalponEstadoService` en `test/service/`: las nueve transiciones válidas, las prohibidas (Disponible → Productivo manual, Aislamiento → En cosecha) y el estado esperado que ya cambió
- [ ] T017 [P] Crear en `test/soporte/` la clase base con Testcontainers PostgreSQL y un `Clock` fijo para las pruebas
- [ ] T018 [P] Escribir en `test/arquitectura/` las reglas de ArchUnit de este plan
- [ ] T019 Prueba de integración en `test/integracion/`: dos solicitudes simultáneas sobre el mismo galpón, una se aplica y la otra se rechaza (depende de T015 y T017)

**Checkpoint**: El esquema migra, las entidades validan contra él y la autoridad de estado funciona. Las historias pueden empezar.

---

## Phase 3: User Story 1 - Registrar galpón (Priority: P1)

**Goal**: El administrador registra un galpón con nombre y aforo máximo y queda Disponible.

**Independent Test**: `POST /galpones` con datos válidos devuelve `201` y estado `DISPONIBLE`; un nombre repetido con otra capitalización devuelve `409`.

### Tests for User Story 1

- [ ] T020 [P] [US1] Prueba de contrato en `test/api/GalponControllerTest.java`: `201`, `400` (nombre vacío, largo, caracteres no permitidos, aforo `0`, `-5`, `10.5`, `"abc"`) y `409`
- [ ] T021 [P] [US1] Prueba de repositorio en `test/repository/GalponRepositoryTest.java`: el índice único rechaza "galpón norte" frente a "Galpón Norte"

### Implementation for User Story 1

- [ ] T022 [P] [US1] Crear `RegistrarGalponRequest` y `GalponResponse` en `main/dto/` con `@NotBlank`, `@Size(max = 100)`, `@Pattern` y `@Positive`
- [ ] T023 [P] [US1] Crear `GalponMapper` en `main/mapper/`
- [ ] T024 [US1] Implementar `GalponService.registrar`: recortar espacios, comprobar nombre único, estado inicial `DISPONIBLE`
- [ ] T025 [US1] Implementar `POST /galpones` en `GalponController` con `Location` en la respuesta
- [ ] T026 [US1] Pruebas unitarias de `GalponService.registrar` en `test/service/`

**Checkpoint**: Registrar galpón funciona de extremo a extremo.

---

## Phase 4: User Story 2 - Consultar galpón y/o lote (Priority: P1)

**Goal**: Administrador, Módulo 2 y Módulo 3 consultan el listado de galpones, el detalle con el lote activo y el historial de lotes.

**Independent Test**: Con 15 galpones, `GET /galpones` devuelve 10 ordenados por nombre; una búsqueda sin resultados devuelve todos con `sinCoincidencias: true`; el detalle de un galpón sin lotes trae `loteActivo: null`.

### Tests for User Story 2

- [ ] T027 [P] [US2] Prueba de contrato de `GET /galpones`, `GET /galpones/{id}` y `GET /galpones/{id}/lotes`, incluido `404` y criterio de orden inválido
- [ ] T028 [P] [US2] Prueba de repositorio: búsqueda parcial sin distinguir mayúsculas, filtro por estado, paginación sin repetir ni omitir registros y lote activo por galpón

### Implementation for User Story 2

- [ ] T029 [P] [US2] Crear `GalponResumenResponse`, `GalponDetalleResponse`, `LoteResponse` y `PaginaResponse` en `main/dto/`
- [ ] T030 [P] [US2] Crear `LoteMapper` con el cálculo de `edadDias` a partir del `Clock`, y `null` en los campos inválidos
- [ ] T031 [US2] Implementar en `GalponService` la consulta paginada (10 por página), la búsqueda parcial con reintento sin filtro cuando no hay coincidencias, el filtro por estado y el orden permitido
- [ ] T032 [US2] Implementar el detalle con lote activo y el historial de lotes
- [ ] T033 [US2] Implementar los tres `GET` en `GalponController`

**Checkpoint**: La consulta funciona sola y sirve para verificar las historias siguientes.

---

## Phase 5: User Story 3 - Actualizar estado (Priority: P1)

**Goal**: El administrador cambia el estado de un galpón solo por las transiciones que le corresponden.

**Independent Test**: Un galpón Productivo pasa a En cosecha con `POST /galpones/{id}/estado` y queda en el historial con origen `ADMINISTRADOR`; pedir Productivo desde Disponible devuelve `409`.

### Tests for User Story 3

- [ ] T034 [P] [US3] Prueba de contrato de `GET /galpones/{id}/transiciones`, `POST /galpones/{id}/estado` y `GET /galpones/{id}/historial-estados`

### Implementation for User Story 3

- [ ] T035 [P] [US3] Crear `ActualizarEstadoRequest` y `HistorialEstadoResponse` en `main/dto/`
- [ ] T036 [US3] Implementar en `GalponService` las transiciones que puede pedir el administrador (En cosecha y Mantenimiento → Disponible) llamando a `GalponEstadoService` con origen `ADMINISTRADOR`
- [ ] T037 [US3] Implementar los tres endpoints en `GalponController`

**Checkpoint**: El ciclo de vida manual funciona; las demás transiciones llegan con sus historias.

---

## Phase 6: User Story 4 - Registrar lote (Priority: P1)

**Goal**: El administrador registra un lote en un galpón Disponible y el galpón pasa a Productivo en la misma transacción.

**Independent Test**: `POST /lotes` en un galpón Disponible devuelve `201`, `poblacionActual` igual a la inicial y el galpón queda Productivo; si falla el cambio de estado no queda lote guardado.

### Tests for User Story 4

- [ ] T038 [P] [US4] Prueba de contrato de `POST /lotes` y `GET /lotes`: `201`, `400` (fecha futura, población o costo `0`, negativos, decimales o texto), `404` y `409` (nombre repetido, galpón no disponible)
- [ ] T039 [P] [US4] Prueba de integración: registrar lote y cambio de estado son atómicos; una fecha pasada se acepta

### Implementation for User Story 4

- [ ] T040 [P] [US4] Crear `RegistrarLoteRequest` en `main/dto/` con `@PastOrPresent` y `@Positive`
- [ ] T041 [US4] Implementar `LoteService.registrar`: galpón Disponible, nombre único, población actual igual a la inicial y solicitud `DISPONIBLE → PRODUCTIVO` con origen `REGISTRO_LOTE`
- [ ] T042 [US4] Implementar `LoteController` con `POST /lotes` y `GET /lotes?activos=true`
- [ ] T043 [US4] Pruebas unitarias de `LoteService` en `test/service/`

**Checkpoint**: Un galpón puede tener producción.

---

## Phase 7: User Story 5 - Recibir mortalidad del galpón y Actualizar población actual (Priority: P1)

**Goal**: El Módulo 2 informa pollos muertos y la población actual del lote activo se descuenta una sola vez.

**Independent Test**: Un lote de 1.000 pollos recibe 25 muertos y queda en 975; reenviar la misma alerta no descuenta de nuevo; 101 muertos sobre 100 devuelve `409`.

### Tests for User Story 5

- [ ] T044 [P] [US5] Prueba de contrato de `POST /integracion/modulo-2/mortalidad` con todos los rechazos de ambos specs
- [ ] T045 [P] [US5] Prueba de integración: descuentos sucesivos (25 y 10 sobre 1.000 dan 965), población en cero sin cambio de estado, alerta repetida y UUID con datos distintos

### Implementation for User Story 5

- [ ] T046 [P] [US5] Crear `MortalidadRequest` y `PoblacionResponse` en `main/dto/`
- [ ] T047 [US5] Implementar `PoblacionService.descontar`: lote activo del galpón, cantidad no mayor que la población actual, población inicial intacta
- [ ] T048 [US5] Implementar `MortalidadService.recibir`: validaciones, galpón Productivo, fecha del día, idempotencia con `registro_procesamiento` y llamada a `PoblacionService` en la misma transacción
- [ ] T049 [US5] Implementar el endpoint en `IntegracionModulo2Controller`
- [ ] T050 [US5] Pruebas unitarias de `PoblacionService` y `MortalidadService`

**Checkpoint**: La población del lote refleja la mortalidad.

---

## Phase 8: User Story 6 - Recibir alerta sanitaria (Priority: P1)

**Goal**: El Módulo 2 aísla un galpón Productivo o lo reanuda desde Aislamiento, sin que el Módulo 1 guarde la alerta.

**Independent Test**: Una alerta de aislamiento para un galpón Productivo lo deja en Aislamiento con origen `ALERTA_SANITARIA_MODULO_2`; una reanudación lo devuelve a Productivo; un lote histórico devuelve `409`.

### Tests for User Story 6

- [ ] T051 [P] [US6] Prueba de contrato de `POST /integracion/modulo-2/alertas-sanitarias`: aislamiento sin tipo o descripción (`400`), sin galpón ni lote (`400`), fecha de otro día (`400`), acción incompatible con el estado (`409`)
- [ ] T052 [P] [US6] Prueba de integración: la alerta identificada por lote resuelve su galpón y no modifica el lote

### Implementation for User Story 6

- [ ] T053 [P] [US6] Crear `AlertaSanitariaRequest` en `main/dto/` con la validación condicional según la acción
- [ ] T054 [US6] Implementar `AlertaSanitariaService.recibir`: resolver el galpón directamente o por el lote activo y solicitar el cambio a `GalponEstadoService`
- [ ] T055 [US6] Implementar el endpoint en `IntegracionModulo2Controller`

**Checkpoint**: El galpón refleja la situación sanitaria.

---

## Phase 9: User Story 7 - Recibir vaciado sanitario (Priority: P1)

**Goal**: Al terminar la cosecha el galpón pasa a Vaciado sanitario, el lote se desvincula y, al cumplirse el periodo, vuelve solo a Disponible.

**Independent Test**: Una alerta para un galpón En cosecha lo deja en Vaciado sanitario, con el lote desvinculado y la alerta guardada; con el periodo cumplido, el proceso automático lo deja Disponible.

### Tests for User Story 7

- [ ] T056 [P] [US7] Prueba de contrato de `POST /integracion/modulo-2/vaciado-sanitario` con los rechazos del spec
- [ ] T057 [P] [US7] Prueba de integración: registro, desvinculación y cambio de estado son atómicos; la alerta repetida no duplica nada; con el periodo vacío el proceso no cambia el estado

### Implementation for User Story 7

- [ ] T058 [P] [US7] Crear `VaciadoSanitarioRequest` en `main/dto/`
- [ ] T059 [US7] Implementar `VaciadoSanitarioService.recibir`: guardar la alerta con sus UUID, marcar `desvinculado_en` en el lote y solicitar `EN_COSECHA → VACIADO_SANITARIO`
- [ ] T060 [US7] Implementar `VaciadoSanitarioService.finalizarPeriodosCumplidos` y `VaciadoSanitarioJob` en `main/api/job/` con `@Scheduled`; activar `@EnableScheduling` en `main/config/`
- [ ] T061 [US7] Implementar el endpoint en `IntegracionModulo2Controller`

**Checkpoint**: El ciclo completo del galpón se cierra y vuelve a Disponible.

---

## Phase 10: User Story 8 - Generar alerta de mantenimiento (Priority: P1)

**Goal**: El técnico reporta una falla en un galpón Disponible y el administrador la atiende, lo que pasa el galpón a Mantenimiento.

**Independent Test**: El técnico crea una alerta Pendiente sin cambiar el estado; una segunda alerta para el mismo galpón devuelve `409`; al atenderla el galpón queda en Mantenimiento.

### Tests for User Story 8

- [ ] T062 [P] [US8] Prueba de contrato de `POST /alertas-mantenimiento`, `GET /alertas-mantenimiento` y `POST /alertas-mantenimiento/{id}/atender`
- [ ] T063 [P] [US8] Prueba de repositorio: el índice parcial impide dos alertas pendientes para el mismo galpón aunque lleguen a la vez

### Implementation for User Story 8

- [ ] T064 [P] [US8] Crear `GenerarAlertaMantenimientoRequest` y `AlertaMantenimientoResponse` en `main/dto/`, y `AlertaMantenimientoMapper` en `main/mapper/`
- [ ] T065 [US8] Implementar `AlertaMantenimientoService.generar` (galpón Disponible, descripción obligatoria de hasta 500 caracteres, una pendiente por galpón) y `atender` (alerta `ATENDIDA` y solicitud `DISPONIBLE → MANTENIMIENTO` con origen `ADMINISTRADOR`)
- [ ] T066 [US8] Implementar `AlertaMantenimientoController`

**Checkpoint**: Todas las transiciones de la matriz tienen un camino.

---

## Phase 11: User Story 9 - Editar galpón (Priority: P3)

**Goal**: El administrador cambia el nombre de un galpón en cualquier estado y el aforo solo en Mantenimiento, con historial.

**Independent Test**: Cambiar el nombre de "Galpón C" a "Galpón Central" devuelve `200` y deja un registro en el historial; cambiar el aforo de un galpón Disponible devuelve `409`.

### Tests for User Story 9

- [ ] T067 [P] [US9] Prueba de contrato de `PATCH /galpones/{id}`: guardar el mismo nombre no es duplicado; aforo fuera de Mantenimiento devuelve `409`

### Implementation for User Story 9

- [ ] T068 [P] [US9] Crear `EditarGalponRequest` en `main/dto/` con ambos campos opcionales
- [ ] T069 [US9] Implementar `GalponService.editar`: unicidad que excluye al propio galpón, aforo solo en Mantenimiento y un registro en `historial_cambio_galpon` por campo cambiado
- [ ] T070 [US9] Implementar `PATCH /galpones/{id}` en `GalponController`

**Checkpoint**: Los 10 casos de uso del Módulo 1 funcionan de forma independiente.

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a varias historias.

- [ ] T071 [P] Revisar que cada escenario de aceptación de los 10 specs tenga su prueba y completar las que falten
- [ ] T072 [P] Alcanzar la cobertura propuesta en `service` según el informe de JaCoCo
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
- **US3 Actualizar estado**: solo de Foundational (`GalponEstadoService`).
- **US4 Registrar lote**: de Foundational; se prueba con galpones creados en la prueba.
- **US5 Mortalidad y población, US6 Alerta sanitaria, US7 Vaciado sanitario**: necesitan un lote activo; se prueban con datos de prueba, sin depender de US4.
- **US8 Alerta de mantenimiento**: solo de Foundational.
- **US9 Editar galpón**: sin dependencias entre historias.

Con varios integrantes, después de la Fase 2 las historias pueden repartirse en paralelo. Con una sola persona, el orden sugerido es el de las fases.

### Within Each User Story

- DTOs y mappers antes que el servicio.
- Servicio antes que el controlador.
- La historia completa y probada antes de pasar a la siguiente.
- Las pruebas de contrato se pueden escribir primero y ejecutar al terminar la implementación.

## Open Questions

Decisiones que los specs no cierran. El plan propone una opción; hay que confirmarlas con el equipo antes de implementar la historia afectada.

1. **Autenticación y roles.** Ningún spec la define (actualizar estado FR-008, consultar FR-022). Propuesta: fuera de alcance por ahora; con Spring Security se agregaría como una fase aparte.
2. **Actualizar población actual: ¿automática o con confirmación del administrador?** El plan la hace automática dentro de la transacción de "Recibir mortalidad" para cumplir la atomicidad (FR-017). Los mockups mostraron una confirmación; si se quiere, hace falta un estado "solicitud pendiente".
3. **Periodo del vaciado sanitario.** Cuántos días dura; mientras no se defina, el proceso automático no cambia el estado.
4. **Historial de lotes después del vaciado.** Al desvincular el lote se pierde la relación activa. El plan conserva `galpon_id` en el lote y marca `desvinculado_en`, de modo que el historial de Consultar sigue funcionando. Confirmar que esa es la interpretación correcta de "eliminar la relación activa".
5. **Lote activo.** Consultar lo define como "el de ingreso más reciente"; población y mortalidad como "el que referencia actualmente al galpón". El plan los une: el de ingreso más reciente que no está desvinculado.
6. **Disponible → Mantenimiento.** El plan solo lo permite al atender una alerta pendiente (`/alertas-mantenimiento/{id}/atender`). Confirmar si el administrador puede pedirlo también sin alerta.
7. **Población inicial frente al aforo máximo.** Ningún spec impide registrar más pollos que el aforo del galpón. Confirmar si debe validarse.
8. **Operario de granja.** Consultar menciona operarios, pero no aparecen en los casos de uso.
9. **Orden por estado.** Confirmar si el orden de los estados es alfabético o el del ciclo de vida.
10. **Metas de rendimiento y cobertura.** Las cifras de este plan son propuestas.

## Notes

- `[P]` = puede hacerse en paralelo con otras tareas `[P]` del mismo bloque.
- `[USn]` enlaza la tarea con su historia para la trazabilidad.
- Cada historia debe poder completarse y probarse por separado.
- Hacer commit después de cada tarea o grupo lógico de tareas.
- Detenerse en cada checkpoint para validar la historia.
- Evitar tareas vagas, conflictos en el mismo archivo y dependencias entre historias que rompan su independencia.
