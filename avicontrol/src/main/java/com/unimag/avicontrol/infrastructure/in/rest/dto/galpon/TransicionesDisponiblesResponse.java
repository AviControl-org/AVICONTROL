package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import java.util.List;
import java.util.UUID;

public record TransicionesDisponiblesResponse(UUID galponId, EstadoGalpon estadoActual,
                                              List<EstadoGalpon> destinosPermitidos) {
}
