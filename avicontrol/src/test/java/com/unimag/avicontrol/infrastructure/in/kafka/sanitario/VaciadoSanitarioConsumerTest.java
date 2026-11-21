package com.unimag.avicontrol.infrastructure.in.kafka.sanitario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirVaciadoSanitarioUseCase;
import com.unimag.avicontrol.application.port.in.sanitario.RecibirVaciadoSanitarioUseCase.Comando;
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
class VaciadoSanitarioConsumerTest {

    @Mock
    private RecibirVaciadoSanitarioUseCase useCase;

    private VaciadoSanitarioConsumer consumer;

    @BeforeEach
    void preparar() {
        LectorEventos lector = new LectorEventos(
                JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        consumer = new VaciadoSanitarioConsumer(lector, useCase);
    }

    private static String sobre(String eventType, int version, String payload) {
        return """
                {
                  "eventId": "3b9a1c7e-2d40-4f18-8a65-5c0e7d1b9f23",
                  "eventType": "%s",
                  "eventVersion": %d,
                  "occurredAt": "2026-10-05T16:00:00-05:00",
                  "publishedAt": "2026-10-05T16:00:02-05:00",
                  "producer": "modulo-2",
                  "aggregateType": "Galpon",
                  "aggregateId": "550e8400-e29b-41d4-a716-446655440000",
                  "payload": %s
                }
                """.formatted(eventType, version, payload);
    }

    private static String sobreDelFixture() {
        return sobre(VaciadoSanitarioConsumer.EVENT_TYPE, 1, FixtureJson.texto("vaciado-sanitario-payload.json"));
    }

    @Test
    void elTopicYElTipoDeEventoSonLosDelContrato() {
        assertThat(VaciadoSanitarioConsumer.TOPIC).isEqualTo("avicontrol.vaciado-sanitario");
        assertThat(VaciadoSanitarioConsumer.EVENT_TYPE).isEqualTo("VACIADO_SANITARIO_SOLICITADO");
    }

    @Test
    void deserializaElFixtureDentroDelSobreYDelegaEnElCasoDeUso() {
        consumer.consumir(sobreDelFixture());

        ArgumentCaptor<Comando> captor = ArgumentCaptor.forClass(Comando.class);
        verify(useCase).recibir(captor.capture());
        Comando c = captor.getValue();
        assertThat(c.alertaId()).isEqualTo(UUID.fromString("0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47"));
        assertThat(c.galponId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(c.loteId()).isEqualTo(UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90"));
        assertThat(c.fechaHoraEvento().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-05T16:00:00-05:00").toInstant());
    }

    @Test
    void unRechazoDeNegocioSeRegistraYNoSePropagaParaNoReintentar() {
        doThrow(new ValidacionNegocioException("GALPON_NO_EN_COSECHA", "El galpón no está en estado EN_COSECHA."))
                .when(useCase).recibir(any());

        consumer.consumir(sobreDelFixture());

        verify(useCase).recibir(any());
    }

    @Test
    void unaAlertaInconsistenteTampocoSePropaga() {
        doThrow(new ConflictoException("ALERTA_INCONSISTENTE", "El alertaId ya fue procesado con datos distintos."))
                .when(useCase).recibir(any());

        consumer.consumir(sobreDelFixture());

        verify(useCase).recibir(any());
    }

    @Test
    void unErrorTecnicoSiSePropagaParaQueKafkaReintente() {
        doThrow(new IllegalStateException("base de datos no disponible")).when(useCase).recibir(any());

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> consumer.consumir(sobreDelFixture()));
    }

    @Test
    void unMensajeIlegibleSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir("esto no es json");

        verifyNoInteractions(useCase);
    }

    @Test
    void unaVersionDistintaDeUnoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre(VaciadoSanitarioConsumer.EVENT_TYPE, 2, FixtureJson.texto("vaciado-sanitario-payload.json")));

        verifyNoInteractions(useCase);
    }

    @Test
    void otroTipoDeEventoSeRechazaSinLlamarAlCasoDeUso() {
        consumer.consumir(sobre("MORTALIDAD_REGISTRADA", 1, FixtureJson.texto("vaciado-sanitario-payload.json")));

        verifyNoInteractions(useCase);
    }
}
