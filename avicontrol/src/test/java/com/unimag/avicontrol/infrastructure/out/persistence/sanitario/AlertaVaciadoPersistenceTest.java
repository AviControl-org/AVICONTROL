package com.unimag.avicontrol.infrastructure.out.persistence.sanitario;

import static org.assertj.core.api.Assertions.assertThat;

import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.AlertaVaciadoSanitarioRepository;
import com.unimag.avicontrol.soporte.PersistenciaTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * US7 sobre PostgreSQL real: la consulta que usa el proceso automático para saber qué galpones cumplieron el período
 * de vaciado sanitario (escenarios 14 y 15 del plan).
 */
class AlertaVaciadoPersistenceTest extends PersistenciaTestBase {

    /** Período configurado de la prueba; el límite que recibe la consulta es AHORA menos estos días. */
    private static final Duration PERIODO = Duration.ofDays(3);
    private static final Instant LIMITE = AHORA.minus(PERIODO);

    @Autowired
    private AlertaVaciadoSanitarioRepository alertas;

    @Autowired
    private GalponRepository galpones;

    @Autowired
    private LoteRepository lotes;

    private int contador;

    private Galpon galponEn(EstadoGalpon estado) {
        contador++;
        return galpones.save(Galpon.nuevo(NombreNormalizado.de("Galpon " + contador), 1000).conEstado(estado));
    }

    private Lote loteDe(Galpon galpon) {
        return lotes.save(Lote.nuevo(galpon.id(), NombreNormalizado.de("Lote " + galpon.nombre()),
                LocalDate.of(2026, 9, 1), 500, 1_000_000));
    }

    private void alertaRecibida(Galpon galpon, Lote lote, Instant recepcion) {
        alertas.save(new AlertaVaciadoSanitario(UUID.randomUUID(), galpon.id(), lote.id(),
                recepcion.minusSeconds(60), recepcion, "ACEPTADA"));
    }

    @Test
    void incluyeElGalponCuandoLaAlertaSeRecibioHaceMasDelPeriodo() {
        Galpon g = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        alertaRecibida(g, loteDe(g), AHORA.minus(Duration.ofDays(4)));

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).containsExactly(g.id());
    }

    @Test
    void incluyeElGalponCuandoElPeriodoSeCumpleJustoEnElLimite() {
        Galpon g = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        alertaRecibida(g, loteDe(g), LIMITE);

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).containsExactly(g.id());
    }

    @Test
    void noIncluyeElGalponSiElPeriodoAunNoSeCumple() {
        Galpon g = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        alertaRecibida(g, loteDe(g), LIMITE.plusSeconds(1));

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).isEmpty();
    }

    @Test
    void noIncluyeUnGalponQueYaNoEstaEnVaciadoSanitario() {
        Galpon g = galponEn(EstadoGalpon.DISPONIBLE);
        alertaRecibida(g, loteDe(g), AHORA.minus(Duration.ofDays(10)));

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).isEmpty();
    }

    @Test
    void comparaConLaUltimaAlertaDelGalponSiPasoPorVariosVaciados() {
        Galpon g = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        Lote lote = loteDe(g);
        alertaRecibida(g, lote, AHORA.minus(Duration.ofDays(30)));
        alertaRecibida(g, lote, AHORA.minus(Duration.ofDays(1)));

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).isEmpty();
    }

    @Test
    void devuelveSoloLosGalponesCumplidosCuandoHayVarios() {
        Galpon cumplido = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        alertaRecibida(cumplido, loteDe(cumplido), AHORA.minus(Duration.ofDays(5)));
        Galpon enCurso = galponEn(EstadoGalpon.VACIADO_SANITARIO);
        alertaRecibida(enCurso, loteDe(enCurso), AHORA.minus(Duration.ofDays(1)));

        assertThat(alertas.findGalponIdsConVaciadoCumplido(LIMITE)).containsExactly(cumplido.id());
    }
}
