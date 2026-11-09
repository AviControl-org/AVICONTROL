package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Ambos campos son opcionales: un campo ausente (o null) no se modifica. */
public record EditarGalponRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @Positive Long capacidad) {

    public EditarGalponRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
