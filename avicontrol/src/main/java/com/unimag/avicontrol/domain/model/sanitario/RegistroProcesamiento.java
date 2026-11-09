package com.unimag.avicontrol.domain.model.sanitario;

import java.time.Instant;
import java.util.UUID;

/** Control técnico de idempotencia de la mortalidad: el mismo `alertaId` no se aplica dos veces. */
public record RegistroProcesamiento(UUID alertaId, UUID galponId, UUID loteId, long cantidadMuertos,
                                    Instant fechaHoraEvento, Instant procesadoEn) {

    /** Mismo alertaId con los mismos datos aplicados: es una reentrega, no una alerta distinta. */
    public boolean mismosDatos(UUID galponId, UUID loteId, long cantidad, Instant fechaHora) {
        return this.galponId.equals(galponId) && this.loteId.equals(loteId) && this.cantidadMuertos == cantidad
                && this.fechaHoraEvento.equals(fechaHora);
    }
}
