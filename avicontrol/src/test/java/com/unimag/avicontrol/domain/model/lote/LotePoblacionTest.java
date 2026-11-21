package com.unimag.avicontrol.domain.model.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LotePoblacionTest {

    private static Lote lote(long inicial, long actual) {
        return new Lote(UUID.randomUUID(), UUID.randomUUID(), "Lote A1", LocalDate.of(2026, 9, 15), inicial, actual,
                5_000_000, null, 3L);
    }

    @Test
    void descontarRestaDeLaPoblacionActualYDejaIntactaLaInicial() {
        Lote lote = lote(1000, 1000);

        Lote resultado = lote.descontar(25);

        assertThat(resultado.poblacionActual()).isEqualTo(975);
        assertThat(resultado.poblacionInicial()).isEqualTo(1000);
        assertThat(lote.poblacionActual()).isEqualTo(1000);
    }

    @Test
    void descontarNoTocaLosDemasAtributos() {
        Lote lote = lote(1000, 1000);

        Lote r = lote.descontar(25);

        assertThat(r).usingRecursiveComparison().ignoringFields("poblacionActual").isEqualTo(lote);
    }

    @Test
    void descuentosSucesivosSeAcumulan() {
        assertThat(lote(1000, 1000).descontar(25).descontar(10).poblacionActual()).isEqualTo(965);
    }

    @Test
    void descontarTodaLaPoblacionDejaCeroYNuncaNegativo() {
        Lote r = lote(1000, 40).descontar(40);

        assertThat(r.poblacionActual()).isZero();
        assertThat(r.poblacionInicial()).isEqualTo(1000);
    }

    @Test
    void descontarMasQueLaPoblacionActualLanzaExcepcionDeNegocio() {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lote(1000, 40).descontar(41))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("CANTIDAD_EXCEDE_POBLACION"));
    }

    @ParameterizedTest(name = "cantidad {0} es inválida")
    @ValueSource(longs = {0, -1, -25})
    void cantidadCeroONegativaSeRechaza(long cantidad) {
        assertThatExceptionOfType(ValidacionNegocioException.class)
                .isThrownBy(() -> lote(1000, 1000).descontar(cantidad))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("CANTIDAD_INVALIDA"));
    }
}
