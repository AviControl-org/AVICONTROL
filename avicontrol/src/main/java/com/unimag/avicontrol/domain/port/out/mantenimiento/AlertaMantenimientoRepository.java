package com.unimag.avicontrol.domain.port.out.mantenimiento;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import java.util.Optional;
import java.util.UUID;

public interface AlertaMantenimientoRepository {

    AlertaMantenimiento save(AlertaMantenimiento alerta);

    Optional<AlertaMantenimiento> findById(UUID id);

    boolean existsPendienteByGalponId(UUID galponId);

    /** Más recientes primero (desempata por id); `galponId` nulo no filtra por galpón; `pagina` empieza en 1. */
    Pagina<AlertaMantenimiento> search(EstadoAlertaMantenimiento estado, UUID galponId, int pagina, int tamanio);
}
