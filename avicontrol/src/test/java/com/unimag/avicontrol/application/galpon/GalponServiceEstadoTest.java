package com.unimag.avicontrol.application.galpon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase.TransicionesDisponibles;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.galpon.GalponService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US4: las transiciones del administrador; el estado solo lo escribe GalponEstadoService (FR-012). */
@ExtendWith(MockitoExtension.class)
class GalponServiceEstadoTest {

    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private HistorialEstadoRepository historialEstados;
    @Mock
    private GalponEstadoService estados;

    private GalponService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GalponService(galpones, lotes, historialEstados, estados);
    }

    private void galponEn(EstadoGalpon estado) {
        when(galpones.findById(GALPON_ID)).thenReturn(
                Optional.of(new Galpon(GALPON_ID, "Galpón Norte", 5000, estado, 0L)));
    }

    private static HistorialEstado fila(EstadoGalpon anterior, EstadoGalpon nuevo) {
        return new HistorialEstado(UUID.randomUUID(), GALPON_ID, anterior, nuevo, Instant.parse("2026-10-05T14:15:00Z"),
                OrigenCambioEstado.ADMINISTRADOR);
    }

    private void assertRechazo(EstadoGalpon esperado, EstadoGalpon destino, String codigo) {
        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.actualizarEstado(GALPON_ID, esperado, destino))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    @ParameterizedTest(name = "{0} -> {1} lo pide el administrador")
    @CsvSource({"PRODUCTIVO,EN_COSECHA", "AISLAMIENTO,EN_COSECHA", "MANTENIMIENTO,DISPONIBLE"})
    void lasTresTransicionesDelAdministradorLlamanAGalponEstadoServiceConOrigenAdministrador(
            EstadoGalpon desde, EstadoGalpon hacia) {
        galponEn(desde);
        HistorialEstado escrita = fila(desde, hacia);
        when(estados.solicitarCambio(GALPON_ID, desde, hacia, OrigenCambioEstado.ADMINISTRADOR)).thenReturn(escrita);

        HistorialEstado resultado = servicio.actualizarEstado(GALPON_ID, desde, hacia);

        assertThat(resultado).isSameAs(escrita);
        verify(estados).solicitarCambio(GALPON_ID, desde, hacia, OrigenCambioEstado.ADMINISTRADOR);
    }

    @Test
    void elServicioNoEscribeElEstadoPorSiMismo() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        when(estados.solicitarCambio(any(), any(), any(), any())).thenReturn(
                fila(EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA));

        servicio.actualizarEstado(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA);

        verify(galpones, never()).save(any());
        verify(historialEstados, never()).save(any());
    }

    @ParameterizedTest(name = "{0} -> {1} da ORIGEN_NO_AUTORIZADO")
    @CsvSource({"DISPONIBLE,PRODUCTIVO", "DISPONIBLE,MANTENIMIENTO", "PRODUCTIVO,AISLAMIENTO",
            "EN_COSECHA,VACIADO_SANITARIO", "VACIADO_SANITARIO,DISPONIBLE", "AISLAMIENTO,PRODUCTIVO"})
    void transicionDeLaMatrizQueElAdministradorNoPuedePedirDaOrigenNoAutorizado(EstadoGalpon desde, EstadoGalpon hacia) {
        galponEn(desde);

        assertRechazo(desde, hacia, "ORIGEN_NO_AUTORIZADO");

        verifyNoInteractions(estados);
    }

    @ParameterizedTest(name = "{0} -> {1} da TRANSICION_NO_PERMITIDA")
    @CsvSource({"AISLAMIENTO,VACIADO_SANITARIO", "PRODUCTIVO,DISPONIBLE", "DISPONIBLE,EN_COSECHA",
            "EN_COSECHA,PRODUCTIVO", "PRODUCTIVO,PRODUCTIVO"})
    void transicionFueraDeLaMatrizDaTransicionNoPermitida(EstadoGalpon desde, EstadoGalpon hacia) {
        galponEn(desde);

        assertRechazo(desde, hacia, "TRANSICION_NO_PERMITIDA");

        verifyNoInteractions(estados);
    }

    @Test
    void siElEstadoVigenteNoEsElEsperadoDaEstadoEsperadoCambiado() {
        galponEn(EstadoGalpon.AISLAMIENTO);

        assertRechazo(EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA, "ESTADO_ESPERADO_CAMBIADO");

        verifyNoInteractions(estados);
    }

    @Test
    void siGalponEstadoServiceRechazaPorConcurrenciaElRechazoSePropaga() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        ConflictoException falla = new ConflictoException("ESTADO_ESPERADO_CAMBIADO", "El estado del galpón cambió.");
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(falla);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.actualizarEstado(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA))
                .isSameAs(falla);
    }

    @Test
    void cambiarElEstadoDeUnGalponInexistenteDa404() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.actualizarEstado(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(estados);
    }

    // ------------------------------------------------------------- transiciones

    @ParameterizedTest(name = "desde {0} el administrador puede pedir {1}")
    @CsvSource({"PRODUCTIVO,EN_COSECHA", "AISLAMIENTO,EN_COSECHA", "MANTENIMIENTO,DISPONIBLE"})
    void transicionesListaLosDestinosManualesDelEstadoActual(EstadoGalpon actual, EstadoGalpon destino) {
        galponEn(actual);

        TransicionesDisponibles t = servicio.transiciones(GALPON_ID);

        assertThat(t.galponId()).isEqualTo(GALPON_ID);
        assertThat(t.estadoActual()).isEqualTo(actual);
        assertThat(t.destinos()).containsExactly(destino);
    }

    @ParameterizedTest(name = "desde {0} el administrador no tiene salidas manuales")
    @CsvSource({"DISPONIBLE", "EN_COSECHA", "VACIADO_SANITARIO"})
    void transicionesSinSalidasManualesDevuelveListaVacia(EstadoGalpon actual) {
        galponEn(actual);

        assertThat(servicio.transiciones(GALPON_ID).destinos()).isEmpty();
    }

    @Test
    void transicionesDeUnGalponInexistenteDa404() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.transiciones(GALPON_ID))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));
    }

    // ---------------------------------------------------------------- historial

    @Test
    void historialDelegaEnElRepositorioConLaPaginaPedida() {
        galponEn(EstadoGalpon.EN_COSECHA);
        Pagina<HistorialEstado> pagina = new Pagina<>(List.of(fila(EstadoGalpon.PRODUCTIVO, EstadoGalpon.EN_COSECHA)),
                2, 5, 6);
        when(historialEstados.findByGalponId(GALPON_ID, 2, 5)).thenReturn(pagina);

        assertThat(servicio.historial(GALPON_ID, 2, 5)).isSameAs(pagina);
    }

    @Test
    void historialDeUnGalponInexistenteDa404SinConsultarElHistorial() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.historial(GALPON_ID, 1, 20))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(historialEstados);
    }
}
