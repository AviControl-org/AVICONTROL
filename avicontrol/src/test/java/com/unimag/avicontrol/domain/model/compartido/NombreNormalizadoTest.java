package com.unimag.avicontrol.domain.model.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import org.junit.jupiter.api.Test;

class NombreNormalizadoTest {

    @Test
    void nombreVacioEsInvalido() {
        assertThatThrownBy(() -> NombreNormalizado.de(""))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "NOMBRE_INVALIDO");
    }

    @Test
    void nombreNuloEsInvalido() {
        assertThatThrownBy(() -> NombreNormalizado.de(null))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "NOMBRE_INVALIDO");
    }

    @Test
    void nombreSoloEspaciosEsInvalido() {
        assertThatThrownBy(() -> NombreNormalizado.de("   "))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "NOMBRE_INVALIDO");
    }

    @Test
    void nombreDe101CaracteresEsInvalido() {
        assertThatThrownBy(() -> NombreNormalizado.de("a".repeat(101)))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "NOMBRE_INVALIDO");
    }

    @Test
    void nombreDe100CaracteresEsValido() {
        assertThat(NombreNormalizado.de("a".repeat(100)).valor()).hasSize(100);
    }

    @Test
    void caracteresNoPermitidosSeRechazan() {
        assertThatThrownBy(() -> NombreNormalizado.de("Galpón #1"))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "NOMBRE_INVALIDO");
    }

    @Test
    void permiteLetrasNumerosEspaciosGuionesYGuionesBajos() {
        assertThat(NombreNormalizado.de("Galpón Norte-2_B").valor()).isEqualTo("Galpón Norte-2_B");
    }

    @Test
    void recortaLosEspaciosDeLosExtremos() {
        assertThat(NombreNormalizado.de("  Galpón A  ").valor()).isEqualTo("Galpón A");
    }

    @Test
    void mayusculasYEspaciosExternosDanLaMismaClave() {
        assertThat(NombreNormalizado.de("Galpón A").clave()).isEqualTo(NombreNormalizado.de(" galpón a ").clave());
    }
}
