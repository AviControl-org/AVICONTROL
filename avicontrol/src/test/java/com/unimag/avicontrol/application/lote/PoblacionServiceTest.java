package com.unimag.avicontrol.application.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionCommand;
import com.unimag.avicontrol.application.port.in.lote.ActualizarPoblacionResultado;
import com.unimag.avicontrol.application.usecase.lote.PoblacionService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.RegistroProcesamientoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PoblacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZonaNegocio.BOGOTA);
    private static final UUID ALERTA = UUID.fromString("9d3c0b2e-6a58-4c42-8b0c-2a1f3f0f7a11");
    private static final UUID GALPON = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID LOTE_ID = UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90");
    private static final Instant EVENTO = Instant.parse("2026-10-05T19:30:00Z");

    @Mock
    private LoteRepository lotes;
    @Mock
    private RegistroProcesamientoRepository registros;

    private PoblacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PoblacionService(lotes, registros, RELOJ);
    }

    private static Lote lote(long inicial, long actual, Instant desvinculadoEn) {
        return new Lote(LOTE_ID, GALPON, "Lote A1", LocalDate.of(2026, 9, 15), inicial, actual, 5_000_000,
                desvinculadoEn, 0L);
    }

    private static ActualizarPoblacionCommand comando(long cantidad) {
        return new ActualizarPoblacionCommand(ALERTA, LOTE_ID, GALPON, cantidad, EVENTO);
    }

    private void loteExiste(Lote lote) {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote));
    }

    private void guardarDevuelveLoRecibido() {
        when(lotes.save(any(Lote.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void assertRechazo(ActualizarPoblacionCommand comando, Class<? extends NegocioException> tipo, String codigo) {
        assertThatExceptionOfType(tipo).isThrownBy(() -> servicio.actualizar(comando))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    @Test
    void descuentaDeLaPoblacionActualYDejaIntactaLaInicial() {
        loteExiste(lote(1000, 1000, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        guardarDevuelveLoRecibido();

        ActualizarPoblacionResultado r = servicio.actualizar(comando(25));

        assertThat(r).isEqualTo(new ActualizarPoblacionResultado(LOTE_ID, 1000, 975, false));
        ArgumentCaptor<Lote> guardado = ArgumentCaptor.forClass(Lote.class);
        verify(lotes).save(guardado.capture());
        assertThat(guardado.getValue().poblacionActual()).isEqualTo(975);
        assertThat(guardado.getValue().poblacionInicial()).isEqualTo(1000);
    }

    @Test
    void guardaElRegistroDeProcesamientoConLosDatosAplicados() {
        loteExiste(lote(1000, 1000, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        guardarDevuelveLoRecibido();

        servicio.actualizar(comando(25));

        verify(registros).save(new RegistroProcesamiento(ALERTA, GALPON, LOTE_ID, 25, EVENTO, AHORA));
    }

    @Test
    void descuentosSucesivosDeAlertasDistintasSumanVeinticincoMasDiezSobreMil() {
        UUID otraAlerta = UUID.randomUUID();
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote(1000, 1000, null)), Optional.of(lote(1000, 975, null)));
        when(registros.findById(any())).thenReturn(Optional.empty());
        guardarDevuelveLoRecibido();

        servicio.actualizar(comando(25));
        ActualizarPoblacionResultado segundo = servicio.actualizar(
                new ActualizarPoblacionCommand(otraAlerta, LOTE_ID, GALPON, 10, EVENTO));

        assertThat(segundo.poblacionAnterior()).isEqualTo(975);
        assertThat(segundo.poblacionActual()).isEqualTo(965);
    }

    @Test
    void cantidadMayorQueLaPoblacionActualSeRechazaSinGuardarNada() {
        loteExiste(lote(1000, 40, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());

        assertRechazo(comando(41), ValidacionNegocioException.class, "CANTIDAD_EXCEDE_POBLACION");

        verify(lotes, never()).save(any());
        verify(registros, never()).save(any());
    }

    @Test
    void laPoblacionPuedeLlegarACeroSinOtroEfectoQueGuardarElLote() {
        loteExiste(lote(1000, 40, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());
        guardarDevuelveLoRecibido();

        ActualizarPoblacionResultado r = servicio.actualizar(comando(40));

        assertThat(r.poblacionActual()).isZero();
        verify(registros).save(any(RegistroProcesamiento.class));
    }

    @Test
    void cantidadCeroONegativaSeRechaza() {
        loteExiste(lote(1000, 1000, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());

        assertRechazo(comando(0), ValidacionNegocioException.class, "CANTIDAD_INVALIDA");
        assertRechazo(comando(-3), ValidacionNegocioException.class, "CANTIDAD_INVALIDA");

        verify(lotes, never()).save(any());
    }

    @Test
    void loteInexistenteSeRechaza() {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.empty());

        assertRechazo(comando(25), RecursoNoEncontradoException.class, "LOTE_NO_ENCONTRADO");
    }

    @Test
    void loteDeOtroGalponSeRechaza() {
        loteExiste(lote(1000, 1000, null));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());

        assertRechazo(new ActualizarPoblacionCommand(ALERTA, LOTE_ID, UUID.randomUUID(), 25, EVENTO),
                ValidacionNegocioException.class, "LOTE_NO_PERTENECE_AL_GALPON");

        verify(lotes, never()).save(any());
    }

    @Test
    void loteNoActivoSeRechaza() {
        loteExiste(lote(1000, 1000, Instant.parse("2026-09-20T13:15:00Z")));
        when(registros.findById(ALERTA)).thenReturn(Optional.empty());

        assertRechazo(comando(25), ValidacionNegocioException.class, "LOTE_NO_ACTIVO");

        verify(lotes, never()).save(any());
    }

    @Test
    void mismoAlertaIdConLosMismosDatosDevuelveElResultadoSinDescontarDeNuevo() {
        loteExiste(lote(1000, 975, null));
        when(registros.findById(ALERTA)).thenReturn(
                Optional.of(new RegistroProcesamiento(ALERTA, GALPON, LOTE_ID, 25, EVENTO, AHORA)));

        ActualizarPoblacionResultado r = servicio.actualizar(comando(25));

        assertThat(r).isEqualTo(new ActualizarPoblacionResultado(LOTE_ID, 975, 975, true));
        verify(lotes, never()).save(any());
        verify(registros, never()).save(any());
    }

    @Test
    void mismoAlertaIdConOtraCantidadEsAlertaInconsistente() {
        loteExiste(lote(1000, 975, null));
        when(registros.findById(ALERTA)).thenReturn(
                Optional.of(new RegistroProcesamiento(ALERTA, GALPON, LOTE_ID, 25, EVENTO, AHORA)));

        assertRechazo(comando(30), ConflictoException.class, "ALERTA_INCONSISTENTE");

        verify(lotes, never()).save(any());
    }

    @Test
    void mismoAlertaIdConOtraFechaEsAlertaInconsistente() {
        loteExiste(lote(1000, 975, null));
        when(registros.findById(ALERTA)).thenReturn(
                Optional.of(new RegistroProcesamiento(ALERTA, GALPON, LOTE_ID, 25, EVENTO, AHORA)));

        assertRechazo(new ActualizarPoblacionCommand(ALERTA, LOTE_ID, GALPON, 25, EVENTO.plusSeconds(60)),
                ConflictoException.class, "ALERTA_INCONSISTENTE");
    }
}
