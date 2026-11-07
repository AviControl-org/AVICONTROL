package com.unimag.avicontrol.infrastructure.out.persistence.adapter.compartido;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Nombres de los índices únicos de `V1__esquema_inicial.sql`. Permiten traducir la violación de un índice
 * a una excepción de negocio dentro de los adaptadores, sin que salga ninguna excepción de JPA.
 */
public final class RestriccionesUnicas {

    public static final String GALPON_NOMBRE = "ux_galpon_nombre";
    public static final String LOTE_NOMBRE = "ux_lote_nombre";
    public static final String LOTE_ACTIVO_POR_GALPON = "ux_lote_activo_por_galpon";
    public static final String ALERTA_PENDIENTE_POR_GALPON = "ux_alerta_mantenimiento_pendiente_por_galpon";

    private RestriccionesUnicas() {
    }

    /** Indica si la violación de integridad corresponde al índice indicado. */
    public static boolean viola(DataIntegrityViolationException e, String indice) {
        for (Throwable causa = e; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof ConstraintViolationException cv && indice.equalsIgnoreCase(cv.getConstraintName())) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains(indice)) {
                return true;
            }
        }
        return false;
    }
}
