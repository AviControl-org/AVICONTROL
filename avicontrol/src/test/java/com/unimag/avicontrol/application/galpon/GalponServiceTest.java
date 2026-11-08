package com.unimag.avicontrol.application.galpon;

import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase.Comando;
import com.unimag.avicontrol.application.usecase.galpon.GalponService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GalponServiceTest {

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

    private void guardarDevuelveLoRecibido() {
        when(galpones.save(any(Galpon.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registraElGalponEnEstadoDisponibleConUuidYLoGuarda() {
        guardarDevuelveLoRecibido();

        Galpon resultado = servicio.registrar(new Comando("Galpón Norte", 1000));

        ArgumentCaptor<Galpon> guardado = ArgumentCaptor.forClass(Galpon.class);
        verify(galpones).save(guardado.capture());
        assertThat(guardado.getValue()).isEqualTo(resultado);
        assertThat(resultado.id()).isNotNull();
        assertThat(resultado.nombre()).isEqualTo("Galpón Norte");
        assertThat(resultado.capacidad()).isEqualTo(1000);
        assertThat(resultado.estado()).isEqualTo(EstadoGalpon.DISPONIBLE);
    }

    @Test
    void cadaGalponRecibeUnUuidDistinto() {
        guardarDevuelveLoRecibido();

        Galpon primero = servicio.registrar(new Comando("Galpón Norte", 1000));
        Galpon segundo = servicio.registrar(new Comando("Galpón Sur", 1000));

        assertThat(primero.id()).isNotEqualTo(segundo.id());
    }

    @Test
    void recortaLosEspaciosDelNombreYConsultaLaUnicidadPorSuClave() {
        guardarDevuelveLoRecibido();

        Galpon resultado = servicio.registrar(new Comando("  Galpón Norte  ", 1000));

        assertThat(resultado.nombre()).isEqualTo("Galpón Norte");
        verify(galpones).existsByNombreClave("galpón norte");
    }

    @Test
    void nombreDuplicadoSinImportarMayusculasEsConflictoYNoGuarda() {
        when(galpones.existsByNombreClave("galpón norte")).thenReturn(true);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(new Comando("GALPÓN NORTE", 500)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO");
                    assertThat(e.getMessage()).isEqualTo("Ya existe un galpón con ese nombre.");
                    assertThat(e.errores()).hasSize(1);
                });

        verify(galpones, never()).save(any());
    }

    @ParameterizedTest(name = "nombre [{0}] es inválido")
    @ValueSource(strings = {"", "   ", "Galpón #1", "Galpón@Norte", "Galpón/Norte"})
    void nombreInvalidoEsRechazadoAntesDeTocarElRepositorio(String nombre) {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(new Comando(nombre, 1000)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_INVALIDO"));

        verifyNoMoreInteractions(galpones);
    }

    @Test
    void nombreNuloEsInvalido() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(new Comando(null, 1000)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_INVALIDO"));

        verifyNoMoreInteractions(galpones);
    }

    @Test
    void nombreDeMasDeCienCaracteresEsInvalidoYElDeCienEsValido() {
        guardarDevuelveLoRecibido();

        assertThat(servicio.registrar(new Comando("A".repeat(100), 1000)).nombre()).hasSize(100);

        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(new Comando("A".repeat(101), 1000)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_INVALIDO"));
    }

    @Test
    void admiteLetrasNumerosEspaciosGuionesYGuionesBajos() {
        guardarDevuelveLoRecibido();

        Galpon resultado = servicio.registrar(new Comando("Galpón_Norte-2 B", 1000));

        assertThat(resultado.nombre()).isEqualTo("Galpón_Norte-2 B");
    }

    @ParameterizedTest(name = "capacidad {0} es inválida")
    @ValueSource(longs = {0, -1, -5, Long.MIN_VALUE})
    void capacidadNoPositivaEsInvalidaYNoGuarda(long capacidad) {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(new Comando("Galpón Norte", capacidad)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("CAPACIDAD_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("La capacidad debe ser un número entero mayor que cero.");
                });

        verify(galpones, never()).save(any());
    }

    @Test
    void laCapacidadMinimaValidaEsUno() {
        guardarDevuelveLoRecibido();

        assertThat(servicio.registrar(new Comando("Galpón Norte", 1)).capacidad()).isEqualTo(1);
    }
}
