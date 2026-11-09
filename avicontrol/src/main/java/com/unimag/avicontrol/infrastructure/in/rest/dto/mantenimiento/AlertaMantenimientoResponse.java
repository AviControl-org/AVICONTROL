package com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento;

import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertaMantenimientoResponse(UUID id, UUID galponId, String descripcion, OffsetDateTime fechaHora,
                                          String tecnicoId, EstadoAlertaMantenimiento estado, String severidad,
                                          String tipo) {
}
