# Implementation Plan: AVICONTROL Modulo 1 - Gestion de Galpones e Infraestructura

**Fecha**: 2026-10-02
**Arquitectura y tecnologías**: [arquitectura.md](../arquitectura/arquitectura.md)

## Summary

El Modulo 1 es la fuente de verdad sobre los galpones y lotes de pollos. Gestiona el ciclo de vida del galpon (maquina de estados de 6 estados y 9 transiciones), mantiene la poblacion actual del lote y expone los datos que el Modulo 2 y el Modulo 3 necesitan.

Implementacion: monolito con arquitectura hexagonal (domain, application, infrastructure) en Java 21 + Spring Boot. El Modulo 2 se integra por Apache Kafka (consumers). El Modulo 3 consulta por API REST. Persistencia en PostgreSQL con Flyway.

## Technical Context

Stack, arquitectura hexagonal, reglas de persistencia, API, tiempo y pruebas: ver [arquitectura.md](../arquitectura/arquitectura.md). Este plan no los repite.

**Alcance específico del Módulo 1**:
- Servicio backend: API REST para consultas síncronas (Módulo 3) y consumers Kafka para eventos del Módulo 2.
- Una sola granja, decenas de galpones, 10 casos de uso, 1 administrador, 1 técnico de infraestructura.
- Rendimiento: p95 menor a 300 ms en consultas y escrituras; paginación de 10 registros.

**Restricciones propias del módulo**:
- Las operaciones multi-paso son atómicas: registrar lote + cambio de estado; vaciado + desvinculación de lote.
- Los eventos del Módulo 2 son idempotentes: el UUID de alerta es la clave en `RegistroProcesamiento`.
- Nombres de galpón y lote únicos sin distinguir mayúsculas y sin espacios extremos.
- Autenticación fuera del alcance de este plan.

---

## Project Structure

### Documentation

```
docs/
├── arquitectura/arquitectura.md      (referencia técnica compartida)
├── plan-general/
│   ├── plan-template.md
│   └── plan-modulo-1.md              (este archivo: lo compartido + índice)
├── Casos de uso.md
└── spec-<caso-de-uso>/               (10 carpetas)
    ├── <spec>.md
    └── plan-<caso-de-uso>.md         (plan de implementación de ese spec)
```

### Source Code (esqueleto; las clases de cada caso están en su plan)

```
avicontrol/
├── pom.xml
├── docker-compose.yml
└── src/
    ├── main/java/com/unimag/avicontrol/
    │   ├── domain/           model/, port/out/, policy/, exception/
    │   ├── application/      port/in/, usecase/
    │   └── infrastructure/
    │       ├── in/rest/      controladores, dto/, mapper/, GlobalExceptionHandler
    │       ├── in/kafka/     consumers y dto/ (mensajes del Módulo 2)
    │       ├── in/job/       tareas programadas
    │       ├── out/persistence/  entity/, repository/, adapter/, mapper/
    │       └── config/
    └── test/java/com/unimag/avicontrol/
        domain/, application/, infrastructure/, arquitectura/, soporte/
```

**Structure Decision**: arquitectura hexagonal (ver arquitectura.md). Los eventos del Módulo 2 entran por `infrastructure/in/kafka/` y las consultas del Módulo 3 por `infrastructure/in/rest/`. Los records Kafka y los DTOs REST son clases separadas aunque compartan campos de los specs.

---
## Phase 1: Setup

**Purpose**: Dejar el proyecto listo para implementar API REST, consumers Kafka y base de datos.

- [ ] T001 Crear estructura de paquetes en main/ y test/ segun el arbol de Project Structure
- [ ] T002 Actualizar pom.xml: agregar spring-boot-starter-webmvc, spring-boot-starter-validation, spring-kafka, Flyway, Testcontainers (solo PostgreSQL), ArchUnit y dependencias MockMvc; quitar spring-boot-starter-restclient. Verificar artefactos Boot 4.1.1 con mvnw dependency:tree
- [ ] T003 [P] Crear docker-compose.yml con PostgreSQL y Kafka para desarrollo local
- [ ] T004 [P] Configurar application.properties y application-test.properties: datasource, Kafka bootstrap servers, ddl-auto=validate, Jackson strict, avicontrol.*
- [ ] T005 [P] Agregar plugin JaCoCo al pom.xml

---

## Phase 2: Foundational (Bloqueante)

**Purpose**: Infraestructura tecnica que todas las historias necesitan.

**CRITICO**: Ninguna historia empieza hasta completar esta fase.

