package com.unimag.avicontrol.infrastructure.in.kafka.dto.sanitario;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code payload} del evento de vaciado sanitario. */
public record VaciadoSanitarioMessage(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento) {
}
