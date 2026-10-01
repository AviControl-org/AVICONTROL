# Arquitectura y Stack Tecnológico - Módulo 1

**Estado**: Referencia técnica para los planes del Módulo 1  
**Fecha**: 2026-09-28

## Propósito

Este documento define el stack y las reglas arquitectónicas compartidas para implementar el Módulo 1. Los planes funcionales deben enlazar esta referencia y concentrarse en el alcance, los modelos, las reglas, los contratos y las tareas propias de cada entrega.

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
| Desarrollo local | Docker Compose | PostgreSQL local |

Las versiones administradas por Spring Boot se toman de su gestión de dependencias. No se incorporan Spring Security, Kafka ni Spring Modulith: no son requerimientos de los specs del Módulo 1. La comunicación con otros módulos se realiza mediante los contratos HTTP definidos por cada caso de uso.

## Arquitectura

El Módulo 1 se implementa como un monolito modular con arquitectura hexagonal. Los casos de uso se aíslan del transporte REST, de la planificación y de PostgreSQL mediante puertos y adaptadores.

### Dominio

`domain/` contiene modelos, políticas, invariantes, excepciones y puertos de salida. Es Java puro y no depende de Spring, JPA, Jackson ni DTOs de infraestructura. Las entidades de persistencia son modelos separados y no se exponen mediante la API.

### Aplicación

`application/` contiene los puertos de entrada y los casos de uso. Coordina el dominio con los puertos de salida y define los límites transaccionales sin conocer HTTP ni detalles de JPA. Los casos de uso implementan sus puertos de entrada; los adaptadores los invocan.

### Infraestructura

`infrastructure/` contiene:

- Adaptadores de entrada REST: controladores, DTOs, validación sintáctica, mappers y manejo HTTP de errores.
- Adaptadores de entrada programados: activan casos de uso sin contener reglas de negocio.
- Adaptadores de salida de persistencia: entidades JPA, repositorios, mappers y bloqueos requeridos por las reglas de concurrencia.
- Configuración de Spring, Jackson, propiedades y `Clock`.

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
- Los campos enteros rechazan valores decimales.
- Las reglas de fecha utilizan un `Clock` inyectado y la zona de negocio `America/Bogota`.

## Pruebas

- JUnit 5, Mockito y AssertJ para las reglas del dominio y los casos de uso.
- MockMvc para validar rutas, payloads, errores y códigos HTTP.
- Testcontainers PostgreSQL para repositorios, migraciones y restricciones de base de datos.
- ArchUnit para validar la dirección de dependencias y el aislamiento del dominio.
- Las pruebas de integración/end-to-end no forman parte del alcance del plan general actual.
