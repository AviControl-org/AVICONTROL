package com.unimag.avicontrol.domain.port.out.sanitario;

import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import java.util.Optional;
import java.util.UUID;

public interface AlertaVaciadoSanitarioRepository {

    AlertaVaciadoSanitario save(AlertaVaciadoSanitario alerta);

    Optional<AlertaVaciadoSanitario> findById(UUID alertaId);
}
