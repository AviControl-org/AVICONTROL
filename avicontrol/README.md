# AviControl, Módulo 1: Gestión de Galpones

Servicio del Módulo 1 de AviControl. Administra galpones y lotes, lleva el estado de cada galpón con su historial,
recibe eventos del Módulo 2 por Kafka y genera alertas de mantenimiento. Java 21, Spring Boot, PostgreSQL y Kafka,
con arquitectura hexagonal.

La documentación de diseño está en `docs/`: el plan general en `docs/plan-general/plan-modulo-1.md` y un plan por
historia de usuario en cada carpeta `docs/spec-*`.

## Requisitos

- JDK 21
- Docker Desktop en ejecución (base de datos, Kafka y pruebas de persistencia)

## Levantar PostgreSQL y Kafka

```
docker compose up -d
```

Crea dos contenedores:

| Contenedor | Puerto | Datos |
|---|---|---|
| `avicontrol-postgres` | 5432 | base, usuario y contraseña `avicontrol` |
| `avicontrol-kafka` | 9092 | sin autenticación |

El esquema lo crea Flyway al arrancar la aplicación (`src/main/resources/db/migration`).

## Ejecutar la aplicación

```
./mvnw spring-boot:run
```

En Windows con PowerShell:

```
.\mvnw spring-boot:run
```

La API queda en `http://localhost:8080`.

## Perfil `local` con el simulador del Módulo 2

Sirve para probar sin el Módulo 2. Carga galpones de ejemplo en cada estado con un lote activo y expone un
simulador que publica en Kafka los eventos que enviaría el Módulo 2.

```
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

Simulador (solo con este perfil). Cada petición recibe el `payload` del evento; el servicio lo envuelve en el sobre
del Módulo 2 y lo publica en el tópico:

| Método y ruta | Tópico | Tipo de evento |
|---|---|---|
| `POST /local/simulador-modulo2/mortalidad` | `avicontrol.mortalidad` | `MORTALIDAD_REGISTRADA` |
| `POST /local/simulador-modulo2/alerta-sanitaria` | `avicontrol.alerta-sanitaria` | `ALERTA_SANITARIA_EMITIDA` |
| `POST /local/simulador-modulo2/vaciado-sanitario` | `avicontrol.vaciado-sanitario` | `VACIADO_SANITARIO_SOLICITADO` |

Los cuerpos de ejemplo están en `src/test/resources/contratos/` (`mortalidad-payload.json`,
`alerta-sanitaria-*-payload.json`, `vaciado-sanitario-payload.json`). Las fechas del evento deben ser de hoy y de una
hora ya pasada.

## Pruebas

```
.\mvnw clean test
```

Necesita Docker en ejecución: las pruebas de persistencia levantan PostgreSQL con Testcontainers. El reporte de
cobertura queda en `target/site/jacoco/index.html`.

## Endpoints REST

Prefijo `/api/v1`. Los errores siguen RFC 9457 (`application/problem+json`) con un campo `code` propio. Las consultas
responden con `Cache-Control: no-store`.

| Método y ruta | Descripción |
|---|---|
| `POST /galpones` | Registrar galpón |
| `GET /galpones` | Listar galpones (filtros `nombre` y `estado`, `orden`, `page`) |
| `GET /galpones/{galponId}` | Detalle del galpón con su lote activo |
| `PATCH /galpones/{galponId}` | Editar nombre y capacidad (la capacidad solo en MANTENIMIENTO) |
| `GET /galpones/{galponId}/lotes` | Historial de lotes del galpón |
| `GET /galpones/{galponId}/transiciones` | Cambios de estado que puede pedir el administrador |
| `PATCH /galpones/{galponId}/estado` | Cambiar el estado (`estadoEsperado` y `estadoDestino`) |
| `GET /galpones/{galponId}/historial-estados` | Historial de estados (`page`, `size`) |
| `POST /lotes` | Registrar lote en un galpón disponible |
| `GET /lotes` | Listar lotes (`page`) |
| `POST /alertas-mantenimiento` | Generar alerta de mantenimiento |
| `GET /alertas-mantenimiento` | Listar alertas (`estado`, `galponId`, `page`, `size`) |
| `PATCH /alertas-mantenimiento/{alertaId}` | Atender la alerta; el galpón pasa a MANTENIMIENTO |

El detalle de cada petición y sus respuestas está en los planes de `docs/spec-*`.

## Tópicos Kafka que consume el Módulo 2

El Módulo 1 solo consume. Grupo de consumo `modulo-1`. Cada mensaje es un sobre JSON con `eventType`,
`eventVersion` y `payload`; los demás campos del sobre se ignoran.

| Tópico | `eventType` | Efecto |
|---|---|---|
| `avicontrol.mortalidad` | `MORTALIDAD_REGISTRADA` | Descuenta la mortalidad de la población actual del lote activo |
| `avicontrol.alerta-sanitaria` | `ALERTA_SANITARIA_EMITIDA` | Aísla o reanuda el galpón, o aísla el galpón de un lote |
| `avicontrol.vaciado-sanitario` | `VACIADO_SANITARIO_SOLICITADO` | Pasa el galpón de EN_COSECHA a VACIADO_SANITARIO; un proceso programado lo devuelve a DISPONIBLE al cumplirse los días configurados |

Un mensaje rechazado por una regla de negocio se registra en el log y no se reintenta. Un error técnico (por ejemplo,
base de datos caída) se reintenta con espera fija y, agotados los reintentos, se descarta con un log de error.

## Configuración

`src/main/resources/application.properties`:

| Propiedad | Valor por defecto | Uso |
|---|---|---|
| `avicontrol.vaciado-sanitario.dias` | 3 | Días de vaciado sanitario antes de volver a DISPONIBLE |
| `avicontrol.vaciado-sanitario.cron` | `0 0 * * * *` | Cuándo corre el proceso que revisa los vaciados cumplidos |
| `avicontrol.reintento-intervalo-ms` | 1000 | Espera entre reintentos de un mensaje Kafka |
| `avicontrol.reintentos-maximos` | 3 | Reintentos por mensaje Kafka |
