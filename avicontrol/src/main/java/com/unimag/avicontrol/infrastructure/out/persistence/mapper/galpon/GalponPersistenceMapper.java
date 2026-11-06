package com.unimag.avicontrol.infrastructure.out.persistence.mapper.galpon;

import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.GalponEntity;

public final class GalponPersistenceMapper {

    private GalponPersistenceMapper() {
    }

    public static Galpon aDominio(GalponEntity e) {
        return new Galpon(e.getId(), e.getNombre(), e.getCapacidad(), e.getEstado(), e.getVersion());
    }

    public static GalponEntity aEntidad(Galpon g) {
        return new GalponEntity(g.id(), g.nombre(), g.capacidad(), g.estado(), g.version());
    }
}
