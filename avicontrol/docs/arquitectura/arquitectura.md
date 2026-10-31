# Arquitectura y Stack Tecnológico - AVICONTROL

**Estado**: Referencia técnica compartida para todos los módulos de AVICONTROL  
**Fecha**: 2026-09-28

## Propósito

Este documento define el stack, las reglas arquitectónicas y el modelo de comunicación entre módulos del sistema AVICONTROL. Los planes funcionales de cada módulo deben enlazar esta referencia y concentrarse en el alcance, los modelos, las reglas, los contratos y las tareas propias de su entrega.

## Stack tecnológico

| Área | Tecnología | Uso |
| --- | --- | --- |
| Lenguaje | Java 21 | Dominio, casos de uso, adaptadores y pruebas |
| Framework principal | Spring Boot 4.1.1 | Configuración y ejecución de la aplicación |
| Construcción | Maven Wrapper | Dependencias, compilación y pruebas mediante `pom.xml`, `mvnw` y `mvnw.cmd` |
| API HTTP | Spring Web MVC | API REST síncrona |
| Validación | Jakarta Bean Validation | Validación de DTOs de entrada |
| Persistencia | Spring Data JPA e Hibernate | Acceso relacional desde adaptadores de salida |
| Base de datos | PostgreSQL | Persistencia de los datos del Módulo 1 |
| Migraciones | Flyway | Versionamiento y aplicación del esquema |
| Serialización | Jackson | JSON; configuración estricta de campos enteros |
| Pruebas unitarias | JUnit 5, Mockito y AssertJ | Dominio y casos de uso |
| Pruebas HTTP | MockMvc | Contratos REST |
| Pruebas de persistencia | Testcontainers PostgreSQL | Consultas y restricciones específicas de PostgreSQL |
| Reglas arquitectónicas | ArchUnit | Verificación de dependencias entre módulos arquitectónicos |
| Utilidades | Lombok | Reducción de código repetitivo donde aporte claridad |
| Mensajería | Apache Kafka | Comunicación asíncrona de eventos entre módulos |
| Kafka client | Spring Kafka | Productores y consumidores de mensajes Kafka |
| Desarrollo local | Docker Compose | PostgreSQL y Kafka para desarrollo local |

Las versiones administradas por Spring Boot se toman de su gestión de dependencias. No se incorporan Spring Security ni Spring Modulith. La comunicación sincrónica entre módulos (consultas del Módulo 3 al Módulo 1) se realiza mediante contratos REST. La comunicación asíncrona entre módulos (eventos del Módulo 2 hacia el Módulo 1) se realiza mediante Apache Kafka.

## Arquitectura

El Módulo 1 se implementa como un monolito modular con arquitectura hexagonal. Los casos de uso se aíslan del transporte REST, de la planificación y de PostgreSQL mediante puertos y adaptadores.

### Dominio

`domain/` contiene modelos, políticas, invariantes, excepciones y puertos de salida. Es Java puro y no depende de Spring, JPA, Jackson ni DTOs de infraestructura. Las entidades de persistencia son modelos separados y no se exponen mediante la API.

### Aplicación

`application/` contiene los puertos de entrada y los casos de uso. Coordina el dominio con los puertos de salida y define los límites transaccionales sin conocer HTTP ni detalles de JPA. Los casos de uso implementan sus puertos de entrada; los adaptadores los invocan.

### Infraestructura

`infrastructure/` contiene:

- Adaptadores de entrada REST: controladores, DTOs, validación sintáctica, mappers y manejo HTTP de errores.
- Adaptadores de entrada programados: activan casos de uso por planificación sin contener reglas de negocio.
- Adaptadores de entrada de mensajería: consumidores Kafka (`@KafkaListener`) que reciben eventos de otros módulos y los delegan al caso de uso correspondiente mediante su puerto de entrada; no contienen reglas de negocio.
- Adaptadores de salida de persistencia: entidades JPA, repositorios, mappers y bloqueos requeridos por las reglas de concurrencia.
- Adaptadores de salida de mensajería: productores Kafka que implementan puertos de salida del dominio para publicar eventos hacia otros módulos (cuando aplique).
- Configuración de Spring, Jackson, Kafka, propiedades y `Clock`.

