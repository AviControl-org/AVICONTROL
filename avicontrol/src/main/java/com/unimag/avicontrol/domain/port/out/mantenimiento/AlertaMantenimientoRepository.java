package com.unimag.avicontrol.domain.port.out.mantenimiento;

import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import java.util.Optional;
import java.util.UUID;

public interface AlertaMantenimientoRepository {

    AlertaMantenimiento save(AlertaMantenimiento alerta);

    Optional<AlertaMantenimiento> findById(UUID id);
}
