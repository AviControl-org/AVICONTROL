package com.unimag.avicontrol.infrastructure.out.persistence.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.mantenimiento.AlertaMantenimientoRepository;
import com.unimag.avicontrol.soporte.PersistenciaTestBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * US8 sobre PostgreSQL real: el índice único parcial (una sola alerta PENDIENTE por galpón) y su traducción a
 * ALERTA_ACTIVA_EXISTENTE, las consultas de existencia y el listado paginado.
 */
class AlertaMantenimientoPersistenceTest extends PersistenciaTestBase {

    @Autowired
    private AlertaMantenimientoRepository alertas;

    @Autowired
    private GalponRepository galpones;

    @Autowired
    private JdbcTemplate jdbc;

    private Galpon galpon(String nombre) {
        return galpones.save(Galpon.nuevo(NombreNormalizado.de(nombre), 1000));
    }

    private AlertaMantenimiento pendiente(UUID galponId, Instant fechaHora) {
        return new AlertaMantenimiento(UUID.randomUUID(), galponId, "Reparar ventilación", fechaHora, "tecnico-01",
                EstadoAlertaMantenimiento.PENDIENTE, "Alta", "Ventilación");
    }

    private AlertaMantenimiento atendida(UUID galponId, Instant fechaHora) {
        return pendiente(galponId, fechaHora).atendida();
    }

    @Test
    void guardaLaAlertaYLaLeeConSusCamposOpcionales() {
        Galpon g = galpon("Galpon Norte");
        AlertaMantenimiento guardada = alertas.save(new AlertaMantenimiento(UUID.randomUUID(), g.id(), "Revisar",
                AHORA, "tecnico-02", EstadoAlertaMantenimiento.PENDIENTE, null, null));

        assertThat(alertas.findById(guardada.id())).hasValueSatisfying(a -> {
            assertThat(a.galponId()).isEqualTo(g.id());
            assertThat(a.descripcion()).isEqualTo("Revisar");
            assertThat(a.fechaHora()).isEqualTo(AHORA);
            assertThat(a.tecnicoId()).isEqualTo("tecnico-02");
            assertThat(a.estado()).isEqualTo(EstadoAlertaMantenimiento.PENDIENTE);
            assertThat(a.severidad()).isNull();
            assertThat(a.tipo()).isNull();
        });
    }

    @Test
    void existsPendienteSoloCuentaLasPendientesDelGalpon() {
        Galpon con = galpon("Galpon Norte");
        Galpon sin = galpon("Galpon Sur");
        alertas.save(pendiente(con.id(), AHORA));

        assertThat(alertas.existsPendienteByGalponId(con.id())).isTrue();
        assertThat(alertas.existsPendienteByGalponId(sin.id())).isFalse();
    }

    @Test
    void unaAlertaAtendidaYaNoCuentaComoPendiente() {
        Galpon g = galpon("Galpon Norte");
        AlertaMantenimiento alerta = alertas.save(pendiente(g.id(), AHORA));

        alertas.save(alerta.atendida());

        assertThat(alertas.existsPendienteByGalponId(g.id())).isFalse();
        assertThat(alertas.findById(alerta.id())).hasValueSatisfying(a ->
                assertThat(a.estado()).isEqualTo(EstadoAlertaMantenimiento.ATENDIDA));
    }

