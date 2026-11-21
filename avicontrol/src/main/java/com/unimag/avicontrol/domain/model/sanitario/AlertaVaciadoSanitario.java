package com.unimag.avicontrol.domain.model.sanitario;

import java.time.Instant;
import java.util.UUID;

/** Alerta de vaciado sanitario aceptada; conserva los UUID de galpón y lote aunque el lote se desvincule. */
public record AlertaVaciadoSanitario(UUID alertaId, UUID galponId, UUID loteId, Instant fechaHoraEvento,
                                     Instant fechaHoraRecepcion, String resultado) {

    /** Mismo alertaId con los mismos datos: es una reentrega, no una alerta distinta. */
    public boolean mismosDatos(UUID galponId, UUID loteId, Instant fechaHora) {
        return this.galponId.equals(galponId) && this.loteId.equals(loteId) && this.fechaHoraEvento.equals(fechaHora);
    }
}
