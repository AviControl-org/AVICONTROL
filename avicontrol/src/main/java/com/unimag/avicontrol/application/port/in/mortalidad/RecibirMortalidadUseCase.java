package com.unimag.avicontrol.application.port.in.mortalidad;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecibirMortalidadUseCase {

    void recibir(Comando comando);

    /** Campos sin validar tal como llegan del mensaje; el caso de uso valida y rechaza con su código. */
    record Comando(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento, Long cantidadMuertos) {
    }
}
