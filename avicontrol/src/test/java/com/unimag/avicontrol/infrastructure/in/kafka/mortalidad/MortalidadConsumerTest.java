package com.unimag.avicontrol.infrastructure.in.kafka.mortalidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unimag.avicontrol.application.port.in.mortalidad.RecibirMortalidadUseCase;
import com.unimag.avicontrol.application.port.in.mortalidad.RecibirMortalidadUseCase.Comando;
import com.unimag.avicontrol.domain.exception.ConflictoException;
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
class MortalidadConsumerTest {

    @Mock
    private RecibirMortalidadUseCase useCase;

    private MortalidadConsumer consumer;

    @BeforeEach
    void preparar() {
        LectorEventos lector = new LectorEventos(
                JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        consumer = new MortalidadConsumer(lector, useCase);
    }

    private static String sobre(String eventType, int version, String payload) {
        return """
                {
                  "eventId": "6f2d9c1a-0b7e-4e4a-9d51-3c8a7b1e2f90",
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

    private Comando comandoEntregado() {
        ArgumentCaptor<Comando> captor = ArgumentCaptor.forClass(Comando.class);
        verify(useCase).recibir(captor.capture());
        return captor.getValue();
    }

    @Test
    void elTopicYElTipoDeEventoSonLosDelContrato() {
        assertThat(MortalidadConsumer.TOPIC).isEqualTo("avicontrol.mortalidad");
        assertThat(MortalidadConsumer.EVENT_TYPE).isEqualTo("MORTALIDAD_REGISTRADA");
    }

    @Test
    void deserializaElFixtureConSobreYDelegaEnElCasoDeUso() {
        consumer.consumir(FixtureJson.texto("mortalidad-evento.json"));

        Comando c = comandoEntregado();
        assertThat(c.alertaId()).isEqualTo(UUID.fromString("9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11"));
        assertThat(c.galponId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(c.loteId()).isEqualTo(UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90"));
        assertThat(c.fechaHoraEvento().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-05T14:30:00-05:00").toInstant());
        assertThat(c.cantidadMuertos()).isEqualTo(25L);
    }

    @Test
    void elPayloadSueltoEnvueltoEnElSobreDaElMismoComando() {
        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 1, FixtureJson.texto("mortalidad-payload.json")));

        assertThat(comandoEntregado().cantidadMuertos()).isEqualTo(25L);
    }

    @Test
    void ignoraLosCamposDesconocidosDelPayload() {
        String payload = FixtureJson.texto("mortalidad-payload.json").replace("{", "{ \"campoNuevo\": \"x\",");

        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 1, payload));

        assertThat(comandoEntregado().alertaId()).isNotNull();
    }

    @Test
    void cantidadDecimalSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 1,
                FixtureJson.texto("mortalidad-payload-cantidad-decimal.json")));

        verifyNoInteractions(useCase);
    }

    @Test
    void cantidadDecimalConParteEnteraTambienSeRechaza() {
        String payload = FixtureJson.texto("mortalidad-payload.json").replace("\"cantidadMuertos\": 25", "\"cantidadMuertos\": 25.0");

        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 1, payload));

        verifyNoInteractions(useCase);
    }

    @Test
    void cantidadAusenteSeEntregaNulaParaQueElCasoDeUsoLaRechace() {
        String payload = FixtureJson.texto("mortalidad-payload.json").replaceAll(",\\s*\"cantidadMuertos\":\\s*25", "");

        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 1, payload));

        assertThat(comandoEntregado().cantidadMuertos()).isNull();
    }

    @Test
    void unRechazoDeNegocioSeRegistraYNoSePropagaParaNoReintentar() {
        doThrow(new ConflictoException("ALERTA_INCONSISTENTE", "El alertaId ya fue procesado con datos distintos."))
                .when(useCase).recibir(any());

        consumer.consumir(FixtureJson.texto("mortalidad-evento.json"));

        verify(useCase).recibir(any());
    }

    @Test
    void unErrorTecnicoSiSePropagaParaQueKafkaReintente() {
        doThrow(new IllegalStateException("base de datos no disponible")).when(useCase).recibir(any());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> consumer.consumir(FixtureJson.texto("mortalidad-evento.json")));
    }

    @Test
    void unMensajeIlegibleSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir("esto no es json");

        verifyNoInteractions(useCase);
    }

    @Test
    void unaVersionDistintaDeUnoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre(MortalidadConsumer.EVENT_TYPE, 2, FixtureJson.texto("mortalidad-payload.json")));

        verifyNoInteractions(useCase);
    }

    @Test
    void otroTipoDeEventoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre("ALERTA_SANITARIA_EMITIDA", 1, FixtureJson.texto("mortalidad-payload.json")));

        verifyNoInteractions(useCase);
    }
}
