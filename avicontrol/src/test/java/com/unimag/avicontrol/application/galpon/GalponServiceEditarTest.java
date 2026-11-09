package com.unimag.avicontrol.application.galpon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.galpon.EditarGalponUseCase.Comando;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.galpon.GalponService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialCambioGalpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialCambioGalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US9: el nombre se edita siempre, la capacidad solo en MANTENIMIENTO y cada campo cambiado deja su historial. */
@ExtendWith(MockitoExtension.class)
class GalponServiceEditarTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private HistorialEstadoRepository historialEstados;
    @Mock
    private HistorialCambioGalponRepository historialCambios;
    @Mock
    private GalponEstadoService estados;

    private GalponService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GalponService(galpones, lotes, historialEstados, historialCambios, estados,
                Clock.fixed(AHORA, ZonaNegocio.BOGOTA));
    }

    private static Galpon galpon(EstadoGalpon estado) {
        return new Galpon(ID, "Galpón Norte", 1000, estado, 0L);
    }

    private void galponEn(EstadoGalpon estado) {
        when(galpones.findById(ID)).thenReturn(Optional.of(galpon(estado)));
    }

    private void galponEnBloqueado(EstadoGalpon estado) {
        galponEn(estado);
        when(galpones.findByIdForUpdate(ID)).thenReturn(Optional.of(galpon(estado)));
    }

    private void guardarDevuelveLoRecibido() {
        when(galpones.save(any(Galpon.class))).thenAnswer(i -> i.getArgument(0));
    }

    private List<HistorialCambioGalpon> historialGuardado(int veces) {
        ArgumentCaptor<HistorialCambioGalpon> captor = ArgumentCaptor.forClass(HistorialCambioGalpon.class);
        verify(historialCambios, times(veces)).save(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------- nombre

    @Test
    void cambiarElNombreEnCualquierEstadoGuardaYEscribeUnRegistroDeHistorial() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        when(galpones.existsByNombreClaveAndIdNot("galpón sur", ID)).thenReturn(false);
        guardarDevuelveLoRecibido();

        Galpon editado = servicio.editar(ID, new Comando("  Galpón Sur ", null));

        assertThat(editado.nombre()).isEqualTo("Galpón Sur");
        assertThat(editado.capacidad()).isEqualTo(1000);
        assertThat(editado.estado()).isEqualTo(EstadoGalpon.PRODUCTIVO);
        HistorialCambioGalpon cambio = historialGuardado(1).get(0);
        assertThat(cambio.galponId()).isEqualTo(ID);
        assertThat(cambio.campo()).isEqualTo("nombre");
        assertThat(cambio.valorAnterior()).isEqualTo("Galpón Norte");
        assertThat(cambio.valorNuevo()).isEqualTo("Galpón Sur");
        assertThat(cambio.fechaHora()).isEqualTo(AHORA);
        verify(galpones, never()).findByIdForUpdate(any());
    }

    @Test
    void guardarElMismoNombreNoEsDuplicadoNiGeneraHistorial() {
        galponEn(EstadoGalpon.DISPONIBLE);

        Galpon resultado = servicio.editar(ID, new Comando("Galpón Norte", null));

        assertThat(resultado.nombre()).isEqualTo("Galpón Norte");
        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
        verify(galpones, never()).existsByNombreClaveAndIdNot(any(), any());
    }

    @Test
    void cambiarSoloLasMayusculasDelPropioNombreEsUnCambioYNoChocaConElMismoGalpon() {
        galponEn(EstadoGalpon.DISPONIBLE);
        when(galpones.existsByNombreClaveAndIdNot("galpón norte", ID)).thenReturn(false);
        guardarDevuelveLoRecibido();

        Galpon editado = servicio.editar(ID, new Comando("galpón norte", null));

        assertThat(editado.nombre()).isEqualTo("galpón norte");
        assertThat(historialGuardado(1).get(0).campo()).isEqualTo("nombre");
    }

    @Test
    void rechazaUnNombreUsadoPorOtroGalpon() {
        galponEn(EstadoGalpon.DISPONIBLE);
        when(galpones.existsByNombreClaveAndIdNot("galpón b", ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando("Galpón B", null)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO");
                    assertThat(e.getMessage()).isEqualTo("Ya existe un galpón con ese nombre.");
                });

        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Galpón #1"})
    void rechazaUnNombreInvalido(String nombre) {
        galponEn(EstadoGalpon.DISPONIBLE);

        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(nombre, null)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_INVALIDO"));

        verify(galpones, never()).save(any());
    }

    // ---------------------------------------------------------- capacidad

    @Test
    void cambiarLaCapacidadEnMantenimientoGuardaYEscribeUnRegistroDeHistorial() {
        galponEnBloqueado(EstadoGalpon.MANTENIMIENTO);
        guardarDevuelveLoRecibido();

        Galpon editado = servicio.editar(ID, new Comando(null, 1500L));

        assertThat(editado.capacidad()).isEqualTo(1500);
        assertThat(editado.nombre()).isEqualTo("Galpón Norte");
        HistorialCambioGalpon cambio = historialGuardado(1).get(0);
        assertThat(cambio.campo()).isEqualTo("capacidad");
        assertThat(cambio.valorAnterior()).isEqualTo("1000");
        assertThat(cambio.valorNuevo()).isEqualTo("1500");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoGalpon.class, names = "MANTENIMIENTO", mode = EnumSource.Mode.EXCLUDE)
    void rechazaUnaCapacidadDistintaFueraDeMantenimiento(EstadoGalpon estado) {
        galponEnBloqueado(estado);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, 1500L)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("CAPACIDAD_NO_EDITABLE");
                    assertThat(e.getMessage())
                            .isEqualTo("La capacidad solo se puede modificar cuando el galpón está en mantenimiento.");
                });

        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    @Test
    void rechazaCambiarLaCapacidadSiElGalponTieneUnLoteActivoAunqueEsteEnMantenimiento() {
        galponEnBloqueado(EstadoGalpon.MANTENIMIENTO);
        when(lotes.findActivoByGalponId(ID)).thenReturn(Optional.of(
                Lote.nuevo(ID, NombreNormalizado.de("Lote 1"), LocalDate.of(2026, 9, 1), 500, 1000000)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, 1500L)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("CAPACIDAD_NO_EDITABLE");
                    assertThat(e.getMessage())
                            .isEqualTo("La capacidad no se puede modificar mientras el galpón tenga un lote activo.");
                });

        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    @Test
    void elLoteActivoNoImpideEditarSoloElNombre() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        when(galpones.existsByNombreClaveAndIdNot("galpón sur", ID)).thenReturn(false);
        guardarDevuelveLoRecibido();

        servicio.editar(ID, new Comando("Galpón Sur", null));

        verify(lotes, never()).findActivoByGalponId(any());
    }

    @Test
    void unaCapacidadIgualALaActualFueraDeMantenimientoNoEsErrorNiGeneraHistorial() {
        galponEn(EstadoGalpon.DISPONIBLE);

        Galpon resultado = servicio.editar(ID, new Comando(null, 1000L));

        assertThat(resultado.capacidad()).isEqualTo(1000);
        verify(galpones, never()).findByIdForUpdate(any());
        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    @Test
    void nombreYCapacidadDelMismoPedidoFueraDeMantenimientoNoGuardanNada() {
        galponEnBloqueado(EstadoGalpon.DISPONIBLE);
        when(galpones.existsByNombreClaveAndIdNot("galpón norte b", ID)).thenReturn(false);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando("Galpón Norte B", 1800L)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("CAPACIDAD_NO_EDITABLE"));

        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    void rechazaUnaCapacidadQueNoEsPositiva(long capacidad) {
        galponEn(EstadoGalpon.MANTENIMIENTO);

        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, capacidad)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("CAPACIDAD_INVALIDA"));

        verify(galpones, never()).save(any());
    }

    // -------------------------------------------------------------- ambos

    @Test
    void nombreYCapacidadCambiadosGeneranDosRegistrosConLaMismaMarcaDeTiempo() {
        galponEnBloqueado(EstadoGalpon.MANTENIMIENTO);
        when(galpones.existsByNombreClaveAndIdNot("galpón norte b", ID)).thenReturn(false);
        guardarDevuelveLoRecibido();

        Galpon editado = servicio.editar(ID, new Comando("Galpón Norte B", 1800L));

        assertThat(editado.nombre()).isEqualTo("Galpón Norte B");
        assertThat(editado.capacidad()).isEqualTo(1800);
        List<HistorialCambioGalpon> cambios = historialGuardado(2);
        assertThat(cambios).extracting(HistorialCambioGalpon::campo).containsExactlyInAnyOrder("nombre", "capacidad");
        assertThat(cambios).extracting(HistorialCambioGalpon::fechaHora).containsOnly(AHORA);
        verify(galpones, times(1)).save(any(Galpon.class));
    }

    @Test
    void losMismosValoresQueYaTieneNoSonUnCambio() {
        galponEn(EstadoGalpon.DISPONIBLE);

        Galpon resultado = servicio.editar(ID, new Comando("Galpón Norte", 1000L));

        assertThat(resultado).isEqualTo(galpon(EstadoGalpon.DISPONIBLE));
        verify(galpones, never()).save(any());
        verifyNoInteractions(historialCambios);
    }

    // ------------------------------------------------------------ errores

    @Test
    void sinNingunCampoDevuelveSinCambios() {
        galponEn(EstadoGalpon.DISPONIBLE);

        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, null)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("SIN_CAMBIOS");
                    assertThat(e.getMessage()).isEqualTo("Indica al menos un campo para modificar: nombre o capacidad.");
                });

        verify(galpones, never()).save(any());
    }

    @Test
    void unGalponInexistenteDevuelve404AunSinCampos() {
        when(galpones.findById(ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, null)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(historialCambios);
    }

    @Test
    void siElGalponDesapareceAlBloquearloDevuelve404() {
        galponEn(EstadoGalpon.MANTENIMIENTO);
        when(galpones.findByIdForUpdate(ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.editar(ID, new Comando(null, 1500L)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(historialCambios);
    }
}
