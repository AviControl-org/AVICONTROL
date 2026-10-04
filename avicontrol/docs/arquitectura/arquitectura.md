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

- Flyway es la fuente de verdad para crear y modificar el esquema; Hibernate valida el esquema sin actualizarlo automáticamente.
- Las restricciones de integridad se aplican en PostgreSQL y, cuando corresponda, en las reglas del dominio/aplicación.
- Cada operación que escribe datos define su límite transaccional de acuerdo con el spec. Los cambios de estado, historiales y operaciones atómicas se coordinan mediante los casos de uso autorizados.
- Se emplea bloqueo pesimista únicamente para validar y persistir cambios de estado concurrentes del mismo galpón.

## API y tiempo

- La API usa JSON y DTOs validados; no retorna entidades JPA.
- Los errores REST utilizan `ProblemDetail` compatible con RFC 9457 y agregan errores de campo cuando aplica.
- Cada error incluye un `code` estable para el cliente (por ejemplo `NOMBRE_DUPLICADO`, `GALPON_NO_ENCONTRADO`) y, cuando aplica, `fieldErrors` con `field`, `code` y `message`.
- Mapeo general: `400` datos inválidos, `404` recurso inexistente, `409` conflicto de estado, nombre duplicado o concurrencia, `500` error técnico con mensaje genérico.

```json
{
  "title": "Conflicto",
  "status": 409,
  "detail": "Ya existe un galpón con ese nombre",
  "instance": "/api/v1/galpones",
  "code": "NOMBRE_DUPLICADO",
  "fieldErrors": [{ "field": "nombre", "code": "DUPLICADO", "message": "El nombre ya está en uso" }]
}
```

- Los campos enteros rechazan valores decimales.
- Las reglas de fecha utilizan un `Clock` inyectado y la zona de negocio `America/Bogota`.

## Pruebas

- JUnit 5, Mockito y AssertJ para las reglas del dominio y los casos de uso.
- MockMvc para validar rutas, payloads, errores y códigos HTTP.
- Testcontainers PostgreSQL para repositorios, migraciones y restricciones de base de datos.
- ArchUnit para validar la dirección de dependencias y el aislamiento del dominio.
- Por indicación del profesor, el proyecto no incluye pruebas de integración ni end-to-end. Los consumers Kafka y los jobs se prueban de forma unitaria con Mockito.

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

### Reglas de los adaptadores Kafka

- Los consumers Kafka pertenecen a `infrastructure/in/kafka/` y no contienen reglas de negocio.
- Cada consumer deserializa el mensaje, construye el comando equivalente y lo delega al caso de uso a través de su puerto de entrada.
- La idempotencia se garantiza con el mismo mecanismo que los specs definen para los eventos: UUID de alerta como clave primaria en la tabla de procesamiento.
- Los producers Kafka (si aplican) pertenecen a `infrastructure/out/kafka/` e implementan un puerto de salida del dominio.

## Convenciones para los planes de implementación

Cada spec tiene su propio plan en su carpeta (`docs/spec-<caso-de-uso>/plan-<caso-de-uso>.md`). `plan-modulo-1.md` contiene solo lo compartido: contexto del módulo, Setup, Foundational, índice de planes por spec, dependencias y preguntas abiertas.

Todo plan por spec debe incluir al inicio:

```markdown
**Spec**: [archivo-spec.md](archivo-spec.md)
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)
**Plan general**: [plan-modulo-1.md](../plan-general/plan-modulo-1.md)
```

Los planes por spec no deben repetir:

- La lista de tecnologías ni la explicación de la arquitectura hexagonal.
- Las reglas generales de persistencia, API, tiempo, pruebas o Kafka.
- Las tareas de Setup y Foundational del plan general.

Cada plan se limita a:

- Resumen del spec cubierto.
- Contexto, restricciones e integraciones propias del caso de uso.
- Clases y adaptadores propios.
- Tareas de pruebas e implementación, con la numeración del plan general.
- Dependencias con otros specs.
- Decisiones pendientes que la arquitectura no cubre.
