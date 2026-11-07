package com.unimag.avicontrol.infrastructure.out.persistence.mapper.compartido;

import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.mantenimiento.AlertaMantenimientoEntity;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario.AlertaVaciadoSanitarioEntity;
import com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario.RegistroProcesamientoEntity;

/** Mapper de las alertas (mantenimiento y vaciado sanitario) y del registro de procesamiento de eventos. */
public final class AlertaPersistenceMapper {

    private AlertaPersistenceMapper() {
    }

    public static AlertaMantenimiento aDominio(AlertaMantenimientoEntity e) {
        return new AlertaMantenimiento(e.getId(), e.getGalponId(), e.getDescripcion(), e.getFechaHora(),
                e.getTecnicoId(), e.getEstado(), e.getSeveridad(), e.getTipo());
    }

    public static AlertaMantenimientoEntity aEntidad(AlertaMantenimiento a) {
        return new AlertaMantenimientoEntity(a.id(), a.galponId(), a.descripcion(), a.fechaHora(),
                a.tecnicoId(), a.estado(), a.severidad(), a.tipo());
    }

    public static AlertaVaciadoSanitario aDominio(AlertaVaciadoSanitarioEntity e) {
        return new AlertaVaciadoSanitario(e.getAlertaId(), e.getGalponId(), e.getLoteId(), e.getFechaHoraEvento(),
                e.getFechaHoraRecepcion(), e.getResultado());
    }

    public static AlertaVaciadoSanitarioEntity aEntidad(AlertaVaciadoSanitario a) {
        return new AlertaVaciadoSanitarioEntity(a.alertaId(), a.galponId(), a.loteId(), a.fechaHoraEvento(),
                a.fechaHoraRecepcion(), a.resultado());
    }

    public static RegistroProcesamiento aDominio(RegistroProcesamientoEntity e) {
        return new RegistroProcesamiento(e.getAlertaId(), e.getGalponId(), e.getLoteId(), e.getCantidadMuertos(),
                e.getFechaHoraEvento(), e.getProcesadoEn());
    }

    public static RegistroProcesamientoEntity aEntidad(RegistroProcesamiento r) {
        return new RegistroProcesamientoEntity(r.alertaId(), r.galponId(), r.loteId(), r.cantidadMuertos(),
                r.fechaHoraEvento(), r.procesadoEn());
    }
}
