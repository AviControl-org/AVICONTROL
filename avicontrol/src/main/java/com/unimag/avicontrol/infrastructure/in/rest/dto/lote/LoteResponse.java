package com.unimag.avicontrol.infrastructure.in.rest.dto.lote;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code desvinculadoEn} se omite mientras el lote está activo (la respuesta no envía nulos). */
public record LoteResponse(UUID id, String nombre, UUID galponId, LocalDate fechaIngreso, long poblacionInicial,
                           long poblacionActual, long costoTotal, OffsetDateTime desvinculadoEn) {
}
