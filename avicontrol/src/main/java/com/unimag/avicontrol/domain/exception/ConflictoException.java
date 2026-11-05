package com.unimag.avicontrol.domain.exception;

/** Choque con el estado actual del sistema (HTTP 409). */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
