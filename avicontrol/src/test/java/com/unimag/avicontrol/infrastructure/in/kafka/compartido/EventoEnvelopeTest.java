package com.unimag.avicontrol.infrastructure.in.kafka.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido.EventoEnvelope;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class EventoEnvelopeTest {

    private static final String TIPO = "prueba.evento";
    private static final TypeReference<EventoEnvelope<PayloadPrueba>> REFERENCIA = new TypeReference<>() {
    };

    /** Payload mínimo de prueba: los reales (mortalidad, alerta sanitaria, vaciado) llegan con cada historia. */
    record PayloadPrueba(String dato, Integer cantidad) {
    }

    private final LectorEventos lector = new LectorEventos(
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());

    private static String sobre(String eventType, String eventVersion, String extra) {
        return """
                {
                  "eventId": "8d1b1c6e-3f56-4d0e-9a4c-2f6f0c7a1b11",
                  "eventType": "%s",
                  "eventVersion": %s,
                  "occurredAt": "2026-11-05T10:00:00-05:00",
                  "publishedAt": "2026-11-05T10:00:01-05:00",
                  "producer": "modulo-2",
                  "correlationId": "c-1",
                  "causationId": "c-0",
                  "aggregateType": "Galpon",
                  "aggregateId": "g-1",
                  %s
                  "payload": {"dato": "abc", "cantidad": 25 %s}
                }
                """.formatted(eventType, eventVersion, extra.isEmpty() ? "" : "\"campoExtra\": \"x\",",
                extra.isEmpty() ? "" : ", \"otroExtra\": true");
    }

    @Test
    void leeElSobreYElPayload() {
        EventoEnvelope<PayloadPrueba> evento = lector.leer(sobre(TIPO, "1", ""), REFERENCIA, TIPO);

        assertThat(evento.eventId()).isEqualTo(UUID.fromString("8d1b1c6e-3f56-4d0e-9a4c-2f6f0c7a1b11"));
        assertThat(evento.eventType()).isEqualTo(TIPO);
        assertThat(evento.eventVersion()).isEqualTo(1);
        assertThat(evento.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-11-05T10:00:00-05:00"));
        assertThat(evento.producer()).isEqualTo("modulo-2");
        assertThat(evento.payload()).isEqualTo(new PayloadPrueba("abc", 25));
    }

    @Test
    void ignoraLosCamposDesconocidosEnElSobreYEnElPayload() {
        EventoEnvelope<PayloadPrueba> evento = lector.leer(sobre(TIPO, "1", "x"), REFERENCIA, TIPO);

        assertThat(evento.payload()).isEqualTo(new PayloadPrueba("abc", 25));
    }

    @Test
    void rechazaUnaVersionDistintaDeUno() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lector.leer(sobre(TIPO, "2", ""), REFERENCIA, TIPO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("VERSION_NO_SOPORTADA"));
    }

    @Test
    void rechazaUnaVersionAusente() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lector.leer(sobre(TIPO, "null", ""), REFERENCIA, TIPO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("VERSION_NO_SOPORTADA"));
    }

    @Test
    void rechazaUnTipoDeEventoQueNoCorrespondeAlTopico() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lector.leer(sobre("otro.evento", "1", ""), REFERENCIA, TIPO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("TIPO_EVENTO_NO_SOPORTADO"));
    }

    @Test
    void rechazaUnMensajeQueNoEsJson() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lector.leer("esto no es json", REFERENCIA, TIPO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("MENSAJE_ILEGIBLE"));
    }

    @Test
    void rechazaUnSobreSinPayload() {
        String sinPayload = "{\"eventType\": \"" + TIPO + "\", \"eventVersion\": 1}";

        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lector.leer(sinPayload, REFERENCIA, TIPO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("ALERTA_INCOMPLETA"));
    }
}
