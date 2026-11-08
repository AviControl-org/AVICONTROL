package com.unimag.avicontrol.application.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.lote.RegistrarLoteUseCase.Comando;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.lote.LoteService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoteServiceTest {

    /** 2026-11-05 10:00 en America/Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZonaNegocio.BOGOTA);
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private GalponEstadoService estados;

    private LoteService servicio;

    @BeforeEach
    void preparar() {
        servicio = new LoteService(galpones, lotes, estados, RELOJ);
    }

    private static Galpon galpon(EstadoGalpon estado) {
        return new Galpon(GALPON_ID, "Galpón Norte", 5000, estado, 0L);
    }

    private static Comando comando(String nombre, LocalDate fecha) {
        return new Comando(GALPON_ID, nombre, fecha, 1000, 2_500_000);
    }

    private void galponDisponibleYNombreLibre() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galpon(EstadoGalpon.DISPONIBLE)));
        when(lotes.existsByNombreClave(any())).thenReturn(false);
    }

    private void guardarDevuelveLoRecibido() {
        when(lotes.save(any(Lote.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registraElLoteActivoConLaPoblacionActualIgualALaInicialYPasaElGalponAProductivo() {
        galponDisponibleYNombreLibre();
        guardarDevuelveLoRecibido();

        Lote lote = servicio.registrar(comando("Lote 2026-01", HOY));

        assertThat(lote.id()).isNotNull();
        assertThat(lote.galponId()).isEqualTo(GALPON_ID);
        assertThat(lote.nombre()).isEqualTo("Lote 2026-01");
        assertThat(lote.fechaIngreso()).isEqualTo(HOY);
        assertThat(lote.poblacionInicial()).isEqualTo(1000);
        assertThat(lote.poblacionActual()).isEqualTo(1000);
        assertThat(lote.costoTotal()).isEqualTo(2_500_000);
        assertThat(lote.activo()).isTrue();

        InOrder orden = inOrder(estados, lotes);
        orden.verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.DISPONIBLE, EstadoGalpon.PRODUCTIVO,
                OrigenCambioEstado.REGISTRO_LOTE);
        orden.verify(lotes).save(any(Lote.class));
    }

    @Test
    void recortaElNombreYConsultaLaUnicidadPorSuClave() {
        galponDisponibleYNombreLibre();
        guardarDevuelveLoRecibido();

        Lote lote = servicio.registrar(comando("  Lote Uno  ", HOY));

        assertThat(lote.nombre()).isEqualTo("Lote Uno");
        verify(lotes).existsByNombreClave("lote uno");
    }

    @Test
    void aceptaUnaFechaDeIngresoPasada() {
        galponDisponibleYNombreLibre();
        guardarDevuelveLoRecibido();

        assertThat(servicio.registrar(comando("Lote Uno", HOY.minusDays(10))).fechaIngreso())
                .isEqualTo(HOY.minusDays(10));
    }

    @Test
    void fechaDeIngresoFuturaEsInvalidaYNoTocaNada() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY.plusDays(1))))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("FECHA_INGRESO_FUTURA"));

        verifyNoInteractions(galpones, lotes, estados);
    }

    @Test
    void nombreInvalidoEsRechazadoAntesDeTocarNada() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote #1", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_INVALIDO"));

        verifyNoInteractions(galpones, lotes, estados);
    }

    @Test
    void galponInexistenteEs404YNoGuarda() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verifyNoInteractions(estados);
        verify(lotes, never()).save(any());
    }

    @Test
    void nombreDuplicadoSinImportarMayusculasEsConflictoYNoCambiaElEstado() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galpon(EstadoGalpon.DISPONIBLE)));
        when(lotes.existsByNombreClave("lote uno")).thenReturn(true);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(comando("LOTE UNO", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO"));

        verifyNoInteractions(estados);
        verify(lotes, never()).save(any());
    }

    @Test
    void elNombreDuplicadoSeValidaAntesQueElEstadoDelGalpon() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galpon(EstadoGalpon.PRODUCTIVO)));
        when(lotes.existsByNombreClave("lote uno")).thenReturn(true);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO"));
    }

    @Test
    void galponQueNoEstaDisponibleEsConflictoYNoCambiaElEstadoNiGuarda() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galpon(EstadoGalpon.MANTENIMIENTO)));
        when(lotes.existsByNombreClave(any())).thenReturn(false);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_DISPONIBLE"));

        verifyNoInteractions(estados);
        verify(lotes, never()).save(any());
    }

    @Test
    void siOtroAdministradorGanoLaCarreraElEstadoEsperadoCambiadoSeTraduceAGalponNoDisponible() {
        galponDisponibleYNombreLibre();
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(new ConflictoException(
                "ESTADO_ESPERADO_CAMBIADO", "El estado del galpón cambió."));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_DISPONIBLE"));

        verify(lotes, never()).save(any());
    }

    @Test
    void siGalponEstadoServiceFallaLaExcepcionSePropagaYElLoteNoSeGuarda() {
        galponDisponibleYNombreLibre();
        RecursoNoEncontradoException falla = new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe.");
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(falla);

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .isSameAs(falla);

        verify(lotes, never()).save(any());
    }

    @Test
    void siGuardarElLoteFallaLaExcepcionSePropagaParaQueSeRevierteElCambioDeEstado() {
        galponDisponibleYNombreLibre();
        ConflictoException falla = ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre.");
        when(lotes.save(any(Lote.class))).thenThrow(falla);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.registrar(comando("Lote Uno", HOY)))
                .isSameAs(falla);
    }

    @Test
    void cadaLoteRecibeUnUuidDistinto() {
        galponDisponibleYNombreLibre();
        guardarDevuelveLoRecibido();

        ArgumentCaptor<Lote> guardados = ArgumentCaptor.forClass(Lote.class);
        servicio.registrar(comando("Lote Uno", HOY));
        servicio.registrar(comando("Lote Dos", HOY));

        verify(lotes, org.mockito.Mockito.times(2)).save(guardados.capture());
        assertThat(guardados.getAllValues().get(0).id()).isNotEqualTo(guardados.getAllValues().get(1).id());
    }

    @Test
    void listarDelegaEnElRepositorioConLaPaginaYElTamanio() {
        Pagina<Lote> pagina = new Pagina<>(List.of(), 2, 20, 0);
        when(lotes.findAll(2, 20)).thenReturn(pagina);

        assertThat(servicio.listar(2, 20)).isSameAs(pagina);
    }
}
