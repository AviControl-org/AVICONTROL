package com.unimag.avicontrol.application.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase.Comando;
import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase.ResultadoAtencion;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.mantenimiento.AlertaMantenimientoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.mantenimiento.AlertaMantenimientoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US8: reglas de generar y atender una alerta de mantenimiento; el estado del galpón solo lo escribe GalponEstadoService. */
@ExtendWith(MockitoExtension.class)
class AlertaMantenimientoServiceTest {

    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID ALERTA_ID = UUID.fromString("7a4f9e10-2b3c-4d5e-8f60-1a2b3c4d5e6f");
    private static final Instant AHORA = Instant.parse("2026-10-05T15:20:00Z");

    @Mock
    private AlertaMantenimientoRepository alertas;
    @Mock
    private GalponRepository galpones;
    @Mock
    private GalponEstadoService estados;

    private AlertaMantenimientoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertaMantenimientoService(alertas, galpones, estados,
                Clock.fixed(AHORA, ZonaNegocio.BOGOTA));
    }

    private static Galpon galponEn(EstadoGalpon estado) {
        return new Galpon(GALPON_ID, "Galpon Norte", 1000, estado, 0L);
    }

    private static Comando comando(String descripcion, String tecnicoId) {
        return new Comando(GALPON_ID, descripcion, "Alta", "Ventilación", tecnicoId);
    }

    private static AlertaMantenimiento alerta(EstadoAlertaMantenimiento estado) {
        return new AlertaMantenimiento(ALERTA_ID, GALPON_ID, "Reparar sistema de ventilación", AHORA, "tecnico-01",
                estado, "Alta", "Ventilación");
    }

    private static HistorialEstado cambioAMantenimiento() {
        return new HistorialEstado(UUID.randomUUID(), GALPON_ID, EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                AHORA, OrigenCambioEstado.ADMINISTRADOR);
    }

    // -------------------------------------------------------------- generar

    @Test
    void generaLaAlertaPendienteConLosDatosRecortadosYSinTocarElEstadoDelGalpon() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galponEn(EstadoGalpon.DISPONIBLE)));
        when(alertas.existsPendienteByGalponId(GALPON_ID)).thenReturn(false);
        when(alertas.save(any(AlertaMantenimiento.class))).thenAnswer(i -> i.getArgument(0));

        AlertaMantenimiento creada = servicio.generar(comando("  Reparar ventilación  ", " tecnico-01 "));

        assertThat(creada.id()).isNotNull();
        assertThat(creada.galponId()).isEqualTo(GALPON_ID);
        assertThat(creada.descripcion()).isEqualTo("Reparar ventilación");
        assertThat(creada.tecnicoId()).isEqualTo("tecnico-01");
        assertThat(creada.estado()).isEqualTo(EstadoAlertaMantenimiento.PENDIENTE);
        assertThat(creada.fechaHora()).isEqualTo(AHORA);
        assertThat(creada.severidad()).isEqualTo("Alta");
        assertThat(creada.tipo()).isEqualTo("Ventilación");
        verifyNoInteractions(estados);
    }

    @Test
    void laSeveridadYElTipoEnBlancoSeGuardanComoNulos() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galponEn(EstadoGalpon.DISPONIBLE)));
        when(alertas.existsPendienteByGalponId(GALPON_ID)).thenReturn(false);
        when(alertas.save(any(AlertaMantenimiento.class))).thenAnswer(i -> i.getArgument(0));

        AlertaMantenimiento creada = servicio.generar(new Comando(GALPON_ID, "Reparar", "  ", null, "tecnico-01"));

        assertThat(creada.severidad()).isNull();
        assertThat(creada.tipo()).isNull();
    }

    @Test
    void aceptaUnaDescripcionDeExactamente500Caracteres() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galponEn(EstadoGalpon.DISPONIBLE)));
        when(alertas.existsPendienteByGalponId(GALPON_ID)).thenReturn(false);
        when(alertas.save(any(AlertaMantenimiento.class))).thenAnswer(i -> i.getArgument(0));

        AlertaMantenimiento creada = servicio.generar(comando("a".repeat(500), "tecnico-01"));

        assertThat(creada.descripcion()).hasSize(500);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rechazaLaDescripcionVaciaOSoloConEspacios(String descripcion) {
        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.generar(comando(descripcion, "tecnico-01")))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("DESCRIPCION_INVALIDA"));

        verifyNoInteractions(alertas, galpones, estados);
    }

    @Test
    void rechazaLaDescripcionDe501Caracteres() {
        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.generar(comando("a".repeat(501), "tecnico-01")))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("DESCRIPCION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("La descripción es obligatoria y tiene máximo 500 caracteres.");
                });

        verifyNoInteractions(alertas, galpones, estados);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rechazaUnTecnicoVacio(String tecnicoId) {
        assertThatExceptionOfType(NegocioException.class)
                .isThrownBy(() -> servicio.generar(comando("Reparar", tecnicoId)))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("TECNICO_REQUERIDO"));

        verifyNoInteractions(alertas, galpones, estados);
    }

    @Test
    void rechazaUnGalponInexistente() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.generar(comando("Reparar", "tecnico-01")))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO");
                    assertThat(e.getMessage()).isEqualTo("El galpón seleccionado no existe.");
                });

        verify(alertas, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoGalpon.class, names = "DISPONIBLE", mode = EnumSource.Mode.EXCLUDE)
    void rechazaUnGalponQueNoEstaDisponible(EstadoGalpon estado) {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galponEn(estado)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.generar(comando("Reparar", "tecnico-01")))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("GALPON_NO_DISPONIBLE");
                    assertThat(e.getMessage()).isEqualTo("Solo se pueden generar alertas para galpones disponibles.");
                });

        verify(alertas, never()).save(any());
    }

    @Test
    void rechazaUnaSegundaAlertaPendienteDelMismoGalpon() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galponEn(EstadoGalpon.DISPONIBLE)));
        when(alertas.existsPendienteByGalponId(GALPON_ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.generar(comando("Reparar", "tecnico-01")))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("ALERTA_ACTIVA_EXISTENTE");
                    assertThat(e.getMessage()).isEqualTo("Ya existe una alerta activa para ese galpón.");
                });

        verify(alertas, never()).save(any());
    }

    // --------------------------------------------------------------- atender

    @Test
    void atenderMarcaLaAlertaAtendidaYPideElCambioAMantenimientoConOrigenAdministrador() {
        when(alertas.findById(ALERTA_ID)).thenReturn(Optional.of(alerta(EstadoAlertaMantenimiento.PENDIENTE)));
        when(estados.solicitarCambio(GALPON_ID, EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                OrigenCambioEstado.ADMINISTRADOR)).thenReturn(cambioAMantenimiento());
        when(alertas.save(any(AlertaMantenimiento.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoAtencion resultado = servicio.atender(ALERTA_ID);

        assertThat(resultado.alerta().id()).isEqualTo(ALERTA_ID);
        assertThat(resultado.alerta().estado()).isEqualTo(EstadoAlertaMantenimiento.ATENDIDA);
        assertThat(resultado.galponId()).isEqualTo(GALPON_ID);
        assertThat(resultado.estadoGalpon()).isEqualTo(EstadoGalpon.MANTENIMIENTO);
        verify(alertas).save(alerta(EstadoAlertaMantenimiento.ATENDIDA));
    }

    @Test
    void atenderUnaAlertaInexistenteDevuelve404SinTocarElGalpon() {
        when(alertas.findById(ALERTA_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.atender(ALERTA_ID))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("ALERTA_NO_ENCONTRADA");
                    assertThat(e.getMessage()).isEqualTo("La alerta no existe.");
                });

        verifyNoInteractions(estados);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAlertaMantenimiento.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    void atenderUnaAlertaQueNoEstaPendienteDevuelve409SinTocarElGalpon(EstadoAlertaMantenimiento estado) {
        when(alertas.findById(ALERTA_ID)).thenReturn(Optional.of(alerta(estado)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.atender(ALERTA_ID))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("ALERTA_NO_PENDIENTE");
                    assertThat(e.getMessage()).isEqualTo("La alerta ya no está pendiente.");
                });

        verifyNoInteractions(estados);
        verify(alertas, never()).save(any());
    }

    @Test
    void siElGalponYaNoEstaDisponibleLaAlertaNoSeGuardaComoAtendida() {
        when(alertas.findById(ALERTA_ID)).thenReturn(Optional.of(alerta(EstadoAlertaMantenimiento.PENDIENTE)));
        when(estados.solicitarCambio(GALPON_ID, EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                OrigenCambioEstado.ADMINISTRADOR)).thenThrow(new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo."));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.atender(ALERTA_ID))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("ESTADO_ESPERADO_CAMBIADO");
                    assertThat(e.getMessage())
                            .isEqualTo("El galpón ya no está disponible. Actualiza la información e inténtalo de nuevo.");
                });

        verify(alertas, never()).save(any());
    }

    @Test
    void otroRechazoDelCambioDeEstadoSePropagaSinCambiosYSinGuardarLaAlerta() {
        when(alertas.findById(ALERTA_ID)).thenReturn(Optional.of(alerta(EstadoAlertaMantenimiento.PENDIENTE)));
        when(estados.solicitarCambio(GALPON_ID, EstadoGalpon.DISPONIBLE, EstadoGalpon.MANTENIMIENTO,
                OrigenCambioEstado.ADMINISTRADOR)).thenThrow(
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.atender(ALERTA_ID))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verify(alertas, never()).save(any());
    }

    // ---------------------------------------------------------------- listar

    @Test
    void listarDelegaEnElRepositorioConLosFiltros() {
        Pagina<AlertaMantenimiento> pagina = new Pagina<>(List.of(alerta(EstadoAlertaMantenimiento.ATENDIDA)), 2, 5, 6);
        when(alertas.search(EstadoAlertaMantenimiento.ATENDIDA, GALPON_ID, 2, 5)).thenReturn(pagina);

        assertThat(servicio.listar(EstadoAlertaMantenimiento.ATENDIDA, GALPON_ID, 2, 5)).isSameAs(pagina);
    }
}
