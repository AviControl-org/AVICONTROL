package com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** `tecnicoId` viaja en el cuerpo mientras no haya autenticación (P-14); `severidad` y `tipo` son texto libre (P-15). */
public record GenerarAlertaMantenimientoRequest(@NotNull UUID galponId,
                                                @NotBlank @Size(max = 500) String descripcion,
                                                String severidad, String tipo,
                                                @NotBlank String tecnicoId) {

    public GenerarAlertaMantenimientoRequest {
        descripcion = descripcion == null ? null : descripcion.strip();
        tecnicoId = tecnicoId == null ? null : tecnicoId.strip();
    }
}
