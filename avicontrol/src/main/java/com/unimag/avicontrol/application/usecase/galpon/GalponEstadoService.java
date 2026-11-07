package com.unimag.avicontrol.application.usecase.galpon;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.policy.galpon.TransicionesPermitidas;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única autoridad que escribe el estado del galpón. Todos los specs que cambian el estado lo piden aquí
 * con su propio origen (FR-012).
 */
@Service
public class GalponEstadoService {

    private final GalponRepository galpones;
    private final HistorialEstadoRepository historial;
    private final Clock clock;

    public GalponEstadoService(GalponRepository galpones, HistorialEstadoRepository historial, Clock clock) {
        this.galpones = galpones;
        this.historial = historial;
        this.clock = clock;
    }

    /**
     * Orden de validación: galpón existe (404) -> el estado vigente es el esperado (409) -> transición en la matriz
     * (409) -> origen autorizado (409). El cambio y su fila de historial se guardan en la misma transacción.
     */
    @Transactional
    public HistorialEstado solicitarCambio(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino,
                                           OrigenCambioEstado origen) {
        Galpon galpon = galpones.findByIdForUpdate(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        if (galpon.estado() != estadoEsperado) {
            throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                    "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.");
        }
        TransicionesPermitidas.validar(galpon.estado(), estadoDestino, origen);

        galpones.save(galpon.conEstado(estadoDestino));
        return historial.save(new HistorialEstado(UUID.randomUUID(), galponId, galpon.estado(), estadoDestino,
                clock.instant(), origen));
    }
}
