package com.unimag.avicontrol.application.port.in.mantenimiento;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import java.util.UUID;

public interface AlertaMantenimientoUseCase {

    /** Crea la alerta PENDIENTE. No cambia el estado del galpón. */
    AlertaMantenimiento generar(Comando comando);

    /** Marca la alerta ATENDIDA y pasa el galpón de DISPONIBLE a MANTENIMIENTO en la misma transacción. */
    ResultadoAtencion atender(UUID alertaId);

    /** Más recientes primero; `galponId` nulo significa todos los galpones. */
    Pagina<AlertaMantenimiento> listar(EstadoAlertaMantenimiento estado, UUID galponId, int pagina, int tamanio);

    record Comando(UUID galponId, String descripcion, String severidad, String tipo, String tecnicoId) {
    }

    record ResultadoAtencion(AlertaMantenimiento alerta, UUID galponId, EstadoGalpon estadoGalpon) {
    }
}
