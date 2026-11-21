package com.unimag.avicontrol.domain.model.lote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-012 / P-12: el día de ingreso cuenta como día 1. */
class LoteEdadTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    private static Lote loteIngresadoEl(LocalDate fecha) {
        return new Lote(UUID.randomUUID(), UUID.randomUUID(), "Lote A1", fecha, 1000, 1000, 5_000_000, null, 0L);
    }

    @Test
    void elDiaDeIngresoEsElDiaUno() {
        assertThat(loteIngresadoEl(HOY).edadEn(HOY)).isEqualTo(1);
    }

    @Test
    void ingresadoHaceVeinteDiasTieneVeintiunDias() {
        assertThat(loteIngresadoEl(HOY.minusDays(20)).edadEn(HOY)).isEqualTo(21);
    }

    @Test
    void ingresadoAyerTieneDosDias() {
        assertThat(loteIngresadoEl(HOY.minusDays(1)).edadEn(HOY)).isEqualTo(2);
    }

    @Test
    void cuentaCorrectamenteAtravesandoMesesYAnios() {
        assertThat(loteIngresadoEl(LocalDate.of(2025, 12, 31)).edadEn(LocalDate.of(2026, 1, 2))).isEqualTo(3);
    }

    @Test
    void loteSinDesvincularEstaActivoYUnoDesvinculadoNo() {
        Lote activo = loteIngresadoEl(HOY);

        assertThat(activo.activo()).isTrue();
        assertThat(activo.desvincular(Instant.parse("2026-11-05T15:00:00Z")).activo()).isFalse();
    }
}
