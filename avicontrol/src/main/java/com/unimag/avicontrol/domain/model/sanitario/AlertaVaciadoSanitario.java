package com.unimag.avicontrol.domain.model.sanitario;

import java.time.Instant;
import java.util.UUID;

/** Alerta de vaciado sanitario aceptada; conserva los UUID de galpón y lote aunque el lote se desvincule. */
public record AlertaVaciadoSanitario(UUID alertaId, UUID galponId, UUID loteId, Instant fechaHoraEvento,
                                     Instant fechaHoraRecepcion, String resultado) {
}
