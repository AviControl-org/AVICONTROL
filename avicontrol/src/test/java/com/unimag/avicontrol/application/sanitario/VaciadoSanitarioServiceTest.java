package com.unimag.avicontrol.application.sanitario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unimag.avicontrol.application.port.in.sanitario.RecibirVaciadoSanitarioUseCase.Comando;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.sanitario.VaciadoSanitarioService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.AlertaVaciadoSanitarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class VaciadoSanitarioServiceTest {

    /** 2026-10-05 17:00 en America/Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-05T22:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaNegocio.BOGOTA);
    private static final UUID ALERTA = UUID.fromString("0c8e2a11-5d4f-4b7a-a3e9-1f6b2c9d8e47");
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID LOTE_ID = UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90");
    private static final OffsetDateTime EVENTO = OffsetDateTime.parse("2026-10-05T16:00:00-05:00");

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private AlertaVaciadoSanitarioRepository alertas;
    @Mock
    private GalponEstadoService estados;

    private VaciadoSanitarioService servicio;
    private ListAppender<ILoggingEvent> registro;
    private Logger logger;

    @BeforeEach
    void preparar() {
        servicio = new VaciadoSanitarioService(galpones, lotes, alertas, estados, RELOJ, 3);
        logger = (Logger) LoggerFactory.getLogger(VaciadoSanitarioService.class);
        registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
    }

    @AfterEach
    void limpiar() {
        logger.detachAppender(registro);
    }

    private static Comando valido() {
        return new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO);
    }

    private static Lote lote(UUID galponId) {
        return new Lote(LOTE_ID, galponId, "Lote A1", LocalDate.of(2026, 9, 15), 1000, 975, 5_000_000, null, 0L);
    }

    private void escenario(EstadoGalpon estado, Lote lote) {
        when(alertas.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(new Galpon(GALPON_ID, "Galpón Norte", 5000, estado, 0L)));
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote));
    }

    private void escenarioValido() {
        escenario(EstadoGalpon.EN_COSECHA, lote(GALPON_ID));
    }

    private void assertRechazo(Comando comando, Class<? extends NegocioException> tipo, String codigo) {
        assertThatExceptionOfType(tipo).isThrownBy(() -> servicio.recibir(comando))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo(codigo));
        verify(alertas, never()).save(any());
        verify(lotes, never()).save(any());
    }

    private AlertaVaciadoSanitario alertaPrevia(UUID galponId, UUID loteId, Instant evento) {
        return new AlertaVaciadoSanitario(ALERTA, galponId, loteId, evento, AHORA.minusSeconds(60), "ACEPTADA");
    }

    // ------------------------------------------------------------------ recibir

    @Test
    void galponEnCosechaSolicitaVaciadoGuardaLaAlertaYDesvinculaElLote() {
        escenarioValido();

        servicio.recibir(valido());

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.EN_COSECHA, EstadoGalpon.VACIADO_SANITARIO,
                OrigenCambioEstado.ALERTA_VACIADO_SANITARIO);
        verify(alertas).save(new AlertaVaciadoSanitario(ALERTA, GALPON_ID, LOTE_ID, EVENTO.toInstant(), AHORA, "ACEPTADA"));
        ArgumentCaptor<Lote> guardado = ArgumentCaptor.forClass(Lote.class);
        verify(lotes).save(guardado.capture());
        assertThat(guardado.getValue().desvinculadoEn()).isEqualTo(AHORA);
        assertThat(guardado.getValue().activo()).isFalse();
        assertThat(guardado.getValue().poblacionActual()).isEqualTo(975);
        assertThat(guardado.getValue().galponId()).isEqualTo(GALPON_ID);
    }

    @Test
    void elCambioDeEstadoSePideAntesDeGuardarLaAlertaYDesvincularElLote() {
        escenarioValido();

        servicio.recibir(valido());

        InOrder orden = inOrder(estados, alertas, lotes);
        orden.verify(estados).solicitarCambio(any(), any(), any(), any());
        orden.verify(alertas).save(any());
        orden.verify(lotes).save(any());
    }

    @Test
    void siElCambioDeEstadoFallaNoSeGuardaLaAlertaNiSeDesvinculaElLote() {
        escenarioValido();
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(new IllegalStateException("fallo"));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> servicio.recibir(valido()));

        verify(alertas, never()).save(any());
        verify(lotes, never()).save(any());
    }

    @ParameterizedTest(name = "galpón en {0} se rechaza")
    @ValueSource(strings = {"DISPONIBLE", "VACIADO_SANITARIO", "PRODUCTIVO", "MANTENIMIENTO", "AISLAMIENTO"})
    void galponQueNoEstaEnCosechaSeRechaza(EstadoGalpon estado) {
        escenario(estado, lote(GALPON_ID));

        assertRechazo(valido(), ValidacionNegocioException.class, "GALPON_NO_EN_COSECHA");

        verifyNoInteractions(estados);
    }

    @Test
    void siElEstadoCambioEntreLaLecturaYLaSolicitudSeRechazaComoGalponNoEnCosecha() {
        escenarioValido();
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(
                new ConflictoException("ESTADO_ESPERADO_CAMBIADO", "El estado del galpón cambió."));

        assertRechazo(valido(), ValidacionNegocioException.class, "GALPON_NO_EN_COSECHA");
    }

    @Test
    void otroRechazoDeGalponEstadoServiceSePropagaTalCual() {
        escenarioValido();
        ConflictoException falla = new ConflictoException("TRANSICION_NO_PERMITIDA", "No permitida.");
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(falla);

        assertThatExceptionOfType(ConflictoException.class).isThrownBy(() -> servicio.recibir(valido())).isSameAs(falla);
        verify(alertas, never()).save(any());
    }

    @Test
    void faltaUnCampoObligatorioEsAlertaIncompleta() {
        assertRechazo(new Comando(null, GALPON_ID, LOTE_ID, EVENTO), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, null, LOTE_ID, EVENTO), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, GALPON_ID, null, EVENTO), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, null), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        verifyNoInteractions(galpones, estados);
    }

    @Test
    void alertaIdYaProcesadoConLosMismosDatosSeIgnoraSinTocarNada() {
        when(alertas.findById(ALERTA)).thenReturn(Optional.of(alertaPrevia(GALPON_ID, LOTE_ID, EVENTO.toInstant())));

        servicio.recibir(valido());

        verifyNoInteractions(galpones, lotes, estados);
        verify(alertas, never()).save(any());
        assertThat(registro.list).anyMatch(e -> e.getLevel() == Level.INFO
                && e.getFormattedMessage().contains("ignorada alertaId=" + ALERTA + " motivo=YA_PROCESADA"));
    }

    @Test
    void alertaIdYaProcesadoConOtroGalponLoteOFechaEsAlertaInconsistente() {
        when(alertas.findById(ALERTA)).thenReturn(Optional.of(alertaPrevia(GALPON_ID, LOTE_ID, EVENTO.toInstant())));

        assertRechazo(new Comando(ALERTA, UUID.randomUUID(), LOTE_ID, EVENTO), ConflictoException.class, "ALERTA_INCONSISTENTE");
        assertRechazo(new Comando(ALERTA, GALPON_ID, UUID.randomUUID(), EVENTO), ConflictoException.class, "ALERTA_INCONSISTENTE");
        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO.plusMinutes(5)), ConflictoException.class, "ALERTA_INCONSISTENTE");
        verifyNoInteractions(estados);
    }

    @Test
    void galponInexistenteSeRechaza() {
        when(alertas.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertRechazo(valido(), RecursoNoEncontradoException.class, "GALPON_NO_ENCONTRADO");
    }

    @Test
    void loteInexistenteSeRechaza() {
        when(alertas.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(new Galpon(GALPON_ID, "Galpón Norte", 5000, EstadoGalpon.EN_COSECHA, 0L)));
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.empty());

        assertRechazo(valido(), RecursoNoEncontradoException.class, "LOTE_NO_ENCONTRADO");
    }

    @Test
    void loteQueNoPerteneceAlGalponSeRechaza() {
        escenario(EstadoGalpon.EN_COSECHA, lote(UUID.randomUUID()));

        assertRechazo(valido(), ValidacionNegocioException.class, "LOTE_NO_PERTENECE_AL_GALPON");
        verifyNoInteractions(estados);
    }

    @ParameterizedTest(name = "fecha {0} se rechaza")
    @ValueSource(strings = {"2026-10-05T17:00:01-05:00", "2026-10-06T09:00:00-05:00", "2026-10-04T23:59:59-05:00"})
    void fechaFuturaODeOtroDiaSeRechazaSinCambiarElEstado(String fecha) {
        escenarioValido();

        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, OffsetDateTime.parse(fecha)),
                ValidacionNegocioException.class, "FECHA_INVALIDA");
        verifyNoInteractions(estados);
    }

    // ------------------------------------------------ finalizarPeriodosCumplidos

    @Test
    void conPeriodoCumplidoSolicitaDisponibleConOrigenProcesoAutomatico() {
        UUID otro = UUID.randomUUID();
        when(alertas.findGalponIdsConVaciadoCumplido(AHORA.minusSeconds(3L * 24 * 3600))).thenReturn(List.of(GALPON_ID, otro));

        servicio.finalizarPeriodosCumplidos();

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.VACIADO_SANITARIO, EstadoGalpon.DISPONIBLE,
                OrigenCambioEstado.PROCESO_AUTOMATICO);
        verify(estados).solicitarCambio(otro, EstadoGalpon.VACIADO_SANITARIO, EstadoGalpon.DISPONIBLE,
                OrigenCambioEstado.PROCESO_AUTOMATICO);
    }

    @Test
    void sinGalponesConPeriodoCumplidoNoSolicitaNada() {
        when(alertas.findGalponIdsConVaciadoCumplido(any())).thenReturn(List.of());

        servicio.finalizarPeriodosCumplidos();

        verifyNoInteractions(estados);
    }

    @Test
    void sinPeriodoConfiguradoNoSolicitaNingunaTransicionYDejaElAvisoEnElLog() {
        VaciadoSanitarioService sinConfigurar = new VaciadoSanitarioService(galpones, lotes, alertas, estados, RELOJ, null);

        sinConfigurar.finalizarPeriodosCumplidos();

        verifyNoInteractions(estados, alertas);
        assertThat(registro.list).anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(
                "avicontrol.vaciado-sanitario.dias sin configurar: no se ejecuta la transicion a DISPONIBLE"));
    }

    @Test
    void elFalloDeUnGalponNoImpideProcesarLosDemas() {
        UUID falla = UUID.randomUUID();
        UUID bien = UUID.randomUUID();
        when(alertas.findGalponIdsConVaciadoCumplido(any())).thenReturn(List.of(falla, bien));
        when(estados.solicitarCambio(eq(falla), any(), any(), any())).thenThrow(new IllegalStateException("fallo"));

        servicio.finalizarPeriodosCumplidos();

        verify(estados).solicitarCambio(bien, EstadoGalpon.VACIADO_SANITARIO, EstadoGalpon.DISPONIBLE,
                OrigenCambioEstado.PROCESO_AUTOMATICO);
        assertThat(registro.list).anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains(falla.toString()));
    }
}
