# Plan de despliegue en AWS - AVICONTROL

**Fecha**: 2026-11-18
**Autor**: Módulo 1 - Gestión de Galpones e Infraestructura
**Estado**: propuesta del Módulo 1. Los acuerdos de la sección 6 están pendientes de confirmar con los Módulos 2 y 3.
**Complementa a**: [acuerdo-contratos-entre-modulos.md](acuerdo-contratos-entre-modulos.md) (eventos y consulta) y [arquitectura.md](../arquitectura/arquitectura.md) (convenciones técnicas).
**Spec y plan de implementación**: [despliegue-spec.md](../spec-despliegue/despliegue-spec.md) y [plan-despliegue.md](../spec-despliegue/plan-despliegue.md)

## 1. Propósito y alcance

Poner el Módulo 1 en línea para que funcione con los otros módulos: el Módulo 2 publica sus eventos en el Kafka del Módulo 1 y los Módulos 2 y 3 consultan la API REST. El Módulo 1 aloja el broker de Kafka.

Este documento define dónde corre cada pieza, qué puertos se abren, cómo se configura y qué falta acordar con los otros grupos. No cambia el comportamiento de la aplicación ni los contratos ya definidos. Los archivos de despliegue (Dockerfile, compose de producción y configuración del servidor web) no existen todavía y se crean después, uno por uno, con aprobación previa.

## 2. Decisión de plataforma

| Tema | Decisión |
|---|---|
| Proveedor | AWS, cuenta con plan gratuito y créditos |
| Región | us-east-2 (Ohio) |
| Cómputo | Una sola instancia EC2 de un tipo admitido por el plan gratuito (`m7i-flex.large`, que se verifica en la consola antes de crearla) |
| Sistema | Linux con Docker y Docker Compose |
| Dirección | Una IP elástica asociada a la instancia, para que no cambie si se reinicia |
| Uso previsto | Presentación y pruebas con los otros módulos. No se diseña para producción real ni para alta disponibilidad |

Se eligió una sola máquina porque el sistema completo (aplicación, base de datos, broker y servidor web) cabe en ella y solo hay una dirección que dar a los otros grupos.

Si más adelante no se logra exponer Kafka de forma estable en AWS, la alternativa evaluada es Railway, que tiene plantilla de Kafka con acceso externo. No se usa salvo que haga falta.

## 3. Arquitectura en la máquina

Todo corre como contenedores con Docker Compose, en una red interna. Solo se publican hacia afuera los puertos de la tabla de la sección 4.

| Contenedor | Imagen o contenido | Puerto interno | Se publica |
|---|---|---|---|
| nginx | Sirve el frontend compilado (`dist`) y reenvía `/api` al backend | 80 | Sí |
| backend | Aplicación Spring Boot del Módulo 1 | 8080 | Solo para los Módulos 2 y 3 |
| postgres | `postgres:16` con volumen | 5432 | No |
| kafka | `apache/kafka:4.0.0` | 29092 (interno) y 9092 (externo) | Solo el 9092, para el Módulo 2 |

Puntos de diseño:

- El frontend y la API salen por el mismo origen (nginx reenvía `/api`), igual que hace el proxy de Vite en desarrollo. Por eso el backend no necesita CORS para el frontend propio.
- El backend se conecta a la base y a Kafka por los nombres de los contenedores, no por `localhost`.
- La base de datos no se expone. Solo el backend la usa.
- El esquema lo valida Hibernate (`ddl-auto=validate`), así que las migraciones deben aplicarse al arrancar, como ya ocurre en local.

## 4. Red y seguridad

La aplicación no tiene autenticación (P-01). Por eso el control de acceso se hace en el grupo de seguridad de la instancia, no en el código.

| Puerto | Uso | Quién puede entrar |
|---|---|---|
| 22 | Administración por SSH | Solo la IP del Módulo 1 |
| 80 | Frontend del Módulo 1 y API detrás de nginx | Cualquier IP (D-02) |
| 9092 | Kafka | Solo la IP o IPs desde las que publica el Módulo 2 |
| 8080 | API REST directa, si los Módulos 2 y 3 no pasan por nginx | Solo las IPs de los Módulos 2 y 3 |
| 5432 | PostgreSQL | Nadie desde afuera |

Reglas:

- Solo el puerto 80 queda abierto a todo internet (D-02). Los demás se limitan a las IPs de la tabla.
- No se guardan claves de acceso de AWS en el repositorio ni se comparten. El ingreso a la consola lo hace cada persona con su sesión.
- La contraseña de la base en el servidor no es la de desarrollo. Se define en un archivo `.env` que no se versiona.

## 5. Configuración de la aplicación

La aplicación ya lee su configuración de `application.properties`. En el servidor se sobrescribe con variables de entorno, sin tocar el código.

