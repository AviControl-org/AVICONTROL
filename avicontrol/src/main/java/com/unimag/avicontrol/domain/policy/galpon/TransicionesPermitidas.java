package com.unimag.avicontrol.domain.policy.galpon;

import static com.unimag.avicontrol.domain.model.galpon.EstadoGalpon.*;
import static com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado.*;

import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Matriz de las 9 transiciones del galpón con los orígenes autorizados para cada una (FR-002). */
public final class TransicionesPermitidas {

    public record Transicion(EstadoGalpon desde, EstadoGalpon hacia) {
    }

    private static final Map<Transicion, Set<OrigenCambioEstado>> MATRIZ = Map.of(
            new Transicion(DISPONIBLE, PRODUCTIVO), Set.of(REGISTRO_LOTE),
            new Transicion(DISPONIBLE, MANTENIMIENTO), Set.of(ADMINISTRADOR),
            new Transicion(PRODUCTIVO, EN_COSECHA), Set.of(ADMINISTRADOR),
            new Transicion(PRODUCTIVO, AISLAMIENTO), Set.of(ALERTA_SANITARIA_MODULO_2),
            new Transicion(EN_COSECHA, VACIADO_SANITARIO), Set.of(ALERTA_VACIADO_SANITARIO),
            new Transicion(VACIADO_SANITARIO, DISPONIBLE), Set.of(PROCESO_AUTOMATICO),
            new Transicion(MANTENIMIENTO, DISPONIBLE), Set.of(ADMINISTRADOR),
            new Transicion(AISLAMIENTO, PRODUCTIVO), Set.of(ALERTA_SANITARIA_MODULO_2),
            new Transicion(AISLAMIENTO, EN_COSECHA), Set.of(ADMINISTRADOR));

    /**
     * Transiciones que el administrador pide directamente por REST. DISPONIBLE -> MANTENIMIENTO también es
     * del administrador, pero solo se hace atendiendo una alerta de mantenimiento.
     */
    private static final Set<Transicion> MANUALES = Set.of(
            new Transicion(PRODUCTIVO, EN_COSECHA),
            new Transicion(AISLAMIENTO, EN_COSECHA),
            new Transicion(MANTENIMIENTO, DISPONIBLE));

    private TransicionesPermitidas() {
    }

    public static boolean existe(EstadoGalpon desde, EstadoGalpon hacia) {
        return MATRIZ.containsKey(new Transicion(desde, hacia));
    }

    /** Destinos que el administrador puede pedir por REST desde el estado dado. */
    public static List<EstadoGalpon> destinosManuales(EstadoGalpon desde) {
        return MANUALES.stream()
                .filter(t -> t.desde() == desde)
                .map(Transicion::hacia)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Valida matriz y origen; el orden del spec es: transición existente y luego origen autorizado. */
    public static void validar(EstadoGalpon desde, EstadoGalpon hacia, OrigenCambioEstado origen) {
        Set<OrigenCambioEstado> origenes = MATRIZ.get(new Transicion(desde, hacia));
        if (origenes == null) {
            throw transicionNoPermitida();
        }
        if (!origenes.contains(origen)) {
            throw origenNoAutorizado(origen);
        }
    }

    public static ConflictoException transicionNoPermitida() {
        return new ConflictoException("TRANSICION_NO_PERMITIDA",
                "No se puede pasar de ese estado al estado solicitado.");
    }

    public static ConflictoException origenNoAutorizado(OrigenCambioEstado origen) {
        String detalle = origen == ADMINISTRADOR
                ? "El administrador no puede solicitar esa transición por este servicio."
                : "El origen de la solicitud no está autorizado para esa transición.";
        return new ConflictoException("ORIGEN_NO_AUTORIZADO", detalle);
    }
}
