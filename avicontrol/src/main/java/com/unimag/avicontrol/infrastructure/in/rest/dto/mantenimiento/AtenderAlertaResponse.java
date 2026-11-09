package com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import java.util.UUID;

public record AtenderAlertaResponse(AlertaEstado alerta, GalponEstado galpon) {

    public record AlertaEstado(UUID id, EstadoAlertaMantenimiento estado) {
    }

    public record GalponEstado(UUID id, EstadoGalpon estado) {
    }
}