| Variable | Valor en el servidor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/avicontrol` |
| `SPRING_DATASOURCE_USERNAME` | usuario de la base en el servidor |
| `SPRING_DATASOURCE_PASSWORD` | contraseña de la base en el servidor |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` (listener interno del broker) |
| `SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `earliest` (decisión D-01) |
| `AVICONTROL_VACIADO_SANITARIO_DIAS` | `3`, el valor del plan |
| `AVICONTROL_VACIADO_SANITARIO_CRON` | el valor por defecto, salvo que se acuerde otro para la demostración |

Kafka:

- El broker anuncia dos direcciones: una interna para el backend (`kafka:29092`, que no sale de la instancia) y una externa, con la IP elástica en el puerto 9092, para el Módulo 2. Sin esa segunda dirección el productor del otro grupo se conecta al broker pero no logra enviar mensajes.
- Los tópicos son `avicontrol.mortalidad`, `avicontrol.alerta-sanitaria` y `avicontrol.vaciado-sanitario`, con el grupo de consumo `modulo-1`. Se crean al arrancar o a mano antes de la primera prueba.
- El código no fija `auto-offset-reset`, así que Spring usa `latest`: si el backend arranca después de que el Módulo 2 publicó el primer mensaje, ese mensaje no se lee. En el servidor se fija `earliest` con la variable de la tabla anterior (D-01), sin cambiar el código. Un mensaje leído dos veces no duplica efectos porque la idempotencia va por `alertaId`.

Datos y perfiles:

- En el servidor no se usa el perfil `local`, así que no se cargan los datos de ejemplo ni el simulador de eventos.
- Los galpones y lotes del servidor se crean desde el frontend o por la API, como en el uso normal.

## 6. Acuerdos con los Módulos 2 y 3

Son los puntos que deben quedar confirmados por escrito antes de dar por cerrado el despliegue.

Con el Módulo 2:

| Acuerdo | Qué se necesita |
|---|---|
| Kafka alojado por el Módulo 1 | Confirmar que el Módulo 2 publica en el broker del Módulo 1 |
| Dirección del broker | El Módulo 1 entrega `IP_ELASTICA:9092` cuando la máquina esté lista |
| IP de origen | El Módulo 2 informa desde qué IP o IPs publicará, para abrirlas en el puerto 9092 |
| Tópicos y sobre | Los tres tópicos y el sobre de eventos del acuerdo de contratos, sin cambios |
| Topología (P-10) | Procesos separados: el Módulo 2 llama por REST al Módulo 1 y no comparte su proceso |
| Consultas REST | El Módulo 2 usa la URL base que entregue el Módulo 1, bajo `/api/v1` |

Con el Módulo 3:

| Acuerdo | Qué se necesita |
|---|---|
| Forma de consulta | Confirmar que consulta por REST (`GET`) y no por Kafka |
| Origen de las llamadas | Si llama desde su servidor, no hace falta CORS. Si llama desde un navegador, el Módulo 1 debe agregar CORS, y eso requiere decisión previa porque no está en los planes |
| IP de origen | El Módulo 3 informa desde qué IP llamará, para abrirla en el puerto 8080 |
| URL base | Se entrega junto con la del Módulo 2 |

Para los dos:

- Los contratos ya enviados siguen vigentes. Si algún grupo necesita un cambio, se acuerda y se anota en los documentos de contratos antes de tocar el código.
- El envío formal de los contratos (T090) sigue pendiente y se cierra junto con estos acuerdos.

## 7. Pendientes de decisión del Módulo 1

| # | Tema | Decisión o opciones | Estado |
|---|---|---|---|
| D-01 | `auto-offset-reset` en el servidor | `earliest`, para no perder el primer mensaje | Decidido |
| D-02 | Acceso al frontend desde internet | Abierto a todos, el puerto 80 sin restricción de IP | Decidido |
| D-03 | Entrada de los Módulos 2 y 3 a la API | Por nginx en el puerto 80 o directo al backend en el 8080 | Pendiente de los tres grupos |
| D-04 | CORS | Solo si el Módulo 3 llama desde un navegador | Pendiente del Módulo 3 |
| D-05 | Autenticación (P-01) | Sin autenticación y control por grupo de seguridad, como queda en este plan | Decidido |

## 8. Archivos de despliegue por definir

Ninguno de estos archivos existe todavía. Su contenido propuesto y sus tareas (TD01 a TD05) están en [plan-despliegue.md](../spec-despliegue/plan-despliegue.md). Se crean después de aprobar la spec y el plan.

| Archivo | Para qué |
|---|---|
| Dockerfile del backend | Empaquetar la aplicación en una imagen |
| Compose de producción | Levantar nginx, backend, postgres y kafka juntos, con el listener externo de Kafka |
| Configuración de nginx | Servir el frontend compilado y reenviar `/api` al backend |
| Archivo `.env` de ejemplo | Mostrar las variables de la sección 5 sin valores reales |

## 9. Verificación

El despliegue se considera listo cuando se cumple todo lo siguiente:

- [ ] El frontend abre desde la IP elástica y lista los galpones.
- [ ] `GET /api/v1/galpones` responde desde afuera de la máquina.
- [ ] Un evento enviado por el Módulo 2 al puerto 9092 llega al backend y cambia lo que debe cambiar (mortalidad, alerta sanitaria o vaciado sanitario).
- [ ] El Módulo 3 consulta galpones y lotes por REST con la URL entregada.
- [ ] El puerto 5432 no responde desde afuera.
- [ ] Un reinicio de la máquina deja todo funcionando con la misma dirección.
