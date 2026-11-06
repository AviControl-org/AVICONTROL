package com.unimag.avicontrol.application.galpon;

import static com.unimag.avicontrol.domain.model.galpon.EstadoGalpon.*;
import static com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.policy.galpon.TransicionesPermitidas;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransicionesPermitidasTest {

    /** Las 9 transiciones de la matriz con su único origen autorizado. */
    static Stream<Arguments> validas() {
        return Stream.of(
                Arguments.of(DISPONIBLE, PRODUCTIVO, REGISTRO_LOTE),
                Arguments.of(DISPONIBLE, MANTENIMIENTO, ADMINISTRADOR),
                Arguments.of(PRODUCTIVO, EN_COSECHA, ADMINISTRADOR),
                Arguments.of(PRODUCTIVO, AISLAMIENTO, ALERTA_SANITARIA_MODULO_2),
                Arguments.of(EN_COSECHA, VACIADO_SANITARIO, ALERTA_VACIADO_SANITARIO),
                Arguments.of(VACIADO_SANITARIO, DISPONIBLE, PROCESO_AUTOMATICO),
                Arguments.of(MANTENIMIENTO, DISPONIBLE, ADMINISTRADOR),
                Arguments.of(AISLAMIENTO, PRODUCTIVO, ALERTA_SANITARIA_MODULO_2),
                Arguments.of(AISLAMIENTO, EN_COSECHA, ADMINISTRADOR));
    }

    /** Todos los pares (desde, hacia) que no están en la matriz, incluido quedarse en el mismo estado. */
    static Stream<Arguments> invalidas() {
        List<EstadoGalpon[]> validas = validas()
                .map(a -> new EstadoGalpon[] {(EstadoGalpon) a.get()[0], (EstadoGalpon) a.get()[1]}).toList();
        List<Arguments> resultado = new ArrayList<>();
        for (EstadoGalpon desde : EstadoGalpon.values()) {
            for (EstadoGalpon hacia : EstadoGalpon.values()) {
                boolean esValida = validas.stream().anyMatch(v -> v[0] == desde && v[1] == hacia);
                if (!esValida) {
                    resultado.add(Arguments.of(desde, hacia));
                }
            }
        }
        return resultado.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} con origen {2} es válida")
    @MethodSource("validas")
    void transicionValidaConSuOrigenNoLanza(EstadoGalpon desde, EstadoGalpon hacia, OrigenCambioEstado origen) {
        assertThat(TransicionesPermitidas.existe(desde, hacia)).isTrue();
        assertThatCode(() -> TransicionesPermitidas.validar(desde, hacia, origen)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} rechaza cualquier origen distinto de {2}")
    @MethodSource("validas")
    void transicionValidaConOtroOrigenEsOrigenNoAutorizado(EstadoGalpon desde, EstadoGalpon hacia,
                                                            OrigenCambioEstado autorizado) {
        for (OrigenCambioEstado origen : OrigenCambioEstado.values()) {
            if (origen == autorizado) {
                continue;
            }
            assertThatExceptionOfType(ConflictoException.class)
                    .as("origen %s en %s -> %s", origen, desde, hacia)
                    .isThrownBy(() -> TransicionesPermitidas.validar(desde, hacia, origen))
                    .satisfies(e -> assertThat(e.codigo()).isEqualTo("ORIGEN_NO_AUTORIZADO"));
        }
    }

    @ParameterizedTest(name = "{0} -> {1} no está en la matriz")
    @MethodSource("invalidas")
    void transicionFueraDeLaMatrizEsTransicionNoPermitida(EstadoGalpon desde, EstadoGalpon hacia) {
        assertThat(TransicionesPermitidas.existe(desde, hacia)).isFalse();
        for (OrigenCambioEstado origen : OrigenCambioEstado.values()) {
            assertThatExceptionOfType(ConflictoException.class)
                    .isThrownBy(() -> TransicionesPermitidas.validar(desde, hacia, origen))
                    .satisfies(e -> assertThat(e.codigo()).isEqualTo("TRANSICION_NO_PERMITIDA"));
        }
    }

    @Test
    void laMatrizTieneExactamenteNueveTransiciones() {
        assertThat(validas().count()).isEqualTo(9);
        assertThat(invalidas().count()).isEqualTo(36 - 9);
    }

    @Test
    void aislamientoNoPasaDirectoAVaciadoSanitario() {
        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> TransicionesPermitidas.validar(AISLAMIENTO, VACIADO_SANITARIO, ADMINISTRADOR))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("TRANSICION_NO_PERMITIDA"));
    }

    @Test
    void elAdministradorNoPuedePasarDisponibleAProductivoAMano() {
        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> TransicionesPermitidas.validar(DISPONIBLE, PRODUCTIVO, ADMINISTRADOR))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("ORIGEN_NO_AUTORIZADO");
                    assertThat(e.getMessage())
                            .isEqualTo("El administrador no puede solicitar esa transición por este servicio.");
                });
    }

    @Test
    void elMensajeDeOrigenNoAutorizadoDependeDelOrigen() {
        assertThat(TransicionesPermitidas.origenNoAutorizado(ADMINISTRADOR).getMessage())
                .isEqualTo("El administrador no puede solicitar esa transición por este servicio.");
        assertThat(TransicionesPermitidas.origenNoAutorizado(PROCESO_AUTOMATICO).getMessage())
                .isEqualTo("El origen de la solicitud no está autorizado para esa transición.");
    }

    @Test
    void destinosManualesSonLasTresTransicionesDelAdministradorPorRest() {
        assertThat(TransicionesPermitidas.destinosManuales(PRODUCTIVO)).containsExactly(EN_COSECHA);
        assertThat(TransicionesPermitidas.destinosManuales(AISLAMIENTO)).containsExactly(EN_COSECHA);
        assertThat(TransicionesPermitidas.destinosManuales(MANTENIMIENTO)).containsExactly(DISPONIBLE);
        // DISPONIBLE -> MANTENIMIENTO es del administrador, pero solo atendiendo una alerta, no por este servicio.
        assertThat(TransicionesPermitidas.destinosManuales(DISPONIBLE)).isEmpty();
        assertThat(TransicionesPermitidas.destinosManuales(EN_COSECHA)).isEmpty();
        assertThat(TransicionesPermitidas.destinosManuales(VACIADO_SANITARIO)).isEmpty();
    }
}
