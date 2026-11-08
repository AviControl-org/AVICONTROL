package com.unimag.avicontrol.infrastructure.in.rest.compartido;

import com.unimag.avicontrol.domain.exception.ErrorDeCampo;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones.CampoOrden;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Validación de los query params de las consultas; todo fallo es 400 PARAMETRO_INVALIDO con el parámetro señalado.
 * Crece con cada historia: por ahora lo que usan los listados de lotes (US2) y de galpones (US3).
 */
public final class ParametrosConsulta {

    public static final int TAMANIO_PAGINA_FIJO = 10;

    private ParametrosConsulta() {
    }

    public record Orden(CampoOrden campo, boolean ascendente) {
    }

    public static int pagina(int page) {
        if (page < 1) {
            throw invalido("page", "MINIMO_1", "Debe ser mayor o igual a 1");
        }
        return page;
    }

    /** {@code estado} ausente o vacío significa "sin filtro". */
    public static EstadoGalpon estadoGalpon(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return EstadoGalpon.valueOf(texto.strip());
        } catch (IllegalArgumentException e) {
            throw invalido("estado", "VALOR_NO_PERMITIDO", "Debe ser uno de: " + valores(EstadoGalpon.values()));
        }
    }

    /** Formato {@code campo,direccion}: campo nombre, capacidad o estado; dirección asc o desc (la dirección es opcional). */
    public static Orden orden(String texto) {
        String[] partes = texto.strip().split(",");
        if (partes.length > 2) {
            throw ordenInvalido();
        }
        CampoOrden campo;
        try {
            campo = CampoOrden.valueOf(partes[0].strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ordenInvalido();
        }
        String direccion = partes.length == 2 ? partes[1].strip().toLowerCase() : "asc";
        if (!direccion.equals("asc") && !direccion.equals("desc")) {
            throw ordenInvalido();
        }
        return new Orden(campo, direccion.equals("asc"));
    }

    private static ValidacionNegocioException ordenInvalido() {
        return invalido("orden", "FORMATO_INVALIDO", "Usa campo,direccion: nombre, capacidad o estado; asc o desc");
    }

    private static String valores(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static ValidacionNegocioException invalido(String parametro, String codigo, String mensaje) {
        return new ValidacionNegocioException("PARAMETRO_INVALIDO", "Revisa los parámetros de la solicitud.",
                new ErrorDeCampo(parametro, codigo, mensaje));
    }
}
