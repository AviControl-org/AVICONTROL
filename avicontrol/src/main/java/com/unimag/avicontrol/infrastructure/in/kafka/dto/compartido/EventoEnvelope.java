package com.unimag.avicontrol.infrastructure.in.kafka.dto.compartido;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sobre estándar de los eventos del Módulo 2. El Módulo 1 solo usa `eventType`, `eventVersion` y `payload`;
 * el resto de campos es informativo y los campos que no estén aquí se ignoran (P-06).
 */
public record EventoEnvelope<T>(UUID eventId, String eventType, Integer eventVersion, OffsetDateTime occurredAt,
                                OffsetDateTime publishedAt, String producer, String correlationId,
                                String causationId, String aggregateType, String aggregateId, T payload) {
}
