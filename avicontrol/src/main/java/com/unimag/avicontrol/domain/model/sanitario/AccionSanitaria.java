package com.unimag.avicontrol.domain.model.sanitario;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;



public enum AccionSanitaria {
    AISLAMIENTO, REANUDACION;

    public static AccionSanitaria de(String texto) {
        try {
            return AccionSanitaria.valueOf(texto);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidacionNegocioException("ACCION_INVALIDA", "La acción debe ser AISLAMIENTO o REANUDACION.");
        }
    }
}
