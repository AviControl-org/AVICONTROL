package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistorialEstadoResponse(UUID id, UUID galponId, EstadoGalpon estadoAnterior, EstadoGalpon estadoNuevo,
                                      OffsetDateTime fechaHora, OrigenCambioEstado origen) {
}
