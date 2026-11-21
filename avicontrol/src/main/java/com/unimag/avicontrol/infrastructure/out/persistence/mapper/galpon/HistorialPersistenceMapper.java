package com.unimag.avicontrol.infrastructure.out.persistence.mapper.galpon;

import com.unimag.avicontrol.domain.model.galpon.HistorialCambioGalpon;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.HistorialCambioGalponEntity;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon.HistorialEstadoEntity;

public final class HistorialPersistenceMapper {

    private HistorialPersistenceMapper() {
    }

    public static HistorialEstado aDominio(HistorialEstadoEntity e) {
        return new HistorialEstado(e.getId(), e.getGalponId(), e.getEstadoAnterior(), e.getEstadoNuevo(),
                e.getFechaHora(), e.getOrigen());
    }

    public static HistorialEstadoEntity aEntidad(HistorialEstado h) {
        return new HistorialEstadoEntity(h.id(), h.galponId(), h.estadoAnterior(), h.estadoNuevo(),
                h.fechaHora(), h.origen());
    }

    public static HistorialCambioGalpon aDominio(HistorialCambioGalponEntity e) {
        return new HistorialCambioGalpon(e.getId(), e.getGalponId(), e.getCampo(), e.getValorAnterior(),
                e.getValorNuevo(), e.getFechaHora());
    }

    public static HistorialCambioGalponEntity aEntidad(HistorialCambioGalpon c) {
        return new HistorialCambioGalponEntity(c.id(), c.galponId(), c.campo(), c.valorAnterior(),
                c.valorNuevo(), c.fechaHora());
    }
}
