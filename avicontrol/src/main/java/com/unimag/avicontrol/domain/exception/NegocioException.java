package com.unimag.avicontrol.domain.exception;

/** Base de los rechazos de negocio. El código es el `code` estable del contrato; el mensaje es el detalle. */
public abstract class NegocioException extends RuntimeException {

    private final String codigo;

    protected NegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
