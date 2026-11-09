package com.unimag.avicontrol.application.galpon;

import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.DetalleGalpon;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.HistorialLotes;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.ResultadoListado;
import com.unimag.avicontrol.application.usecase.galpon.GalponService;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones.CampoOrden;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US3: listado, detalle e historial de GalponService (consultas de solo lectura). */
@ExtendWith(MockitoExtension.class)
class GalponServiceConsultaTest {

    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Galpon GALPON = new Galpon(GALPON_ID, "Galpón Norte", 5000, EstadoGalpon.PRODUCTIVO, 0L);

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

    private static ConsultaGalpones consulta(String nombre, EstadoGalpon estado) {
        return new ConsultaGalpones(nombre, estado, CampoOrden.CAPACIDAD, false, 1, 10);
    }

    private static Pagina<Galpon> pagina(int total) {
        return new Pagina<>(total == 0 ? List.of() : List.of(GALPON), 1, 10, total);
    }

    private static Lote lote(String nombre, LocalDate ingreso, Instant desvinculadoEn) {
        return new Lote(UUID.randomUUID(), GALPON_ID, nombre, ingreso, 1000, 950, 5_000_000, desvinculadoEn, 0L);
    }

    // ---------------------------------------------------------------- listar

    @Test
    void listarConCoincidenciasDevuelveLaPaginaSinMarcarSinCoincidencias() {
        ConsultaGalpones c = consulta("norte", null);
        when(galpones.search(c)).thenReturn(pagina(1));

        ResultadoListado resultado = servicio.listar(c);

        assertThat(resultado.sinCoincidencias()).isFalse();
        assertThat(resultado.pagina().totalElementos()).isEqualTo(1);
        verify(galpones, times(1)).search(c);
    }

    @Test
    void sinCoincidenciasRepiteLaConsultaSinNombreConservandoEstadoYOrden() {
        ConsultaGalpones c = consulta("zzz", EstadoGalpon.PRODUCTIVO);
        when(galpones.search(c)).thenReturn(pagina(0));
        when(galpones.search(c.sinNombre())).thenReturn(pagina(3));

        ResultadoListado resultado = servicio.listar(c);

        assertThat(resultado.sinCoincidencias()).isTrue();
        assertThat(resultado.pagina().totalElementos()).isEqualTo(3);
        ConsultaGalpones repetida = c.sinNombre();
        assertThat(repetida.nombre()).isNull();
        assertThat(repetida.estado()).isEqualTo(EstadoGalpon.PRODUCTIVO);
        assertThat(repetida.campo()).isEqualTo(CampoOrden.CAPACIDAD);
        assertThat(repetida.ascendente()).isFalse();
        verify(galpones).search(repetida);
    }

    @Test
    void sinNombreNoHayFiltroNiSegundaBusquedaAunqueNoHayaGalpones() {
        ConsultaGalpones c = consulta(null, EstadoGalpon.AISLAMIENTO);
        when(galpones.search(c)).thenReturn(pagina(0));

        ResultadoListado resultado = servicio.listar(c);

        assertThat(resultado.sinCoincidencias()).isFalse();
        assertThat(resultado.pagina().contenido()).isEmpty();
        verify(galpones, times(1)).search(c);
    }

    // --------------------------------------------------------------- detalle

