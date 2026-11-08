package com.unimag.avicontrol.infrastructure.in.rest.dto.galpon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrarGalponRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$") String nombre,
        @NotNull @Positive Long capacidad) {

    public RegistrarGalponRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
