package com.unimag.avicontrol.infrastructure.out.persistence.mapper.lote;

import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.lote.LoteEntity;

public final class LotePersistenceMapper {

    private LotePersistenceMapper() {
    }

    public static Lote aDominio(LoteEntity e) {
        return new Lote(e.getId(), e.getGalponId(), e.getNombre(), e.getFechaIngreso(), e.getPoblacionInicial(),
                e.getPoblacionActual(), e.getCostoTotal(), e.getDesvinculadoEn(), e.getVersion());
    }

    public static LoteEntity aEntidad(Lote l) {
        return new LoteEntity(l.id(), l.galponId(), l.nombre(), l.fechaIngreso(), l.poblacionInicial(),
                l.poblacionActual(), l.costoTotal(), l.desvinculadoEn(), l.version());
    }
}