    @Test
    void detalleDeGalponInexistenteEs404YNoConsultaLotes() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.detalle(GALPON_ID))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(lotes);
    }

    @Test
    void detalleConLoteActivoLoDevuelveSinIncidencias() {
        Lote activo = lote("Lote A1", LocalDate.of(2026, 10, 5), null);
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findActivoByGalponId(GALPON_ID)).thenReturn(Optional.of(activo));

        DetalleGalpon detalle = servicio.detalle(GALPON_ID);

        assertThat(detalle.galpon()).isEqualTo(GALPON);
        assertThat(detalle.loteActivo()).isEqualTo(activo);
        assertThat(detalle.sinLotes()).isFalse();
        assertThat(detalle.incidencias()).isEmpty();
        verify(lotes, never()).existsByGalponId(GALPON_ID);
    }

    @Test
    void detalleSinLoteActivoPeroConLotesAnterioresNoEsSinLotes() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findActivoByGalponId(GALPON_ID)).thenReturn(Optional.empty());
        when(lotes.existsByGalponId(GALPON_ID)).thenReturn(true);

        DetalleGalpon detalle = servicio.detalle(GALPON_ID);

        assertThat(detalle.loteActivo()).isNull();
        assertThat(detalle.sinLotes()).isFalse();
    }

    @Test
    void detalleDeGalponQueNuncaTuvoLotesEsSinLotes() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findActivoByGalponId(GALPON_ID)).thenReturn(Optional.empty());
        when(lotes.existsByGalponId(GALPON_ID)).thenReturn(false);

        DetalleGalpon detalle = servicio.detalle(GALPON_ID);

        assertThat(detalle.loteActivo()).isNull();
        assertThat(detalle.sinLotes()).isTrue();
        assertThat(detalle.incidencias()).isEmpty();
    }

    @Test
    void loteConNombreVacioGeneraIncidenciaSinTumbarLaConsulta() {
        Lote activo = lote("  ", LocalDate.of(2026, 10, 5), null);
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findActivoByGalponId(GALPON_ID)).thenReturn(Optional.of(activo));

        DetalleGalpon detalle = servicio.detalle(GALPON_ID);

        assertThat(detalle.loteActivo()).isEqualTo(activo);
        assertThat(detalle.incidencias()).singleElement().satisfies(i -> {
            assertThat(i.codigo()).isEqualTo("CAMPO_NO_DISPONIBLE");
            assertThat(i.mensaje()).isEqualTo("El nombre del lote no está disponible.");
        });
    }

    @Test
    void loteSinFechaDeIngresoGeneraIncidenciaSinTumbarLaConsulta() {
        Lote activo = lote("Lote A1", null, null);
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findActivoByGalponId(GALPON_ID)).thenReturn(Optional.of(activo));

        DetalleGalpon detalle = servicio.detalle(GALPON_ID);

        assertThat(detalle.incidencias()).singleElement().satisfies(i -> {
            assertThat(i.codigo()).isEqualTo("CAMPO_NO_DISPONIBLE");
            assertThat(i.mensaje()).contains("fecha de ingreso");
        });
    }

    // ------------------------------------------------------------- historial

    @Test
    void historialDeGalponInexistenteEs404() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.historialLotes(GALPON_ID))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(lotes);
    }

    @Test
    void historialSeparaElLoteActivoDeLosAnterioresConservandoElOrden() {
        Lote activo = lote("Lote A1", LocalDate.of(2026, 10, 5), null);
        Lote anterior1 = lote("Lote Z9", LocalDate.of(2026, 8, 1), Instant.parse("2026-09-20T13:15:00Z"));
        Lote anterior2 = lote("Lote Y8", LocalDate.of(2026, 6, 1), Instant.parse("2026-07-20T13:15:00Z"));
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findByGalponIdOrderByFechaIngresoDesc(GALPON_ID)).thenReturn(List.of(activo, anterior1, anterior2));

        HistorialLotes historial = servicio.historialLotes(GALPON_ID);

        assertThat(historial.galponId()).isEqualTo(GALPON_ID);
        assertThat(historial.loteActivo()).isEqualTo(activo);
        assertThat(historial.lotesAnteriores()).containsExactly(anterior1, anterior2);
        assertThat(historial.incidencias()).isEmpty();
    }

    @Test
    void historialSinLoteActivoSoloTraeLosAnteriores() {
        Lote anterior = lote("Lote Z9", LocalDate.of(2026, 8, 1), Instant.parse("2026-09-20T13:15:00Z"));
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findByGalponIdOrderByFechaIngresoDesc(GALPON_ID)).thenReturn(List.of(anterior));

        HistorialLotes historial = servicio.historialLotes(GALPON_ID);

        assertThat(historial.loteActivo()).isNull();
        assertThat(historial.lotesAnteriores()).containsExactly(anterior);
    }

    @Test
    void historialDeGalponQueNuncaTuvoLotesVieneVacio() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findByGalponIdOrderByFechaIngresoDesc(GALPON_ID)).thenReturn(List.of());

        HistorialLotes historial = servicio.historialLotes(GALPON_ID);

        assertThat(historial.loteActivo()).isNull();
        assertThat(historial.lotesAnteriores()).isEmpty();
        assertThat(historial.incidencias()).isEmpty();
    }

    @Test
    void historialJuntaLasIncidenciasDelLoteActivoYDeLosAnteriores() {
        Lote activo = lote("", LocalDate.of(2026, 10, 5), null);
        Lote anterior = lote("Lote Z9", null, Instant.parse("2026-09-20T13:15:00Z"));
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(GALPON));
        when(lotes.findByGalponIdOrderByFechaIngresoDesc(GALPON_ID)).thenReturn(List.of(activo, anterior));

        HistorialLotes historial = servicio.historialLotes(GALPON_ID);

        assertThat(historial.incidencias()).hasSize(2)
                .allSatisfy(i -> assertThat(i.codigo()).isEqualTo("CAMPO_NO_DISPONIBLE"));
    }
}
