package com.unimag.avicontrol.infrastructure.in.rest.dto.lote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La regla "la fecha no es futura" no va aquí: depende de "hoy" en America/Bogota con el Clock inyectado y la
 * valida LoteService.
 */
public record RegistrarLoteRequest(
        @NotNull UUID galponId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @NotNull LocalDate fechaIngreso,
        @NotNull @Positive Long poblacionInicial,
        @NotNull @Positive Long costoTotal) {

    public RegistrarLoteRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
