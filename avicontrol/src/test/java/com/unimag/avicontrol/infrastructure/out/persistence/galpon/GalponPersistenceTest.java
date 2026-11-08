package com.unimag.avicontrol.infrastructure.out.persistence.galpon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.soporte.PersistenciaTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** US1 sobre PostgreSQL real: el índice único sobre lower(nombre) y su traducción a NOMBRE_DUPLICADO. */
class GalponPersistenceTest extends PersistenciaTestBase {

    @Autowired
    private GalponRepository galpones;

    private Galpon nuevo(String nombre, long capacidad) {
        return Galpon.nuevo(NombreNormalizado.de(nombre), capacidad);
    }

    @Test
    void guardaElGalponNuevoDisponibleYLoPuedeLeer() {
        Galpon guardado = galpones.save(nuevo("Galpon Norte", 1500));

        assertThat(galpones.findById(guardado.id())).hasValueSatisfying(g -> {
            assertThat(g.nombre()).isEqualTo("Galpon Norte");
            assertThat(g.capacidad()).isEqualTo(1500);
            assertThat(g.estado()).isEqualTo(EstadoGalpon.DISPONIBLE);
        });
    }

    @Test
    void elIndiceRechazaUnNombreQueSoloCambiaDeMayusculas() {
        galpones.save(nuevo("Galpon Norte", 1500));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> galpones.save(nuevo("galpon norte", 800)))
                .satisfies(e -> {
                    assertThat(e.codigo()).isEqualTo("NOMBRE_DUPLICADO");
                    assertThat(e.errores()).singleElement().satisfies(campo -> {
                        assertThat(campo.campo()).isEqualTo("nombre");
                        assertThat(campo.codigo()).isEqualTo("DUPLICADO");
                    });
                });
    }

    @Test
    void laBusquedaPorClaveNoDistingueMayusculas() {
        galpones.save(nuevo("Galpon Norte", 1500));

        assertThat(galpones.existsByNombreClave("galpon norte")).isTrue();
        assertThat(galpones.existsByNombreClave("galpon sur")).isFalse();
    }

    @Test
    void nombresDistintosConviven() {
        galpones.save(nuevo("Galpon Norte", 1500));

        assertThat(galpones.save(nuevo("Galpon Sur", 800)).id()).isNotNull();
    }
}
