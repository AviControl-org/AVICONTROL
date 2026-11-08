package com.unimag.avicontrol.infrastructure.out.persistence.galpon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones.CampoOrden;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.soporte.PersistenciaTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * US1 y US3 sobre PostgreSQL real: el índice único sobre lower(nombre) y su traducción a NOMBRE_DUPLICADO, la búsqueda,
 * el filtro, la paginación y el lote activo.
 */
class GalponPersistenceTest extends PersistenciaTestBase {

    @Autowired
    private GalponRepository galpones;

    @Autowired
    private LoteRepository lotes;

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

    // =============================================================== US3: consultas

    private Galpon guardar(String nombre, long capacidad, EstadoGalpon estado) {
        return galpones.save(nuevo(nombre, capacidad).conEstado(estado));
    }

    private static ConsultaGalpones consulta(String nombre, EstadoGalpon estado, CampoOrden campo,
                                             boolean ascendente, int pagina) {
        return new ConsultaGalpones(nombre, estado, campo, ascendente, pagina, 10);
    }

    private static List<String> nombres(Pagina<Galpon> pagina) {
        return pagina.contenido().stream().map(Galpon::nombre).toList();
    }

    @Test
    void busquedaParcialNoDistingueMayusculas() {
        guardar("Galpon Norte", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Galpon Sur", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Granja Este", 1000, EstadoGalpon.DISPONIBLE);

        assertThat(nombres(galpones.search(consulta("GALP", null, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Galpon Norte", "Galpon Sur");
        assertThat(nombres(galpones.search(consulta("norte", null, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Galpon Norte");
        assertThat(nombres(galpones.search(consulta("jA eS", null, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Granja Este");
        assertThat(galpones.search(consulta("xyz", null, CampoOrden.NOMBRE, true, 1)).totalElementos()).isZero();
    }

    @Test
    void losComodinesDeLikeEnElTextoSeTratanComoTextoLiteral() {
        guardar("Galpon 500", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Galpon 50", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Galpon_1", 1000, EstadoGalpon.DISPONIBLE);
        guardar("GalponX1", 1000, EstadoGalpon.DISPONIBLE);

        // Sin escapar, "50%" actuaría como comodín y encontraría "Galpon 500" y "Galpon 50".
        assertThat(galpones.search(consulta("50%", null, CampoOrden.NOMBRE, true, 1)).totalElementos()).isZero();
        assertThat(galpones.search(consulta("%", null, CampoOrden.NOMBRE, true, 1)).totalElementos()).isZero();
        // Sin escapar, "n_1" también encontraría "GalponX1".
        assertThat(nombres(galpones.search(consulta("n_1", null, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Galpon_1");
    }

    @Test
    void filtraPorEstadoYLoCombinaConLaBusquedaPorNombre() {
        guardar("Galpon Norte", 1000, EstadoGalpon.PRODUCTIVO);
        guardar("Galpon Sur", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Galpon Oeste", 1000, EstadoGalpon.PRODUCTIVO);

        assertThat(nombres(galpones.search(consulta(null, EstadoGalpon.PRODUCTIVO, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Galpon Norte", "Galpon Oeste");
        assertThat(nombres(galpones.search(consulta("sur", EstadoGalpon.PRODUCTIVO, CampoOrden.NOMBRE, true, 1))))
                .isEmpty();
        assertThat(galpones.search(consulta(null, EstadoGalpon.AISLAMIENTO, CampoOrden.NOMBRE, true, 1))
                .totalElementos()).isZero();
    }

    @Test
    void ordenaPorNombreSinDistinguirMayusculasYEnAmbosSentidos() {
        guardar("beta", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Alfa", 1000, EstadoGalpon.DISPONIBLE);
        guardar("Gamma", 1000, EstadoGalpon.DISPONIBLE);

        assertThat(nombres(galpones.search(consulta(null, null, CampoOrden.NOMBRE, true, 1))))
                .containsExactly("Alfa", "beta", "Gamma");
        assertThat(nombres(galpones.search(consulta(null, null, CampoOrden.NOMBRE, false, 1))))
                .containsExactly("Gamma", "beta", "Alfa");
    }

    @Test
    void ordenaPorCapacidadYDesempataSiempreConElId() {
        List<Galpon> iguales = List.of(
                guardar("A", 500, EstadoGalpon.DISPONIBLE),
                guardar("B", 500, EstadoGalpon.DISPONIBLE),
                guardar("C", 500, EstadoGalpon.DISPONIBLE));
        guardar("D", 100, EstadoGalpon.DISPONIBLE);
        guardar("E", 900, EstadoGalpon.DISPONIBLE);
        List<UUID> idsEnOrden = iguales.stream().map(Galpon::id)
                .sorted(Comparator.comparing(UUID::toString)).toList();

        List<Galpon> ascendente = galpones.search(consulta(null, null, CampoOrden.CAPACIDAD, true, 1)).contenido();

        assertThat(ascendente.get(0).nombre()).isEqualTo("D");
        assertThat(ascendente.subList(1, 4).stream().map(Galpon::id).toList()).containsExactlyElementsOf(idsEnOrden);
        assertThat(ascendente.get(4).nombre()).isEqualTo("E");
        assertThat(galpones.search(consulta(null, null, CampoOrden.CAPACIDAD, false, 1)).contenido().get(0).nombre())
                .isEqualTo("E");
    }

    @Test
    void ordenaPorEstado() {
        guardar("A", 1000, EstadoGalpon.PRODUCTIVO);
        guardar("B", 1000, EstadoGalpon.AISLAMIENTO);
        guardar("C", 1000, EstadoGalpon.DISPONIBLE);

        assertThat(galpones.search(consulta(null, null, CampoOrden.ESTADO, true, 1)).contenido())
                .extracting(Galpon::estado)
                .containsExactly(EstadoGalpon.AISLAMIENTO, EstadoGalpon.DISPONIBLE, EstadoGalpon.PRODUCTIVO);
    }

    @Test
    void paginaDeDiezEnDiezSinRepetirNiOmitirGalpones() {
        for (int i = 1; i <= 15; i++) {
            guardar(String.format("Galpon %02d", i), 1000, EstadoGalpon.DISPONIBLE);
        }

        Pagina<Galpon> primera = galpones.search(consulta(null, null, CampoOrden.NOMBRE, true, 1));
        Pagina<Galpon> segunda = galpones.search(consulta(null, null, CampoOrden.NOMBRE, true, 2));
        Pagina<Galpon> tercera = galpones.search(consulta(null, null, CampoOrden.NOMBRE, true, 3));

        assertThat(primera.contenido()).hasSize(10);
        assertThat(segunda.contenido()).hasSize(5);
        assertThat(tercera.contenido()).isEmpty();
        assertThat(primera.totalElementos()).isEqualTo(15);
        assertThat(primera.totalPaginas()).isEqualTo(2);
        Set<UUID> ids = new HashSet<>();
        primera.contenido().forEach(g -> ids.add(g.id()));
        segunda.contenido().forEach(g -> ids.add(g.id()));
        assertThat(ids).hasSize(15);
        assertThat(primera.contenido().get(0).nombre()).isEqualTo("Galpon 01");
        assertThat(segunda.contenido().get(4).nombre()).isEqualTo("Galpon 15");
    }

    @Test
    void elLoteActivoEsElQueNoTieneDesvinculadoEnYElHistorialVaDelMasRecienteAlMasAntiguo() {
        Galpon galpon = guardar("Galpon Norte", 1500, EstadoGalpon.PRODUCTIVO);
        Galpon sinLotes = guardar("Galpon Sur", 2000, EstadoGalpon.DISPONIBLE);
        Lote viejo = lotes.save(Lote.nuevo(galpon.id(), NombreNormalizado.de("Lote A1"), LocalDate.of(2026, 7, 1),
                1000, 5_000_000).desvincular(Instant.parse("2026-08-21T15:00:00Z")));
        Lote medio = lotes.save(Lote.nuevo(galpon.id(), NombreNormalizado.de("Lote A2"), LocalDate.of(2026, 8, 25),
                900, 4_000_000).desvincular(Instant.parse("2026-09-20T15:00:00Z")));
        Lote activo = lotes.save(Lote.nuevo(galpon.id(), NombreNormalizado.de("Lote B2"), LocalDate.of(2026, 9, 25),
                1200, 6_000_000));

        assertThat(lotes.findActivoByGalponId(galpon.id())).hasValueSatisfying(l -> {
            assertThat(l.id()).isEqualTo(activo.id());
            assertThat(l.activo()).isTrue();
        });
        assertThat(lotes.findByGalponIdOrderByFechaIngresoDesc(galpon.id()))
                .extracting(Lote::id).containsExactly(activo.id(), medio.id(), viejo.id());
        assertThat(lotes.findByGalponIdOrderByFechaIngresoDesc(galpon.id()).get(2).desvinculadoEn())
                .isEqualTo(Instant.parse("2026-08-21T15:00:00Z"));
        assertThat(lotes.existsByGalponId(galpon.id())).isTrue();
        assertThat(lotes.findActivoByGalponId(sinLotes.id())).isEmpty();
        assertThat(lotes.existsByGalponId(sinLotes.id())).isFalse();
        assertThat(lotes.findByGalponIdOrderByFechaIngresoDesc(sinLotes.id())).isEmpty();
    }

    @Test
    void unGalponSoloConLotesDesvinculadosNoTieneLoteActivoPeroSiTieneLotes() {
        Galpon galpon = guardar("Galpon Norte", 1500, EstadoGalpon.DISPONIBLE);
        lotes.save(Lote.nuevo(galpon.id(), NombreNormalizado.de("Lote A1"), LocalDate.of(2026, 7, 1),
                1000, 5_000_000).desvincular(Instant.parse("2026-08-21T15:00:00Z")));

        assertThat(lotes.findActivoByGalponId(galpon.id())).isEmpty();
        assertThat(lotes.existsByGalponId(galpon.id())).isTrue();
    }
}
