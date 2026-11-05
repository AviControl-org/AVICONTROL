package com.unimag.avicontrol.domain.model.mantenimiento;

import java.time.Instant;
import java.util.UUID;

/** `severidad` y `tipo` son opcionales. */
public record AlertaMantenimiento(UUID id, UUID galponId, String descripcion, Instant fechaHora, String tecnicoId,
                                  EstadoAlertaMantenimiento estado, String severidad, String tipo) {

    public static AlertaMantenimiento nueva(UUID galponId, String descripcion, String tecnicoId, String severidad,
                                            String tipo, Instant ahora) {
        return new AlertaMantenimiento(UUID.randomUUID(), galponId, descripcion, ahora, tecnicoId,
                EstadoAlertaMantenimiento.PENDIENTE, severidad, tipo);
    }

    public AlertaMantenimiento atendida() {
        return new AlertaMantenimiento(id, galponId, descripcion, fechaHora, tecnicoId,
                EstadoAlertaMantenimiento.ATENDIDA, severidad, tipo);
    }
}
