package com.unimag.avicontrol.application.galpon;

import static com.unimag.avicontrol.domain.model.galpon.EstadoGalpon.*;
import static com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GalponEstadoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private GalponRepository galpones;
    @Mock
    private HistorialEstadoRepository historial;

    private GalponEstadoService servicio;
    private final UUID galponId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        servicio = new GalponEstadoService(galpones, historial, Clock.fixed(AHORA, ZonaNegocio.BOGOTA));
    }

    private Galpon galponEn(EstadoGalpon estado) {
        return new Galpon(galponId, "Galpón Norte", 1000, estado, 0L);
    }

    @Test
    void cambioValidoGuardaElNuevoEstadoYEscribeElHistorial() {
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(PRODUCTIVO)));
        when(historial.save(any(HistorialEstado.class))).thenAnswer(i -> i.getArgument(0));

        HistorialEstado resultado = servicio.solicitarCambio(galponId, PRODUCTIVO, EN_COSECHA, ADMINISTRADOR);

        ArgumentCaptor<Galpon> guardado = ArgumentCaptor.forClass(Galpon.class);
        verify(galpones).save(guardado.capture());
        assertThat(guardado.getValue().estado()).isEqualTo(EN_COSECHA);
        assertThat(guardado.getValue().id()).isEqualTo(galponId);

        assertThat(resultado.galponId()).isEqualTo(galponId);
        assertThat(resultado.estadoAnterior()).isEqualTo(PRODUCTIVO);
        assertThat(resultado.estadoNuevo()).isEqualTo(EN_COSECHA);
        assertThat(resultado.origen()).isEqualTo(ADMINISTRADOR);
        assertThat(resultado.fechaHora()).isEqualTo(AHORA);
        assertThat(resultado.id()).isNotNull();
        verify(historial).save(resultado);
    }

    @Test
    void leeElGalponConBloqueoYNoConLecturaSimple() {
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(DISPONIBLE)));
        when(historial.save(any(HistorialEstado.class))).thenAnswer(i -> i.getArgument(0));

        servicio.solicitarCambio(galponId, DISPONIBLE, PRODUCTIVO, REGISTRO_LOTE);

        verify(galpones).findByIdForUpdate(galponId);
        verify(galpones, never()).findById(any());
    }

    @Test
    void galponInexistenteEsRecursoNoEncontradoYNoEscribeNada() {
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.solicitarCambio(galponId, PRODUCTIVO, EN_COSECHA, ADMINISTRADOR))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("GALPON_NO_ENCONTRADO"));

        verify(galpones, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void estadoYaCambiadoEsConflictoYNoEscribeNada() {
        // El administrador veía PRODUCTIVO, pero otra operación ya lo pasó a AISLAMIENTO.
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(AISLAMIENTO)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.solicitarCambio(galponId, PRODUCTIVO, EN_COSECHA, ADMINISTRADOR))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("ESTADO_ESPERADO_CAMBIADO"));

        verify(galpones, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void transicionFueraDeLaMatrizEsConflictoYNoEscribeNada() {
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(AISLAMIENTO)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.solicitarCambio(galponId, AISLAMIENTO, VACIADO_SANITARIO, ADMINISTRADOR))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("TRANSICION_NO_PERMITIDA"));

        verify(galpones, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void origenNoAutorizadoEsConflictoYNoEscribeNada() {
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(DISPONIBLE)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.solicitarCambio(galponId, DISPONIBLE, PRODUCTIVO, ADMINISTRADOR))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("ORIGEN_NO_AUTORIZADO"));

        verify(galpones, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void elEstadoEsperadoSeValidaAntesQueLaTransicion() {
        // Estado vigente DISPONIBLE; se pide VACIADO_SANITARIO -> DISPONIBLE esperando otro estado: gana el choque de estado.
        when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(DISPONIBLE)));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.solicitarCambio(galponId, VACIADO_SANITARIO, DISPONIBLE, PROCESO_AUTOMATICO))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("ESTADO_ESPERADO_CAMBIADO"));
    }

    @Test
    void cadaOrigenAutorizadoPuedeEjecutarSuTransicion() {
        OrigenCambioEstado[] origenes = {REGISTRO_LOTE, ALERTA_SANITARIA_MODULO_2, ALERTA_VACIADO_SANITARIO,
                PROCESO_AUTOMATICO};
        EstadoGalpon[][] pasos = {{DISPONIBLE, PRODUCTIVO}, {PRODUCTIVO, AISLAMIENTO}, {EN_COSECHA, VACIADO_SANITARIO},
                {VACIADO_SANITARIO, DISPONIBLE}};
        when(historial.save(any(HistorialEstado.class))).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < origenes.length; i++) {
            when(galpones.findByIdForUpdate(galponId)).thenReturn(Optional.of(galponEn(pasos[i][0])));

            HistorialEstado h = servicio.solicitarCambio(galponId, pasos[i][0], pasos[i][1], origenes[i]);

            assertThat(h.estadoNuevo()).isEqualTo(pasos[i][1]);
            assertThat(h.origen()).isEqualTo(origenes[i]);
        }
    }
}
