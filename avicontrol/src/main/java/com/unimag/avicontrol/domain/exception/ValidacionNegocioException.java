package com.unimag.avicontrol.domain.exception;

/** Datos inválidos (HTTP 400). */
public class ValidacionNegocioException extends NegocioException {

    public ValidacionNegocioException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
