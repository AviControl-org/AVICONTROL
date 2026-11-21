package com.unimag.avicontrol.infrastructure.in.kafka.dto.mortalidad;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code payload} del evento de mortalidad. {@code cantidadMuertos} llega como Number para distinguir un entero (25)
 * de un decimal (25.0 o 25.5), que se rechaza como CANTIDAD_INVALIDA (FR-008).
 */
public record MortalidadMessage(UUID alertaId, UUID galponId, UUID loteId, OffsetDateTime fechaHoraEvento,
                                Number cantidadMuertos) {
}