- [ ] T006 Escribir V1__esquema_inicial.sql: 7 tablas, restricciones CHECK, indices unicos lower(nombre), indices parciales (lote activo, alerta pendiente por galpon)
- [ ] T007 [P] Crear enums EstadoGalpon, OrigenCambioEstado, EstadoAlertaMantenimiento y AccionSanitaria en main/domain/model/
- [ ] T008 [P] Crear modelos de dominio Galpon y Lote (Java puro) en main/domain/model/; crear GalponEntity y LoteEntity (con @Version) en main/infrastructure/out/persistence/entity/
- [ ] T009 [P] Crear modelos de dominio HistorialEstado, HistorialCambioGalpon, AlertaMantenimiento, AlertaVaciadoSanitario y RegistroProcesamiento en main/domain/model/; crear sus entidades JPA en main/infrastructure/out/persistence/entity/
- [ ] T010 Crear interfaces de puertos de salida en main/domain/port/out/ (incluida GalponRepository.findByIdForUpdate); crear interfaces JpaRepository en main/infrastructure/out/persistence/repository/; crear adaptadores en main/infrastructure/out/persistence/adapter/ (depende de T008, T009)
- [ ] T011 [P] Crear mappers de persistencia en main/infrastructure/out/persistence/mapper/
- [ ] T012 [P] Crear excepciones de negocio en main/domain/exception/
- [ ] T013 [P] Crear GlobalExceptionHandler en main/infrastructure/in/rest/: traduce excepciones de dominio a ProblemDetail (RFC 9457) con lista de errores por campo
- [ ] T014 [P] Crear ClockConfig (zona America/Bogota), JacksonConfig (ACCEPT_FLOAT_AS_INT=false), KafkaConsumerConfig (grupo modulo-1, deserializador JSON) y AvicontrolProperties en main/infrastructure/config/
- [ ] T015 Crear TransicionesPermitidas en main/domain/policy/ con la matriz de 9 transiciones
- [ ] T016 Crear GalponEstadoService en main/application/usecase/: metodo solicitarCambio invoca GalponRepository.findByIdForUpdate, valida en TransicionesPermitidas, persiste cambio y escribe historial (depende de T010, T015)
- [ ] T017 [P] Pruebas unitarias de TransicionesPermitidas y GalponEstadoService en test/application/: 9 transiciones validas, invalidas y estado ya cambiado
- [ ] T018 [P] Crear clase base Testcontainers en test/soporte/ con PostgreSQL y Clock fijo (solo para pruebas de persistencia)
- [ ] T019 [P] Escribir reglas ArchUnit en test/arquitectura/: direccion de dependencias, JPA ausente en domain y application, @Transactional solo en application/usecase, solo GalponEstadoService escribe estado

**Checkpoint**: esquema migra, entidades validan, maquina de estados funciona, ArchUnit pasa.

---

## Planes por spec (Phases 3 a 11)

Cada caso de uso tiene su propio plan de implementación junto a su spec. Las tareas conservan su numeración T021-T079.

| Historia | Spec | Plan | Tareas |
|---|---|---|---|
| US1 (P1) | Registrar galpón | [plan-registrar-galpon](../spec-registrarGalpon/plan-registrar-galpon.md) | T021-T028 |
| US2 (P1) | Registrar lote | [plan-registrar-lote](../spec-registrarLote/plan-registrar-lote.md) | T029-T036 |
| US3 (P1) | Consultar galpón y lote | [plan-consultar-galpon-lote](../spec-consultar-galpón-lote/plan-consultar-galpon-lote.md) | T037-T042 |
| US4 (P1) | Actualizar estado | [plan-actualizar-estado](../spec-actualizarEstado/plan-actualizar-estado.md) | T043-T047 |
| US5 (P1) | Actualizar población actual | [plan-actualizar-poblacion-actual](../spec-actualizarPoblaciónActual/plan-actualizar-poblacion-actual.md) | T050-T052, T055 (parte) |
| US5 (P1) | Recibir mortalidad del galpón | [plan-recibir-mortalidad](../spec-recibirMortalidadDelGalpón/plan-recibir-mortalidad.md) | T048, T050-T051, T053-T055 (parte) |
| US6 (P1) | Recibir alerta sanitaria | [plan-recibir-alerta-sanitaria](../spec-recibirAlertaSanitaria/plan-recibir-alerta-sanitaria.md) | T056-T061 |
| US7 (P1) | Recibir vaciado sanitario | [plan-recibir-vaciado-sanitario](../spec-recibirVaciadoSanitario/plan-recibir-vaciado-sanitario.md) | T062-T068 |
| US8 (P1) | Generar alerta de mantenimiento | [plan-generar-alerta-mantenimiento](../spec-generarAlertaMantenimiento/plan-generar-alerta-mantenimiento.md) | T069-T074 |
| US9 (P3) | Editar galpón | [plan-editar-galpon](../spec-editarGalpon/plan-editar-galpon.md) | T075-T079 |

---

## Phase 12: Polish y preocupaciones transversales

