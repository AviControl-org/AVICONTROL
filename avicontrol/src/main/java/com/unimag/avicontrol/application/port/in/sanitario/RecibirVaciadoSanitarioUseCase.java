package com.unimag.avicontrol.application.port.in.sanitario;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirVaciadoSanitarioUseCase {

    void recibir(Comando comando);

    record Comando(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento) {
    }
}
