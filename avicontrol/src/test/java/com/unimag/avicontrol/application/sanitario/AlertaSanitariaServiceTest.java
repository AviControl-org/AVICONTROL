package com.unimag.avicontrol.application.sanitario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirAlertaSanitariaUseCase.Comando;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.application.usecase.sanitario.AlertaSanitariaService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.NegocioException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertaSanitariaServiceTest {

    /** 2026-10-05 10:00 en America/Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T15:00:00Z"), ZonaNegocio.BOGOTA);
    private static final UUID ALERTA = UUID.fromString("5b1c7d52-3a08-4f0e-9c1d-77a1d4e0b6aa");
    private static final UUID GALPON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID LOTE_ID = UUID.fromString("2fc03a21-84c4-4c83-bb15-6607bb75cb90");
    private static final OffsetDateTime HOY = OffsetDateTime.parse("2026-10-05T09:15:00-05:00");
    private static final String ENFERMEDAD = "Sospecha de Newcastle";
    private static final String DESCRIPCION = "Mortalidad atípica y signos respiratorios";

    @Mock
    private GalponRepository galpones;
    @Mock
    private LoteRepository lotes;
    @Mock
    private GalponEstadoService estados;

    private AlertaSanitariaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertaSanitariaService(galpones, lotes, estados, RELOJ);
    }

    private static Galpon galpon(EstadoGalpon estado) {
        return new Galpon(GALPON_ID, "Galpón Norte", 5000, estado, 0L);
    }

    private static Lote lote(UUID galponId, Instant desvinculadoEn) {
        return new Lote(LOTE_ID, galponId, "Lote A1", LocalDate.of(2026, 9, 15), 1000, 975, 5_000_000,
                desvinculadoEn, 0L);
    }

    private static Comando aislamiento(UUID galponId, UUID loteId) {
        return new Comando(ALERTA, "AISLAMIENTO", galponId, loteId, HOY, ENFERMEDAD, DESCRIPCION, null);
    }

    private static Comando reanudacion() {
        return new Comando(ALERTA, "REANUDACION", GALPON_ID, null, HOY, null, null, null);
    }

    private void galponEn(EstadoGalpon estado) {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.of(galpon(estado)));
    }

    private void assertRechazo(Comando comando, Class<? extends NegocioException> tipo, String codigo) {
        assertThatExceptionOfType(tipo).isThrownBy(() -> servicio.recibir(comando))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    // ------------------------------------------------------------ casos felices

    @Test
    void aislamientoDeUnGalponProductivoSolicitaProductivoAAislamientoConOrigenDelModulo2() {
        galponEn(EstadoGalpon.PRODUCTIVO);

        servicio.recibir(aislamiento(GALPON_ID, null));

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.AISLAMIENTO,
                OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
    }

    @Test
    void reanudacionDeUnGalponEnAislamientoSolicitaAislamientoAProductivoSinExigirTipoNiDescripcion() {
        galponEn(EstadoGalpon.AISLAMIENTO);

        servicio.recibir(reanudacion());

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.AISLAMIENTO, EstadoGalpon.PRODUCTIVO,
                OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
    }

    @Test
    void alertaIdentificadaPorLoteActivoResuelveElGalponYNoModificaElLote() {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote(GALPON_ID, null)));
        galponEn(EstadoGalpon.PRODUCTIVO);

        servicio.recibir(aislamiento(null, LOTE_ID));

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.AISLAMIENTO,
                OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
        verify(lotes).findById(LOTE_ID);
        verifyNoMoreInteractions(lotes);
    }

    @Test
    void galponYLoteQueCoincidenSeAceptan() {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote(GALPON_ID, null)));
        galponEn(EstadoGalpon.PRODUCTIVO);

        servicio.recibir(aislamiento(GALPON_ID, LOTE_ID));

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.AISLAMIENTO,
                OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
    }

    @Test
    void laAlertaNoSeGuardaNiSeModificaNadaDelGalponDirectamente() {
        galponEn(EstadoGalpon.PRODUCTIVO);

        servicio.recibir(aislamiento(GALPON_ID, null));

        verify(galpones, never()).save(any());
        verify(lotes, never()).save(any());
    }

    // ------------------------------------------------------- campos obligatorios

    @Test
    void alertaSinIdAccionOFechaEsIncompleta() {
        assertRechazo(new Comando(null, "AISLAMIENTO", GALPON_ID, null, HOY, ENFERMEDAD, DESCRIPCION, null),
                ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, null, GALPON_ID, null, HOY, ENFERMEDAD, DESCRIPCION, null),
                ValidacionNegocioException.class, "ALERTA_INCOMPLETA");
        assertRechazo(new Comando(ALERTA, "AISLAMIENTO", GALPON_ID, null, null, ENFERMEDAD, DESCRIPCION, null),
                ValidacionNegocioException.class, "ALERTA_INCOMPLETA");

        verifyNoInteractions(galpones, lotes, estados);
    }

    @ParameterizedTest(name = "accion [{0}] es inválida")
    @ValueSource(strings = {"CUARENTENA", "aislamiento", ""})
    void accionDistintaDeAislamientoOReanudacionEsInvalida(String accion) {
        assertRechazo(new Comando(ALERTA, accion, GALPON_ID, null, HOY, ENFERMEDAD, DESCRIPCION, null),
                ValidacionNegocioException.class, "ACCION_INVALIDA");

        verifyNoInteractions(galpones, lotes, estados);
    }

    @Test
    void sinGalponNiLoteSeRechaza() {
        assertRechazo(aislamiento(null, null), ValidacionNegocioException.class, "GALPON_O_LOTE_REQUERIDO");

        verifyNoInteractions(galpones, lotes, estados);
    }

    @ParameterizedTest(name = "tipoEnfermedad [{0}] en aislamiento")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aislamientoSinTipoDeEnfermedadSeRechaza(String tipo) {
        assertRechazo(new Comando(ALERTA, "AISLAMIENTO", GALPON_ID, null, HOY, tipo, DESCRIPCION, null),
                ValidacionNegocioException.class, "DATOS_AISLAMIENTO_INCOMPLETOS");

        verifyNoInteractions(galpones, lotes, estados);
    }

    @ParameterizedTest(name = "descripcion [{0}] en aislamiento")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aislamientoSinDescripcionSeRechaza(String descripcion) {
        assertRechazo(new Comando(ALERTA, "AISLAMIENTO", GALPON_ID, null, HOY, ENFERMEDAD, descripcion, null),
                ValidacionNegocioException.class, "DATOS_AISLAMIENTO_INCOMPLETOS");

        verifyNoInteractions(galpones, lotes, estados);
    }

    // -------------------------------------------------------------------- fecha

    @ParameterizedTest(name = "fechaHora {0} no es del día actual")
    @ValueSource(strings = {"2026-10-04T09:15:00-05:00", "2026-10-06T09:15:00-05:00", "2026-10-06T00:30:00-05:00",
            "2026-10-06T05:30:00Z"})
    void unaFechaDeOtroDiaSeRechazaSinTocarNada(String fechaHora) {
        Comando comando = new Comando(ALERTA, "AISLAMIENTO", GALPON_ID, null, OffsetDateTime.parse(fechaHora),
                ENFERMEDAD, DESCRIPCION, null);

        assertRechazo(comando, ValidacionNegocioException.class, "FECHA_INVALIDA");

        verifyNoInteractions(galpones, lotes, estados);
    }

    @ParameterizedTest(name = "fechaHora {0} sí es del día actual")
    @ValueSource(strings = {"2026-10-05T00:00:00-05:00", "2026-10-05T23:59:00-05:00", "2026-10-06T03:00:00Z"})
    void cualquierHoraDelDiaActualEnBogotaSeAcepta(String fechaHora) {
        galponEn(EstadoGalpon.PRODUCTIVO);
        Comando comando = new Comando(ALERTA, "AISLAMIENTO", GALPON_ID, null, OffsetDateTime.parse(fechaHora),
                ENFERMEDAD, DESCRIPCION, null);

        servicio.recibir(comando);

        verify(estados).solicitarCambio(GALPON_ID, EstadoGalpon.PRODUCTIVO, EstadoGalpon.AISLAMIENTO,
                OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
    }

    // ------------------------------------------------- galpón / lote inexistentes

    @Test
    void galponInexistenteSeRechaza() {
        when(galpones.findById(GALPON_ID)).thenReturn(Optional.empty());

        assertRechazo(aislamiento(GALPON_ID, null), RecursoNoEncontradoException.class, "GALPON_NO_ENCONTRADO");

        verifyNoInteractions(estados);
    }

    @Test
    void loteInexistenteSeRechaza() {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.empty());

        assertRechazo(aislamiento(null, LOTE_ID), RecursoNoEncontradoException.class, "LOTE_NO_ENCONTRADO");

        verifyNoInteractions(estados);
    }

    @Test
    void loteHistoricoOSinActivarSeRechazaComoNoActivo() {
        when(lotes.findById(LOTE_ID)).thenReturn(
                Optional.of(lote(GALPON_ID, Instant.parse("2026-09-20T13:15:00Z"))));

        assertRechazo(aislamiento(null, LOTE_ID), ValidacionNegocioException.class, "LOTE_NO_ACTIVO");

        verifyNoInteractions(estados);
        verify(galpones, never()).findById(any());
    }

    @Test
    void loteDeOtroGalponSeRechaza() {
        when(lotes.findById(LOTE_ID)).thenReturn(Optional.of(lote(UUID.randomUUID(), null)));

        assertRechazo(aislamiento(GALPON_ID, LOTE_ID), ValidacionNegocioException.class,
                "LOTE_NO_PERTENECE_AL_GALPON");

        verifyNoInteractions(estados);
    }

    // ------------------------------------------------------------ acción vs estado

    @Test
    void reentregaDeAislamientoSobreUnGalponYaAisladoEsAccionIncompatible() {
        galponEn(EstadoGalpon.AISLAMIENTO);

        assertRechazo(aislamiento(GALPON_ID, null), ConflictoException.class, "ACCION_INCOMPATIBLE");

        verifyNoInteractions(estados);
    }

    @Test
    void aislamientoSobreUnGalponQueNoEstaProductivoEsAccionIncompatible() {
        galponEn(EstadoGalpon.DISPONIBLE);

        assertRechazo(aislamiento(GALPON_ID, null), ConflictoException.class, "ACCION_INCOMPATIBLE");

        verifyNoInteractions(estados);
    }

    @Test
    void reanudacionSobreUnGalponQueNoEstaEnAislamientoEsAccionIncompatible() {
        galponEn(EstadoGalpon.PRODUCTIVO);

        assertRechazo(reanudacion(), ConflictoException.class, "ACCION_INCOMPATIBLE");

        verifyNoInteractions(estados);
    }

    @Test
    void siElEstadoCambioEntreLaLecturaYLaSolicitudSeRechazaComoAccionIncompatible() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(
                new ConflictoException("ESTADO_ESPERADO_CAMBIADO", "El estado del galpón cambió."));

        assertRechazo(aislamiento(GALPON_ID, null), ConflictoException.class, "ACCION_INCOMPATIBLE");
    }

    @Test
    void otroRechazoDeGalponEstadoServiceSePropagaTalCual() {
        galponEn(EstadoGalpon.PRODUCTIVO);
        ConflictoException falla = new ConflictoException("TRANSICION_NO_PERMITIDA", "No permitida.");
        when(estados.solicitarCambio(any(), any(), any(), any())).thenThrow(falla);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.recibir(aislamiento(GALPON_ID, null)))
                .isSameAs(falla);
    }
}
