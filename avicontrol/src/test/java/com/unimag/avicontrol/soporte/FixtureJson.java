package com.unimag.avicontrol.soporte;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.opentest4j.AssertionFailedError;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Carga los JSON de contrato de `src/test/resources/contratos/` (los mismos que se comparten con los Módulos 2 y 3)
 * y los compara con la respuesta de MockMvc o los deserializa en las pruebas de los consumers.
 *
 * <p>La comparación es estructural: no importa el orden de las claves ni el formato, sí el orden de los arreglos.
 */
public final class FixtureJson {

    private static final String CARPETA = "/contratos/";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private FixtureJson() {
    }

    /** Contenido del fixture como texto. */
    public static String texto(String nombre) {
        try (InputStream in = FixtureJson.class.getResourceAsStream(CARPETA + nombre)) {
            if (in == null) {
                throw new IllegalArgumentException("No existe el fixture src/test/resources" + CARPETA + nombre);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture " + nombre, e);
        }
    }

    /** Deserializa el fixture (por ejemplo, el mensaje que recibe un consumer). */
    public static <T> T leer(String nombre, Class<T> tipo) {
        return MAPPER.readValue(texto(nombre), tipo);
    }

    public static <T> T leer(String nombre, TypeReference<T> tipo) {
        return MAPPER.readValue(texto(nombre), tipo);
    }

    /** Falla si el JSON obtenido no es estructuralmente igual al fixture. */
    public static void assertCoincide(String nombre, String jsonObtenido) {
        JsonNode esperado = MAPPER.readTree(texto(nombre));
        JsonNode obtenido = MAPPER.readTree(jsonObtenido);
        if (!esperado.equals(obtenido)) {
            throw new AssertionFailedError("El JSON no coincide con el fixture " + nombre,
                    esperado.toPrettyString(), obtenido.toPrettyString());
        }
    }

    /** Compara el cuerpo de la respuesta de MockMvc con el fixture. */
    public static void assertCoincide(String nombre, MvcResult resultado) throws java.io.UnsupportedEncodingException {
        assertCoincide(nombre, resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
