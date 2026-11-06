package com.unimag.avicontrol.soporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prueba mínima y temporal: comprueba que Docker, Testcontainers, Flyway, Hibernate y los adaptadores funcionan
 * juntos. Se elimina cuando existan las pruebas de persistencia reales (T022).
 */
class PersistenciaTestBaseTest extends PersistenciaTestBase {

    @Autowired
    private GalponRepository galpones;

    @Test
    void guardaYLeeUnGalponConElEsquemaReal() {
        Galpon guardado = galpones.save(Galpon.nuevo(NombreNormalizado.de("Galpón Norte"), 1000));

        assertThat(galpones.findById(guardado.id())).hasValueSatisfying(g -> {
            assertThat(g.nombre()).isEqualTo("Galpón Norte");
            assertThat(g.capacidad()).isEqualTo(1000);
            assertThat(g.estado()).isEqualTo(EstadoGalpon.DISPONIBLE);
        });
        assertThat(galpones.findByIdForUpdate(guardado.id())).isPresent();
        assertThat(galpones.existsByNombreClave("galpón norte")).isTrue();
        assertThat(galpones.existsByNombreClaveAndIdNot("galpón norte", guardado.id())).isFalse();
    }

    @Test
    void elIndiceUnicoSeTraduceANombreDuplicadoSinImportarMayusculas() {
        galpones.save(Galpon.nuevo(NombreNormalizado.de("Galpón Norte"), 1000));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> galpones.save(Galpon.nuevo(NombreNormalizado.de("galpón norte"), 500)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO");
                    assertThat(e.errores()).hasSize(1);
                });
    }
}
