package com.unimag.avicontrol.domain.exception;

/** Choque con el estado actual del sistema (HTTP 409). */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje, ErrorDeCampo... errores) {
        super(codigo, mensaje, errores);
    }

    /** 409 `NOMBRE_DUPLICADO`; señala el campo `nombre` en `fieldErrors`. */
    public static ConflictoException nombreDuplicado(String mensaje) {
        return new ConflictoException("NOMBRE_DUPLICADO", mensaje,
                new ErrorDeCampo("nombre", "DUPLICADO", "El nombre ya está en uso"));
    }
}
