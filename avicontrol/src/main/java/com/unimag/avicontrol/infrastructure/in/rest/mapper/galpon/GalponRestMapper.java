package com.unimag.avicontrol.infrastructure.in.rest.mapper.galpon;

import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponResponse;

/** Convierte entre el dominio y los DTO REST. Crece con cada historia que expone nuevas respuestas. */
public final class GalponRestMapper {

    private GalponRestMapper() {
    }

    public static GalponResponse aResponse(Galpon galpon) {
        return new GalponResponse(galpon.id(), galpon.nombre(), galpon.capacidad(), galpon.estado());
    }
}
