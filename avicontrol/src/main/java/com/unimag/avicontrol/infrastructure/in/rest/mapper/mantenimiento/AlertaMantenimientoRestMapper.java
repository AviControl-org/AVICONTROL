package com.unimag.avicontrol.infrastructure.in.rest.mapper.mantenimiento;

import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase.ResultadoAtencion;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AlertaMantenimientoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AlertaMantenimientoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AtenderAlertaResponse;

public final class AlertaMantenimientoRestMapper {

    private AlertaMantenimientoRestMapper() {
    }

    public static AlertaMantenimientoResponse aResponse(AlertaMantenimiento a) {
        return new AlertaMantenimientoResponse(a.id(), a.galponId(), a.descripcion(),
                a.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime(), a.tecnicoId(), a.estado(),
                a.severidad(), a.tipo());
    }

    public static AlertaMantenimientoPaginaResponse aPagina(Pagina<AlertaMantenimiento> p) {
        return new AlertaMantenimientoPaginaResponse(p.contenido().stream()
                .map(AlertaMantenimientoRestMapper::aResponse).toList(),
                p.pagina(), p.tamanio(), p.totalElementos(), p.totalPaginas());
    }

    public static AtenderAlertaResponse aAtencion(ResultadoAtencion r) {
        return new AtenderAlertaResponse(
                new AtenderAlertaResponse.AlertaEstado(r.alerta().id(), r.alerta().estado()),
                new AtenderAlertaResponse.GalponEstado(r.galponId(), r.estadoGalpon()));
    }
}