**Purpose**: Mejoras que afectan multiples historias.

- [ ] T080 [P] Verificar que cada escenario de aceptacion de los 10 specs tenga su prueba; completar las faltantes
- [ ] T081 [P] Alcanzar 80% de cobertura de lineas en application/usecase segun JaCoCo
- [ ] T082 [P] Documentar en README.md: levantar PostgreSQL + Kafka con Docker Compose, ejecutar pruebas, endpoints REST y topicos Kafka para el Modulo 2
- [ ] T083 [P] Agregar logs de cambios de estado y alertas rechazadas en consumers Kafka
- [ ] T084 Revisar manejo de errores cuando base de datos o Kafka no estan disponibles: mensaje generico y sin cambios parciales

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias — empieza de inmediato
- **Foundational (Phase 2)**: depende de Setup — bloquea todas las historias
- **User Stories (Phases 3 a 11)**: dependen de Foundational — pueden repartirse en paralelo con varios integrantes
- **Polish (Phase 12)**: depende de las historias entregadas

### User Story Dependencies

- **US1 Registrar galpon**: sin dependencias entre historias
- **US2 Registrar lote**: sin dependencias; galpones se crean en la prueba
- **US3 Consultar**: sin dependencias; sirve para verificar resultados de otras historias
- **US4 Actualizar estado**: solo de Foundational (GalponEstadoService ya listo)
- **US5 Mortalidad**: necesita lote activo creado con datos de prueba
- **US6 Alerta sanitaria**: necesita lote activo creado con datos de prueba
- **US7 Vaciado sanitario**: necesita galpon En cosecha creado con datos de prueba
- **US8 Alerta de mantenimiento**: solo de Foundational
- **US9 Editar galpon**: sin dependencias entre historias

Con varios integrantes, despues de Phase 2 las historias pueden repartirse. Con una persona, seguir el orden de las fases.

### Within Each User Story

- Modelos de dominio y entidades JPA antes que puertos y adaptadores
- Puerto de entrada antes que el caso de uso
- Caso de uso antes que el adaptador de entrada (REST o Kafka)
- Historia completa y probada antes de pasar a la siguiente
- Pruebas de contrato y de consumer se pueden escribir primero (TDD)

---

## Open Questions

1. **Autenticacion y roles**: ningún spec la define. Propuesta: fuera del alcance del Modulo 1; se agrega con Spring Security en una fase separada.
2. **Periodo de vaciado sanitario**: cuantos dias configura avicontrol.vaciado-sanitario.dias; mientras no se defina el job no ejecuta la transicion automatica.
3. **Capacidad vs. poblacion inicial**: ningun spec valida que la poblacion no supere la capacidad del galpon. Confirmar si debe validarse.
4. **Operario de granja**: puede consultar galpones y lotes. Confirmar si tambien puede ver alertas de mantenimiento.
5. **Reintentos Kafka**: politica de retry y dead-letter topic para mensajes que fallan en el consumer; definir en KafkaConsumerConfig antes de implementar Phase 7.
6. **Formato de mensaje Kafka**: confirmar con el equipo del Modulo 2 que el esquema JSON de cada mensaje coincide con los campos definidos en los specs antes de implementar los consumers.
7. **Metas de rendimiento y cobertura**: las cifras de este plan son propuestas sujetas a validacion del equipo.
8. **Lote activo**: el spec de consultar usa "el de fecha de ingreso mas reciente"; el plan usa `desvinculado_en IS NULL`. Confirmar el criterio (detalle en plan-consultar-galpon-lote).
9. **Rechazos hacia el Modulo 2**: los specs de mortalidad, alerta sanitaria y vaciado piden informar el rechazo, pero Kafka es asincrono. Definir log + dead-letter topic o un topic de respuesta.
10. **Contratos por spec**: las rutas REST, los nombres de campo y los codigos de error son propuestas de cada plan por spec; los mensajes Kafka deben acordarse con el Modulo 2.

## Notes

- Por indicacion del profesor no se escriben pruebas de integracion ni end-to-end en el proyecto. Se eliminaron T020, T030, T049, T057 y T063 (su numeracion no se reutiliza) y sus casos se trasladaron a pruebas unitarias (T017, T036, T055, T056, T062).
- Las pruebas de persistencia con Testcontainers PostgreSQL (T022, T038, T070) se mantienen porque verifican indices y consultas propias de PostgreSQL; confirmar con el profesor que no cuentan como integracion.

- [P] = puede hacerse en paralelo con otras tareas [P] del mismo bloque
- [USn] enlaza la tarea con su historia para trazabilidad
- Cada historia debe poder completarse y probarse de forma independiente
- Commit despues de cada tarea o grupo logico
- Detenerse en cada checkpoint para validar la historia antes de continuar