    @Test
    void elIndiceRechazaUnaSegundaPendienteDelMismoGalpon() {
        Galpon g = galpon("Galpon Norte");
        alertas.save(pendiente(g.id(), AHORA));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> alertas.save(pendiente(g.id(), AHORA.plusSeconds(60))))
                .satisfies(e -> assertThat(e.codigo()).isEqualTo("ALERTA_ACTIVA_EXISTENTE"));
    }

    @Test
    void elIndicePermiteUnaPendienteJuntoAVariasAtendidasDelMismoGalpon() {
        Galpon g = galpon("Galpon Norte");
        alertas.save(atendida(g.id(), AHORA.minusSeconds(120)));
        alertas.save(atendida(g.id(), AHORA.minusSeconds(60)));

        alertas.save(pendiente(g.id(), AHORA));

        assertThat(alertas.existsPendienteByGalponId(g.id())).isTrue();
    }

    @Test
    void galponesDistintosPuedenTenerCadaUnoSuPendiente() {
        alertas.save(pendiente(galpon("Galpon Norte").id(), AHORA));

        alertas.save(pendiente(galpon("Galpon Sur").id(), AHORA));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void conDosPeticionesSimultaneasSoloUnaAlertaPendienteQuedaGuardada() throws Exception {
        Galpon g = galpon("Galpon Concurrente");
        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    try {
                        alertas.save(pendiente(g.id(), AHORA));
                        return true;
                    } catch (ConflictoException e) {
                        return "ALERTA_ACTIVA_EXISTENTE".equals(e.codigo()) ? false : null;
                    }
                }));
            }
            salida.countDown();

            int guardadas = 0;
            int rechazadas = 0;
            for (Future<Boolean> r : resultados) {
                Boolean ok = r.get();
                assertThat(ok).as("solo se espera guardar o ALERTA_ACTIVA_EXISTENTE").isNotNull();
                if (ok) {
                    guardadas++;
                } else {
                    rechazadas++;
                }
            }

            assertThat(guardadas).isEqualTo(1);
            assertThat(rechazadas).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from alerta_mantenimiento where galpon_id = ? and estado = 'PENDIENTE'",
                    Integer.class, g.id())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from alerta_mantenimiento where galpon_id = ?", g.id());
            jdbc.update("delete from galpon where id = ?", g.id());
        }
    }

    // ---------------------------------------------------------------- search

    @Test
    void searchFiltraPorEstadoYOrdenaDeLaMasRecienteALaMasAntigua() {
        Galpon g = galpon("Galpon Norte");
        AlertaMantenimiento antigua = alertas.save(atendida(g.id(), AHORA.minusSeconds(300)));
        AlertaMantenimiento reciente = alertas.save(atendida(g.id(), AHORA.minusSeconds(60)));
        AlertaMantenimiento intermedia = alertas.save(atendida(g.id(), AHORA.minusSeconds(120)));
        alertas.save(pendiente(g.id(), AHORA));

        Pagina<AlertaMantenimiento> atendidas = alertas.search(EstadoAlertaMantenimiento.ATENDIDA, null, 1, 20);

        assertThat(atendidas.contenido()).extracting(AlertaMantenimiento::id)
                .containsExactly(reciente.id(), intermedia.id(), antigua.id());
        assertThat(atendidas.totalElementos()).isEqualTo(3);
        assertThat(alertas.search(EstadoAlertaMantenimiento.PENDIENTE, null, 1, 20).contenido()).hasSize(1);
    }

    @Test
    void searchFiltraPorGalpon() {
        Galpon norte = galpon("Galpon Norte");
        Galpon sur = galpon("Galpon Sur");
        AlertaMantenimiento deNorte = alertas.save(pendiente(norte.id(), AHORA));
        alertas.save(pendiente(sur.id(), AHORA));

        Pagina<AlertaMantenimiento> pagina =
                alertas.search(EstadoAlertaMantenimiento.PENDIENTE, norte.id(), 1, 20);

        assertThat(pagina.contenido()).extracting(AlertaMantenimiento::id).containsExactly(deNorte.id());
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void searchPaginaSinRepetirNiOmitirAlertas() {
        Galpon g = galpon("Galpon Norte");
        List<UUID> esperado = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            esperado.add(0, alertas.save(atendida(g.id(), AHORA.minusSeconds(600L - i * 60L))).id());
        }

        Pagina<AlertaMantenimiento> primera = alertas.search(EstadoAlertaMantenimiento.ATENDIDA, g.id(), 1, 2);
        Pagina<AlertaMantenimiento> segunda = alertas.search(EstadoAlertaMantenimiento.ATENDIDA, g.id(), 2, 2);
        Pagina<AlertaMantenimiento> tercera = alertas.search(EstadoAlertaMantenimiento.ATENDIDA, g.id(), 3, 2);

        assertThat(primera.contenido()).extracting(AlertaMantenimiento::id).containsExactlyElementsOf(esperado.subList(0, 2));
        assertThat(segunda.contenido()).extracting(AlertaMantenimiento::id).containsExactlyElementsOf(esperado.subList(2, 4));
        assertThat(tercera.contenido()).extracting(AlertaMantenimiento::id).containsExactly(esperado.get(4));
        assertThat(primera.totalElementos()).isEqualTo(5);
        assertThat(primera.totalPaginas()).isEqualTo(3);
        assertThat(primera.pagina()).isEqualTo(1);
        assertThat(primera.tamanio()).isEqualTo(2);
    }

    @Test
    void searchSinCoincidenciasDevuelvePaginaVacia() {
        Pagina<AlertaMantenimiento> pagina =
                alertas.search(EstadoAlertaMantenimiento.PENDIENTE, UUID.randomUUID(), 1, 20);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
    }
}
