package com.unimag.avicontrol.infrastructure.in.rest.compartido;

import com.unimag.avicontrol.domain.exception.ErrorDeCampo;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;

/**
 * Validación de los query params de las consultas; todo fallo es 400 PARAMETRO_INVALIDO con el parámetro señalado.
 * Crece con cada historia: por ahora solo lo que usa el listado de lotes (US2).
 */
public final class ParametrosConsulta {

    public static final int TAMANIO_PAGINA_FIJO = 10;

    private ParametrosConsulta() {
    }

    public static int pagina(int page) {
        if (page < 1) {
            throw invalido("page", "MINIMO_1", "Debe ser mayor o igual a 1");
        }
        return page;
    }

    private static ValidacionNegocioException invalido(String parametro, String codigo, String mensaje) {
        return new ValidacionNegocioException("PARAMETRO_INVALIDO", "Revisa los parámetros de la solicitud.",
                new ErrorDeCampo(parametro, codigo, mensaje));
    }
}
