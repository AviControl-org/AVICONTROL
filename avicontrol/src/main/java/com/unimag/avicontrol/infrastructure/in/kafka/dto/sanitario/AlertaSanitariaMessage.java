package com.unimag.avicontrol.infrastructure.in.kafka.dto.sanitario;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code payload} del evento de alerta sanitaria. {@code gravedad} es opcional y no se valida ni se guarda. */
public record AlertaSanitariaMessage(UUID alertaId, String accion, UUID galponId, UUID loteId,
                                     OffsetDateTime fechaHora, String tipoEnfermedad, String descripcion,
                                     String gravedad) {
}
