package com.unimag.avicontrol.application.port.in.sanitario;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirAlertaSanitariaUseCase {

    void recibir(Comando comando);

    record Comando(UUID alertaId, String accion, UUID galponId, UUID loteId, OffsetDateTime fechaHora,
                   String tipoEnfermedad, String descripcion, String gravedad) {
    }
}
