package com.unimag.avicontrol.domain.port.out.sanitario;

import java.util.List;
import java.time.Instant;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import java.util.Optional;
import java.util.UUID;

public interface AlertaVaciadoSanitarioRepository {

    AlertaVaciadoSanitario save(AlertaVaciadoSanitario alerta);

    Optional<AlertaVaciadoSanitario> findById(UUID alertaId);

    /** Galpones en VACIADO_SANITARIO cuya última alerta aceptada se recibió en {@code limite} o antes. */
    List<UUID> findGalponIdsConVaciadoCumplido(Instant limite);
}
