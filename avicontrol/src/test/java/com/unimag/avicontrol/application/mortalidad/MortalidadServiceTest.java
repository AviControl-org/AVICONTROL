package com.unimag.avicontrol.application.mortalidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionUseCase;
import com.unimag.avicontrol.application.port.in.mortalidad.RecibirMortalidadUseCase.Comando;
import com.unimag.avicontrol.application.usecase.mortalidad.MortalidadService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.RegistroProcesamientoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MortalidadServiceTest {

    /** 2026-10-05 15:00 en America/Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T20:00:00Z"), ZonaNegocio.BOGOTA);
    private static final UUID ALERTA = UUID.fromString("9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11");
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID LOTE_ID = UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90");
    private static final OffsetDateTime EVENTO = OffsetDateTime.parse("2026-10-05T14:30:00-05:00");

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private RegistroProcesamientoRepository registros;
    @Mock
    private ActualizarPoblacionUseCase poblacion;

    private MortalidadService servicio;

    @BeforeEach
    void preparar() {
        servicio = new MortalidadService(galpones, lotes, registros, poblacion, RELOJ);
    }

    private static Comando valido() {
        return new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO, 25L);
    }

    private static Lote lote(UUID galponId, Instant desvinculadoEn) {
        return new Lote(LOTE_ID, galponId, "Lote A1", LocalDate.of(2026, 9, 15), 1000, 1000, 5_000_000,
                desvinculadoEn, 0L);
    }

    private void escenario(EstadoGalpon estado, Lote lote) {
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(new Galpon(GALPON_ID, "Galpón Norte", 5000, estado, 0L)));
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote));
    }

    private void escenarioValido() {
        escenario(EstadoGalpon.PRODUCTIVO, lote(GALPON_ID, null));
    }

    private void assertRechazo(Comando comando, Class<? extends NegocioException> tipo, String codigo) {
        assertThatExceptionOfType(tipo).isThrownBy(() -> servicio.recibir(comando))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo(codigo));
        verifyNoInteractions(poblacion);
    }

    @Test
    void mensajeValidoRemiteElDescuentoAlCasoDeUsoDePoblacion() {
        escenarioValido();

        servicio.recibir(valido());

        verify(poblacion).actualizar(new ActualizarPoblacionCommand(ALERTA, LOTE_ID, GALPON_ID, 25, EVENTO.toInstant()));
    }

    @Test
    void lasHorasDelDiaYaTranscurridasSeAceptan() {
        escenarioValido();

        servicio.recibir(new Comando(ALERTA, GALPON_ID, LOTE_ID, OffsetDateTime.parse("2026-10-05T00:00:00-05:00"), 25L));

        verify(poblacion).actualizar(any());
    }

    @Test
    void faltaUnCampoObligatorioEsAlertaIncompleta() {
        assertRechazo(new Comando(null, GALPON_ID, LOTE_ID, EVENTO, 25L), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, null, LOTE_ID, EVENTO, 25L), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, GALPON_ID, null, EVENTO, 25L), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, null, 25L), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO, null), ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        verifyNoInteractions(registros, galpones, lotes);
    }

    @Test
    void alertaIdYaProcesadoConLosMismosDatosSeIgnoraSinRemitirOtraVez() {
        when(registros.findById(ALERTA)).thenReturn(Optional.of(
                new RegistroProcesamiento(ALERTA, GALPON_ID, LOTE_ID, 25, EVENTO.toInstant(), Instant.parse("2026-10-05T19:31:00Z"))));

        servicio.recibir(valido());

        verifyNoInteractions(poblacion, galpones, lotes);
    }

    @ParameterizedTest(name = "cantidad distinta {0}")
    @ValueSource(longs = {26, 1})
    void alertaIdYaProcesadoConOtraCantidadEsAlertaInconsistente(long cantidad) {
        when(registros.findById(ALERTA)).thenReturn(Optional.of(
                new RegistroProcesamiento(ALERTA, GALPON_ID, LOTE_ID, 25, EVENTO.toInstant(), Instant.parse("2026-10-05T19:31:00Z"))));

        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO, cantidad), ConflictoException.class, "ALERTA_INCONSISTENTE");
    }

    @Test
    void alertaIdYaProcesadoConOtroLoteEsAlertaInconsistente() {
        when(registros.findById(ALERTA)).thenReturn(Optional.of(
                new RegistroProcesamiento(ALERTA, GALPON_ID, UUID.randomUUID(), 25, EVENTO.toInstant(), Instant.parse("2026-10-05T19:31:00Z"))));

        assertRechazo(valido(), ConflictoException.class, "ALERTA_INCONSISTENTE");
    }

    @Test
    void galponInexistenteSeRechaza() {
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertRechazo(valido(), RecursoNoEncontradoException.class, "GALPON_NO_ENCONTRADO");
    }

    @Test
    void loteInexistenteSeRechaza() {
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(new Galpon(GALPON_ID, "Galpón Norte", 5000, EstadoGalpon.PRODUCTIVO, 0L)));
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.empty());

        assertRechazo(valido(), RecursoNoEncontradoException.class, "LOTE_NO_ENCONTRADO");
    }

    @Test
    void loteQueNoPerteneceAlGalponSeRechaza() {
        escenario(EstadoGalpon.PRODUCTIVO, lote(UUID.randomUUID(), null));

        assertRechazo(valido(), ValidacionNegocioException.class, "LOTE_NO_PERTENECE_AL_GALPON");
    }

    @Test
    void loteQueNoEsElActivoSeRechaza() {
        escenario(EstadoGalpon.PRODUCTIVO, lote(GALPON_ID, Instant.parse("2026-09-20T13:15:00Z")));

        assertRechazo(valido(), ValidacionNegocioException.class, "LOTE_NO_ACTIVO");
    }

    @ParameterizedTest(name = "galpón en {0} se rechaza")
    @ValueSource(strings = {"DISPONIBLE", "EN_COSECHA", "AISLAMIENTO", "MANTENIMIENTO", "VACIADO_SANITARIO"})
    void galponQueNoEstaProductivoSeRechaza(EstadoGalpon estado) {
        escenario(estado, lote(GALPON_ID, null));

        assertRechazo(valido(), ValidacionNegocioException.class, "GALPON_NO_PRODUCTIVO");
    }

    @ParameterizedTest(name = "cantidad {0} es inválida")
    @ValueSource(longs = {0, -1})
    void cantidadCeroONegativaSeRechaza(long cantidad) {
        escenarioValido();

        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO, cantidad), ValidacionNegocioException.class, "CANTIDAD_INVALIDA");
    }

    @ParameterizedTest(name = "fecha {0} se rechaza")
    @ValueSource(strings = {"2026-10-05T15:00:01-05:00", "2026-10-06T09:00:00-05:00", "2026-10-04T23:59:59-05:00"})
    void fechaFuturaODeOtroDiaSeRechaza(String fecha) {
        escenarioValido();

        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, OffsetDateTime.parse(fecha), 25L),
                ValidacionNegocioException.class, "FECHA_INVALIDA");
    }

    @Test
    void elGalponNoProductivoSeValidaAntesQueLaCantidad() {
        escenario(EstadoGalpon.EN_COSECHA, lote(GALPON_ID, null));

        assertRechazo(new Comando(ALERTA, GALPON_ID, LOTE_ID, EVENTO, 0L), ValidacionNegocioException.class, "GALPON_NO_PRODUCTIVO");
    }

    @Test
    void siPoblacionRechazaPorCantidadExcesivaElRechazoSePropagaYNoSeGuardaRegistro() {
        escenarioValido();
        ValidacionNegocioException falla = new ValidacionNegocioException("CANTIDAD_EXCEDE_POBLACION",
                "La cantidad de muertos supera la población actual del lote.");
        when(poblacion.actualizar(any())).thenThrow(falla);

        assertThatExceptionOfType(ValidacionNegocioException.class).isThrownBy(() -> servicio.recibir(valido())).isSameAs(falla);

        verify(registros, never()).save(any());
    }
}
