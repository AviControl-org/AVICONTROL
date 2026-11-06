package com.unimag.avicontrol.domain.exception;

import java.util.List;

/** Base de los rechazos de negocio. El código es el `code` estable del contrato; el mensaje es el detalle. */
public abstract class NegocioException extends RuntimeException {

    private final String codigo;
    private final List<ErrorDeCampo> errores;

    protected NegocioException(String codigo, String mensaje, ErrorDeCampo... errores) {
        super(mensaje);
        this.codigo = codigo;
        this.errores = List.of(errores);
    }

    public String codigo() {
        return codigo;
    }

    /** Errores por campo o parámetro; vacío cuando el rechazo no apunta a un campo concreto. */
    public List<ErrorDeCampo> errores() {
        return errores;
    }
}
