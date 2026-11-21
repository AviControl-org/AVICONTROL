package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import java.util.UUID;

public record GalponResponse(UUID id, String nombre, long capacidad, EstadoGalpon estado) {
}
