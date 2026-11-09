package com.unimag.avicontrol.application.usecase.sanitario;

import com.unimag.avicontrol.application.port.in.sanitario.RecibirAlertaSanitariaUseCase;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.model.sanitario.AccionSanitaria;
import com.unimag.avicontrol.domain.policy.compartido.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No guarda la alerta (FR-004) ni toca el lote; solo pide el cambio de estado del galpón. */
@Service
public class AlertaSanitariaService implements RecibirAlertaSanitariaUseCase {

    private static final String ACCION_INCOMPATIBLE = "La acción no corresponde al estado actual del galpón.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final GalponEstadoService estados;
    private final Clock clock;

    public AlertaSanitariaService(GalponRepository galpones, LoteRepository lotes, GalponEstadoService estados,
                                  Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.estados = estados;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recibir(Comando comando) {
        if (comando.alertaId() == null || comando.accion() == null || comando.fechaHora() == null) {
            throw new ValidacionNegocioException("ALERTA_INCOMPLETA", "Falta un campo obligatorio de la alerta.");
        }
        AccionSanitaria accion = AccionSanitaria.de(comando.accion());
        if (comando.galponId() == null && comando.loteId() == null) {
            throw new ValidacionNegocioException("GALPON_O_LOTE_REQUERIDO",
                    "La alerta debe identificar un galpón o un lote.");
        }
        if (!ReglasFecha.esDeHoy(comando.fechaHora(), clock)) {
            throw new ValidacionNegocioException("FECHA_INVALIDA",
                    "La fecha de la alerta no corresponde al día actual.");
        }
        if (accion == AccionSanitaria.AISLAMIENTO
                && (esVacio(comando.tipoEnfermedad()) || esVacio(comando.descripcion()))) {
            throw new ValidacionNegocioException("DATOS_AISLAMIENTO_INCOMPLETOS",
                    "Una alerta de aislamiento requiere tipo de enfermedad y descripción.");
        }

        Galpon galpon = resolverGalpon(comando.galponId(), comando.loteId());

        // AISLAMIENTO: PRODUCTIVO -> AISLAMIENTO. REANUDACION: AISLAMIENTO -> PRODUCTIVO.
        EstadoGalpon esperado = accion == AccionSanitaria.AISLAMIENTO ? EstadoGalpon.PRODUCTIVO : EstadoGalpon.AISLAMIENTO;
        EstadoGalpon destino = accion == AccionSanitaria.AISLAMIENTO ? EstadoGalpon.AISLAMIENTO : EstadoGalpon.PRODUCTIVO;
        if (galpon.estado() != esperado) {
            throw new ConflictoException("ACCION_INCOMPATIBLE", ACCION_INCOMPATIBLE);
        }
        try {
            estados.solicitarCambio(galpon.id(), esperado, destino, OrigenCambioEstado.ALERTA_SANITARIA_MODULO_2);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("ACCION_INCOMPATIBLE", ACCION_INCOMPATIBLE);
            }
            throw e;
        }
    }

    /** Resuelve el galpón directamente o por el lote activo; si llegan los dos, deben coincidir. */
    private Galpon resolverGalpon(UUID galponId, UUID loteId) {
        UUID idGalpon = galponId;
        if (loteId != null) {
            Lote lote = lotes.findById(loteId).orElseThrow(() ->
                    new RecursoNoEncontradoException("LOTE_NO_ENCONTRADO", "El lote de la alerta no existe."));
            if (galponId != null && !galponId.equals(lote.galponId())) {
                throw new ValidacionNegocioException("LOTE_NO_PERTENECE_AL_GALPON",
                        "El lote no corresponde al galpón de la alerta.");
            }
            if (!lote.activo()) {
                throw new ValidacionNegocioException("LOTE_NO_ACTIVO", "El lote no está activo.");
            }
            idGalpon = lote.galponId();
        }
        return galpones.findById(idGalpon).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón de la alerta no existe."));
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
