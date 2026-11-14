package com.unimag.avicontrol.infrastructure.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describe la API del Módulo 1 como contrato consumible por los Módulos 2 y 3 (T090, P-09).
 *
 * <p>Hasta ahora el contrato solo existía como prosa repartida en los planes de {@code docs/spec-*}. Esto publica
 * la misma información en OpenAPI 3.1, que los otros equipos pueden leer en {@code /swagger-ui.html} o consumir
 * desde {@code /v3/api-docs} para generar su cliente.
 *
 * <p>Las rutas del simulador del Módulo 2 ({@code /local/**}) quedan fuera del documento: existen solo con el
 * perfil {@code local} y son una herramienta de demostración, no parte del contrato (T088).
 */
@Configuration
public class OpenApiConfig {

    private static final String DESCRIPCION = """
            Fuente de verdad sobre los galpones y lotes de la granja. El Módulo 3 consulta por esta API; el \
            Módulo 2 se integra por Kafka y sus tópicos no aparecen aquí (están en el README).

            Convenciones que valen para todas las operaciones:

            - Un campo que no aplica se omite de la respuesta. Nunca se envía `null`.
            - La paginación empieza en `page=1`, no en 0. Una página sin resultados es 200 con `contenido: []`.
            - Los enteros rechazan decimales. Un instante va en ISO-8601 con desplazamiento \
            (`2026-10-05T10:30:00-05:00`); las fechas simples, en `yyyy-MM-dd`. Zona de negocio America/Bogota.
            - Los errores siguen RFC 9457 (`application/problem+json`) y agregan un campo `code` estable. \
            Programe contra `code`, no contra `detail`.
            - Toda respuesta GET lleva `Cache-Control: no-store`.

            Autenticación y autorización están fuera del alcance de este módulo (P-01): la API no valida \
            identidad ni rol, así que cualquier cliente puede invocar cualquier operación.
            """;

    @Bean
    public OpenAPI apiDelModulo1() {
        return new OpenAPI()
                .info(new Info()
                        .title("AviControl · Módulo 1: Gestión de Galpones")
                        .version("v1")
                        .description(DESCRIPCION)
                        .license(new License().name("Uso académico · Universidad del Magdalena")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Desarrollo local (docker compose)")))
                .externalDocs(new ExternalDocumentation()
                        .description("Planes por caso de uso, con todos los escenarios de cada petición")
                        .url("https://github.com/AviControl-org/AVICONTROL/tree/develop/avicontrol/docs"));
    }
}