### Regla de dependencias

```text
infrastructure → application → domain
```

- El dominio no depende de aplicación ni infraestructura.
- Aplicación depende del dominio y coordina los puertos.
- Infraestructura implementa puertos y adapta los mecanismos externos.
- Controladores y tareas programadas no acceden directamente a repositorios JPA.
- DTOs REST, modelos de dominio y entidades JPA se mantienen separados y se convierten mediante mappers.

## Persistencia y transacciones

- Flyway es la fuente de verdad para crear y modificar el esquema; Hibernate valida el esquema sin actualizarlo automáticamente. El diagrama entidad-relación del Módulo 1 está en [plan-modulo-1.md](../plan-general/plan-modulo-1.md#modelo-de-datos).
- Las restricciones de integridad se aplican en PostgreSQL y, cuando corresponda, en las reglas del dominio/aplicación.
- Cada operación que escribe datos define su límite transaccional de acuerdo con el spec. Los cambios de estado, historiales y operaciones atómicas se coordinan mediante los casos de uso autorizados.
- Se emplea bloqueo pesimista únicamente para validar y persistir cambios de estado concurrentes del mismo galpón.
- El bloqueo optimista (`@Version` en galpón y lote) protege las ediciones normales (nombre, capacidad, población); el pesimista (`findByIdForUpdate`) protege solo los cambios de estado. No se mezclan en la misma operación sin justificarlo en el plan.
- Las violaciones de índices únicos (nombre de galpón o lote, alerta de mantenimiento pendiente) las captura el adaptador de persistencia y las traduce a excepciones de dominio; ninguna excepción de JPA sale de `infrastructure/out`.

## API y tiempo

- La API usa JSON y DTOs validados; no retorna entidades JPA.
- Los errores REST utilizan `ProblemDetail` compatible con RFC 9457 (`application/problem+json`) y agregan errores de campo cuando aplica. Los campos y el mapeo de estados están en [Convenciones de contrato](#convenciones-de-contrato).
- Los campos enteros rechazan valores decimales.
- Las reglas de fecha utilizan un `Clock` inyectado y la zona de negocio `America/Bogota`.

## Pruebas

- JUnit 5, Mockito y AssertJ para las reglas del dominio y los casos de uso.
- MockMvc para validar rutas, payloads, errores y códigos HTTP.
- Testcontainers PostgreSQL para repositorios, migraciones y restricciones de base de datos.
- ArchUnit para validar la dirección de dependencias y el aislamiento del dominio.
- Los JSON de ejemplo de cada plan viven como *fixtures* en `src/test/resources/contratos/` y son la evidencia de contrato: las pruebas MockMvc comparan contra ellos y las pruebas unitarias de los consumers los deserializan.
- Por indicación del profesor, el proyecto no incluye pruebas de integración ni end-to-end. Los consumers Kafka y los jobs se prueban de forma unitaria con Mockito.

## Convenciones de contrato

Valen para todos los servicios REST y mensajes Kafka del Módulo 1. Los planes por spec no las repiten: solo definen rutas, campos, reglas y errores propios.

| Regla | Detalle |
| --- | --- |
| Formato | JSON con `Content-Type: application/json`, UTF-8. |
| Nombres | Campos en `camelCase`; enumeraciones en `MAYUSCULA_CON_GUION_BAJO`. |
| Nulos | **Nunca se envía `null`**: un campo que no aplica se omite de la respuesta (`spring.jackson.default-property-inclusion=non_null`). En una petición, un campo ausente significa "no modificar" y un `null` explícito se trata igual que uno ausente (los records de Java no distinguen ambos casos); si tras eso no queda ningún campo, el `PATCH` responde `400 SIN_CAMBIOS`. |
| Fechas | `date` como `yyyy-MM-dd`. Un instante completo va en ISO-8601 **con desplazamiento** (`2026-10-05T10:30:00-05:00`); nunca sin zona. Las reglas de "hoy" y "mismo día" se evalúan en `America/Bogota` con el `Clock` inyectado. |
| Identificadores | UUID, siempre como texto. |
| Números | Enteros sin decimales; `costoTotal` en pesos colombianos. Un decimal o un texto en un campo entero es `400` (`ACCEPT_FLOAT_AS_INT=false`). |
| Paginación | `page` empieza en **1** (no en 0). Respuesta: `contenido`, `pagina`, `tamanio`, `totalElementos`, `totalPaginas`. El tamaño de página es fijo (10) salvo que el plan del servicio permita `size`. Una página sin resultados es `200` con `contenido: []` y `totalPaginas: 0`, nunca `404`. `size` solo existe en los listados que lo declaran (alertas de mantenimiento e historial de estados, por defecto 20, máximo 100). |
| Ordenamiento | Parámetro `orden=campo,direccion` (`asc` o `desc`) solo donde el spec lo pide. |
| Éxito | `POST` crea → `201` con `Location` y la representación creada. `PATCH` modifica → `200` con la representación actualizada. `GET` → `200`. |
| Caché | Toda respuesta `GET` lleva `Cache-Control: no-store`: lo que se muestra es la foto del momento. |
| Errores | RFC 9457. Ver la tabla siguiente. |

### Formato de error

Todo error responde `application/problem+json` con estos campos:

| Campo | Contenido |
| --- | --- |
| `type` | `https://avicontrol.unimag.edu.co/errors/<slug>`, donde `<slug>` es el `code` en minúsculas y con guiones (`NOMBRE_DUPLICADO` → `nombre-duplicado`). Es un identificador estable, no una página que deba existir. |
| `title` | Texto corto fijo por `code`. |
| `status` | Código HTTP. |
| `detail` | Mensaje fijo y claro para la persona; nunca expone excepciones ni SQL. |
| `instance` | Ruta de la petición. |
| `code` | Código estable para el cliente, en `MAYUSCULA_CON_GUION_BAJO`. |
| `fieldErrors` | Opcional: lista de `{field, code, message}` en los `400` de validación y en el `409 NOMBRE_DUPLICADO` (señala `nombre`). Valores de `fieldErrors.code`: `OBLIGATORIO`, `LONGITUD_INVALIDA`, `FORMATO_INVALIDO`, `ENTERO_POSITIVO`, `VALOR_NO_PERMITIDO`, `MINIMO_1`, `FUERA_DE_RANGO`, `DUPLICADO`. |

Mapeo general: `400` datos inválidos, `404` recurso inexistente, `409` conflicto de estado, nombre duplicado o concurrencia, `500` error técnico con `detail` genérico y sin cambios parciales.

Si una petición falla en varios campos a la vez, `code` es `VALIDACION_FALLIDA` y cada error va en `fieldErrors` con su código específico; con un solo campo erróneo, `code` es el código específico del plan (por ejemplo `NOMBRE_INVALIDO`).

Códigos genéricos que el manejador global produce en cualquier servicio, además de los de cada plan: `VALIDACION_FALLIDA` (varios campos inválidos), `CUERPO_INVALIDO` (el body no es un JSON válido), `PARAMETRO_INVALIDO` (UUID o número mal formado en la ruta o en un query param; `fieldErrors` nombra el parámetro), `CONFLICTO_CONCURRENCIA` (`409`, choque del `@Version`), `SOLICITUD_NO_SOPORTADA` (ruta, método o tipo de contenido que Spring MVC no soporta; conserva el status de Spring), `ERROR_CONSULTA` (`500` en un `GET`) y `ERROR_INTERNO` (`500` en una escritura).

```json
{
  "type": "https://avicontrol.unimag.edu.co/errors/nombre-duplicado",
  "title": "Nombre duplicado",
  "status": 409,
  "detail": "Ya existe un galpón con ese nombre.",
  "instance": "/api/v1/galpones",
  "code": "NOMBRE_DUPLICADO",
  "fieldErrors": [{ "field": "nombre", "code": "DUPLICADO", "message": "El nombre ya está en uso" }]
}
```

Los `code`, `title` y `detail` de cada servicio están en la tabla *Textos fijos de error* de su plan. Una consulta sin resultados no es error. Los errores técnicos (`ERROR_INTERNO`, `ERROR_CONSULTA`) siempre responden `500` con `detail` genérico.

### Estructura de carpetas

Cada plan por spec trae el código propio de su caso de uso y, por cada petición o mensaje, todos sus escenarios con path params, query params, body y respuesta.

```text
src/main/java/com/unimag/avicontrol/
├── domain/{model,policy,exception,port/out}
├── application/{port/in,usecase}
└── infrastructure/{in/rest,in/kafka,in/job,out/persistence,config,local}
src/main/resources/db/{migration,local}
src/test/java/com/unimag/avicontrol/{domain,application,infrastructure,arquitectura,soporte}
src/test/resources/contratos/
```

`infrastructure/local` y `db/local` existen solo para el perfil `local` (ver *Perfil local*).

## Perfil local

El perfil `local` permite desarrollar y hacer la demostración sin el Módulo 2:

- `docker-compose.yml` levanta PostgreSQL y Kafka.
- `application-local.properties` activa `db/local` en Flyway con datos de ejemplo (galpones en distintos estados y un lote activo).
- `SimuladorModulo2` (solo con el perfil `local`) publica en los tres tópicos los mismos JSON de los *fixtures*, para ver cómo reacciona el Módulo 1 sin depender de que el otro grupo tenga su módulo listo.

El simulador es una herramienta de demostración; no es una prueba de integración y no se ejecuta en `mvnw test`.

## Comunicación entre módulos

AVICONTROL es un sistema multi-módulo. La comunicación entre módulos sigue este patrón:

- **Síncrona (REST)**: cuando el consumidor necesita una respuesta inmediata (Módulo 3 consultando datos del Módulo 1).
- **Asíncrona (Kafka)**: cuando el productor emite un evento que el consumidor procesa de forma independiente (Módulo 2 notificando eventos al Módulo 1).

### Dirección de comunicación

| Origen | Destino | Mecanismo | Descripción |
| --- | --- | --- | --- |
| Módulo 2 | Módulo 1 | Kafka (consumer) | Eventos de mortalidad, alertas sanitarias y vaciado sanitario |
| Módulo 3 | Módulo 1 | REST (GET) | Consultas de lotes y galpones para liquidación |

### Tópicos de Kafka

| Tópico | Productor | Consumidor | Contenido del mensaje |
| --- | --- | --- | --- |
| `avicontrol.mortalidad` | Módulo 2 | Módulo 1 | UUID de alerta, galpón, lote, fecha/hora y cantidad de pollos muertos |
| `avicontrol.alerta-sanitaria` | Módulo 2 | Módulo 1 | UUID de alerta, galpón o lote, acción (AISLAMIENTO/REANUDACION), fecha/hora, tipo y gravedad |
| `avicontrol.vaciado-sanitario` | Módulo 2 | Módulo 1 | UUID de alerta, galpón, lote y fecha/hora del evento |

Los nombres de tópico son los de esta tabla. El esquema JSON de cada mensaje y la política de reintentos se definen en el plan del spec que lo consume.

### Sobre de eventos

El Módulo 2 define un sobre estándar para sus eventos de integración. Propuesta del Módulo 1 (pendiente P-06): el consumer lee el sobre y toma del `payload` los campos del mensaje; los demás campos del sobre se ignoran, salvo `eventType` y `eventVersion`.

| Campo del sobre | Uso en el Módulo 1 |
| --- | --- |
| `eventId`, `occurredAt`, `publishedAt`, `producer`, `correlationId`, `causationId`, `aggregateType`, `aggregateId` | Se ignoran (solo informativos o para el log). `eventId` identifica la entrega, no sustituye al UUID de alerta. |
| `eventType`, `eventVersion` | Se valida que correspondan al tópico y que la versión sea `1`; otro valor se rechaza. Los valores exactos de `eventType` se acuerdan con el Módulo 2. |
| `payload` | Contiene los campos de negocio que define cada plan (por ejemplo `alertaId`, `galponId`...). El JSON de ejemplo de cada plan es este `payload`. |

El consumer ignora campos desconocidos para tolerar evolución del contrato. La idempotencia usa siempre el `alertaId` del `payload`.

### Reglas de los adaptadores Kafka

- Los consumers Kafka pertenecen a `infrastructure/in/kafka/` y no contienen reglas de negocio.
- Cada consumer deserializa el mensaje, construye el comando equivalente y lo delega al caso de uso a través de su puerto de entrada.
- La idempotencia se garantiza con el mismo mecanismo que los specs definen para los eventos: UUID de alerta como clave primaria en la tabla de procesamiento.
- Los producers Kafka (si aplican) pertenecen a `infrastructure/out/kafka/` e implementan un puerto de salida del dominio.

## Convenciones para los planes de implementación

Cada spec tiene su propio plan en su carpeta (`docs/spec-<caso-de-uso>/plan-<caso-de-uso>.md`). `plan-modulo-1.md` contiene solo lo compartido: contexto del módulo, Setup, Foundational, catálogo de servicios, índice de planes por spec, dependencias y Polish. Las preguntas abiertas llevan un ID (`P-nn`) que cada plan cita en su sección *Pendientes de clarificación*.

Todo plan por spec debe incluir al inicio:

```markdown
**Spec**: [archivo-spec.md](archivo-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md)
```

Los planes por spec no deben repetir:

- La lista de tecnologías ni la explicación de la arquitectura hexagonal.
- Las reglas generales de persistencia, API, tiempo, pruebas o Kafka.
- Las convenciones de contrato (formato, nulos, fechas, paginación, formato de error).
- Las tareas de Setup y Foundational del plan general.

Secciones de cada plan, en este orden:

1. **Summary** y **Enfoque técnico** (pasos numerados de cómo funciona el caso de uso).
2. **Contexto específico**: tareas previas del plan general.
3. **Estructura del proyecto**: árbol con la ruta exacta de cada archivo que crea o ajusta el plan, y de sus pruebas.
4. **Decisiones de diseño**: reglas, concurrencia, índices o algoritmos que el spec no fija.
5. **Contrato**: por servicio, request/response DTO, path params, query params, body y errores; *Textos fijos de error* con un ejemplo JSON de error.
6. **Fuera de alcance**.
7. **Coherencia entre módulos** (solo en los specs compartidos con otros módulos).
8. **Fixtures**: archivos JSON de `src/test/resources/contratos/` que usa el plan.
9. **Tareas**: Goal, Independent Test, pruebas e implementación. Cada tarea cita el FR, SC o caso límite del spec que cubre.
10. **Dependencias** y **Pendientes de clarificación** (IDs `P-nn`).

## Coherencia con los otros módulos

Los specs de consultar galpón y lote, recibir mortalidad, recibir alerta sanitaria y recibir vaciado sanitario son la frontera con los Módulos 2 y 3. Para que ambos lados coincidan:

- Los nombres de campo, los seis valores de `EstadoGalpon`, los formatos (UUID y fechas como texto, zona America/Bogota) y las reglas de negocio compartidas (lote activo, edad del lote) se escriben una sola vez en el plan del spec y se comparten con el otro grupo como contrato.
- Los mensajes Kafka incluyen un JSON de ejemplo en el plan del spec (el `payload` del [sobre de eventos](#sobre-de-eventos)). El consumer ignora campos desconocidos y la idempotencia usa el UUID de negocio de la alerta.
- Las diferencias con los planes del Módulo 2 (`aforoMaximo` frente a `capacidad`, sobre de eventos, rutas sin versión, proceso único frente a procesos separados) quedan registradas como pendientes de clarificación (P-06, P-09, P-10, P-11) hasta acordarlas con los otros grupos.
