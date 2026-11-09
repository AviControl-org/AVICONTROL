package com.unimag.avicontrol.application.port.in.lote;

import java.time.Instant;
import java.util.UUID;

public record ActualizarPoblacionCommand(UUID alertaId, UUID loteId, UUID galponId, long cantidadMuertos,
                                         Instant fechaHoraEvento) {
}
