package com.unimag.avicontrol.application.usecase.sanitario;

import com.unimag.avicontrol.application.port.in.sanitario.FinalizarVaciadoSanitarioUseCase;
import com.unimag.avicontrol.application.port.in.sanitario.RecibirVaciadoSanitarioUseCase;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.policy.compartido.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.domain.port.out.sanitario.AlertaVaciadoSanitarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaciadoSanitarioService implements RecibirVaciadoSanitarioUseCase, FinalizarVaciadoSanitarioUseCase {

    private static final Logger log = LoggerFactory.getLogger(VaciadoSanitarioService.class);
    private static final String GALPON_NO_EN_COSECHA = "El galpón no está en estado EN_COSECHA.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final AlertaVaciadoSanitarioRepository alertas;
    private final GalponEstadoService estados;
    private final Clock clock;
    private final Integer diasVaciado;

    public VaciadoSanitarioService(GalponRepository galpones, LoteRepository lotes,
                                   AlertaVaciadoSanitarioRepository alertas, GalponEstadoService estados, Clock clock,
                                   @Value("${avicontrol.vaciado-sanitario.dias:#{null}}") Integer diasVaciado) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.alertas = alertas;
        this.estados = estados;
        this.clock = clock;
        this.diasVaciado = diasVaciado;
    }

    /**
     * Una sola transacción (FR-011): estado, alerta aceptada y desvinculación del lote se confirman juntos o no
     * se confirma nada. Primero se pide el cambio de estado, que valida bajo el bloqueo del galpón, para que dos
     * alertas distintas simultáneas no apliquen las dos.
     */
    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.galponId() == null || comando.loteId() == null
                || comando.fechaHoraEvento() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        Instant momento = comando.fechaHoraEvento().toInstant();

        var previa = alertas.findById(comando.alertaId());
        if (previa.isPresent()) {
            if (previa.get().mismosDatos(comando.galponId(), comando.loteId(), momento)) {
                log.info("ignorada alertaId={} motivo=YA_PROCESADA", comando.alertaId());
                return;
            }
            throw new ConflictoException("ALERTA_INCONSISTENTE", "El alertaId ya fue procesado con datos distintos.");
        }

        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón de la alerta no existe."));
        Lote lote = lotes.findById(comando.loteId()).orElseThrow(() ->
                new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));
        if (!lote.galponId().equals(galpon.id())) {
            throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                    "El lote no corresponde al galpón de la alerta.");
        }
        if (galpon.estado() != EstadoGalpon.EN_COSECHA) {
            throw new ValidacionNegocioException("GALPON_NO_EN_COSECHA", GALPON_NO_EN_COSECHA);
        }
        if (ReglasFecha.esFutura(comando.fechaHoraEvento(), clock)
                || !ReglasFecha.esDeHoy(comando.fechaHoraEvento(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha del evento es futura o corresponde a otro día.");
        }

        try {
            estados.solicitarCambio(galpon.id(), EstadoGalpon.EN_COSECHA, EstadoGalpon.VACIADO_SANITARIO,
                    OrigenCambioEstado.ALERTA_VACIADO_SANITARIO);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ValidacionNegocioException("GALPON_NO_EN_COSECHA", GALPON_NO_EN_COSECHA);
            }
            throw e;
        }
        Instant ahora = clock.instant();
        alertas.save(new AlertaVaciadoSanitario(comando.alertaId(), galpon.id(), lote.id(), momento, ahora, "ACEPTADA"));
        lotes.save(lote.desvincular(ahora));
    }

    /**
     * Cada galpón se procesa en su propia transacción (la de GalponEstadoService): el fallo de uno no frena a los
     * demás. Sin período configurado no se ejecuta ninguna transición (FR-015, P-02).
     */
    @Override
    public void finalizarPeriodosCumplidos() {
        if (diasVaciado == null) {
            log.warn("avicontrol.vaciado-sanitario.dias sin configurar: no se ejecuta la transicion a DISPONIBLE");
            return;
        }
        Instant limite = clock.instant().minus(Duration.ofDays(diasVaciado));
        for (UUID galponId : alertas.findGalponIdsConVaciadoCumplido(limite)) {
            try {
                estados.solicitarCambio(galponId, EstadoGalpon.VACIADO_SANITARIO, EstadoGalpon.DISPONIBLE,
                        OrigenCambioEstado.PROCESO_AUTOMATICO);
                log.info("galpon={} VACIADO_SANITARIO -> DISPONIBLE por periodo cumplido", galponId);
            } catch (RuntimeException e) {
                log.error("galpon={} no se pudo devolver a DISPONIBLE: {}", galponId, e.getMessage());
            }
        }
    }
}
