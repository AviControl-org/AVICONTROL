package com.unimag.avicontrol.domain.exception;

/** El recurso pedido no existe (HTTP 404). */
public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
