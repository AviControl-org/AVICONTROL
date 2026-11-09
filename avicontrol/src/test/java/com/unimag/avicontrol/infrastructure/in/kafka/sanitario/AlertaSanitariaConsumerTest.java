package com.unimag.avicontrol.infrastructure.in.kafka.sanitario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirAlertaSanitariaUseCase;
import com.unimag.avicontrol.application.port.in.sanitario.RecibirAlertaSanitariaUseCase.Comando;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.infrastructure.in.kafka.compartido.LectorEventos;
import com.unimag.avicontrol.soporte.FixtureJson;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** El consumer solo deserializa y delega: ninguna regla de negocio vive aquí. */
@ExtendWith(MockitoExtension.class)
class AlertaSanitariaConsumerTest {

    @Mock
    private RecibirAlertaSanitariaUseCase useCase;

    private AlertaSanitariaConsumer consumer;

    @BeforeEach
    void preparar() {
        LectorEventos lector = new LectorEventos(
                JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        consumer = new AlertaSanitariaConsumer(lector, useCase);
    }

    /** Sobre estándar del Módulo 2 alrededor del payload de un fixture. */
    private static String sobre(String eventType, int version, String payload) {
        return """
                {
                  "eventId": "1a4d7e90-3c2b-4f68-8d05-9b7e6c2a1f34",
                  "eventType": "%s",
                  "eventVersion": %d,
                  "occurredAt": "2026-10-05T14:30:00-05:00",
                  "publishedAt": "2026-10-05T14:30:02-05:00",
                  "producer": "modulo-2",
                  "aggregateType": "Galpon",
                  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
                  "payload": %s
                }
                """.formatted(eventType, version, payload);
    }

    private static String sobreDe(String fixture) {
        return sobre(AlertaSanitariaConsumer.EVENT_TYPE, 1, FixtureJson.texto(fixture));
    }

    private Comando comandoEntregado() {
        ArgumentCaptor<Comando> captor = ArgumentCaptor.forClass(Comando.class);
        verify(useCase).recibir(captor.capture());
        return captor.getValue();
    }

    @Test
    void elTopicYElTipoDeEventoSonLosDelContrato() {
        assertThat(AlertaSanitariaConsumer.TOPIC).isEqualTo("avicontrol.alerta-sanitaria");
        assertThat(AlertaSanitariaConsumer.EVENT_TYPE).isEqualTo("ALERTA_SANITARIA_EMITIDA");
    }

    @Test
    void deserializaElFixtureDeAislamientoYDelegaEnElCasoDeUso() {
        consumer.consumir(sobreDe("alerta-sanitaria-aislamiento-payload.json"));

        Comando c = comandoEntregado();
        assertThat(c.alertaId()).isEqualTo(UUID.fromString("5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa"));
        assertThat(c.accion()).isEqualTo("AISLAMIENTO");
        assertThat(c.galponId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(c.loteId()).isNull();
        assertThat(c.fechaHora().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-05T09:15:00-05:00").toInstant());
        assertThat(c.tipoEnfermedad()).isEqualTo("Sospecha de Newcastle");
        assertThat(c.descripcion()).isEqualTo("Mortalidad atípica y signos respiratorios");
        assertThat(c.gravedad()).isNull();
    }

    @Test
    void deserializaElFixtureDeReanudacion() {
        consumer.consumir(sobreDe("alerta-sanitaria-reanudacion-payload.json"));

        Comando c = comandoEntregado();
        assertThat(c.accion()).isEqualTo("REANUDACION");
        assertThat(c.galponId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(c.tipoEnfermedad()).isNull();
        assertThat(c.descripcion()).isNull();
    }

    @Test
    void deserializaElFixtureIdentificadoSoloPorLote() {
        consumer.consumir(sobreDe("alerta-sanitaria-por-lote-payload.json"));

        Comando c = comandoEntregado();
        assertThat(c.galponId()).isNull();
        assertThat(c.loteId()).isEqualTo(UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90"));
    }

    @Test
    void ignoraLosCamposDesconocidosDelSobreYDelPayload() {
        String payload = FixtureJson.texto("alerta-sanitaria-aislamiento-payload.json")
                .replace("{", "{ \"campoNuevo\": \"x\",");

        consumer.consumir(sobre(AlertaSanitariaConsumer.EVENT_TYPE, 1, payload));

        assertThat(comandoEntregado().accion()).isEqualTo("AISLAMIENTO");
    }

    @Test
    void unRechazoDeNegocioSeRegistraYNoSePropagaParaNoReintentar() {
        doThrow(new ConflictoException("ACCION_INCOMPATIBLE", "La acción no corresponde al estado actual del galpón."))
                .when(useCase).recibir(any());

        consumer.consumir(sobreDe("alerta-sanitaria-aislamiento-payload.json"));

        verify(useCase).recibir(any());
    }

    @Test
    void otroRechazoDeValidacionTampocoSePropaga() {
        doThrow(new ValidacionNegocioException("FECHA_INVALIDA", "La fecha de la alerta no corresponde al día actual."))
                .when(useCase).recibir(any());

        consumer.consumir(sobreDe("alerta-sanitaria-aislamiento-payload.json"));

        verify(useCase).recibir(any());
    }

    @Test
    void unErrorTecnicoSiSePropagaParaQueKafkaReintente() {
        doThrow(new IllegalStateException("base de datos no disponible")).when(useCase).recibir(any());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> consumer.consumir(sobreDe("alerta-sanitaria-aislamiento-payload.json")));
    }

    @Test
    void unMensajeIlegibleSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir("esto no es json");

        verifyNoInteractions(useCase);
    }

    @Test
    void unaVersionDistintaDeUnoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre(AlertaSanitariaConsumer.EVENT_TYPE, 2,
                FixtureJson.texto("alerta-sanitaria-aislamiento-payload.json")));

        verifyNoInteractions(useCase);
    }

    @Test
    void otroTipoDeEventoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre("MORTALIDAD_REGISTRADA", 1,
                FixtureJson.texto("alerta-sanitaria-aislamiento-payload.json")));

        verifyNoInteractions(useCase);
    }
}
