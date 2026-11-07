package com.unimag.avicontrol.domain.model.sanitario;

import java.time.Instant;
import java.util.UUID;

/** Control técnico de idempotencia de la mortalidad: el mismo `alertaId` no se aplica dos veces. */
public record RegistroProcesamiento(UUID alertaId, UUID galponId, UUID loteId, long cantidadMuertos,
                                    Instant fechaHoraEvento, Instant procesadoEn) {
}
