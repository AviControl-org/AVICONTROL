package com.unimag.avicontrol.domain.model.lote;

import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;


public record Lote(UUID id, UUID galponId, String nombre, LocalDate fechaIngreso, long poblacionInicial,
                   long poblacionActual, long costoTotal, Instant desvinculadoEn, Long version) {

    public static Lote nuevo(UUID galponId, NombreNormalizado nombre, LocalDate fechaIngreso,
                             long poblacionInicial, long costoTotal) {
        return new Lote(UUID.randomUUID(), galponId, nombre.valor(), fechaIngreso, poblacionInicial,
                poblacionInicial, costoTotal, null, null);
    }

    public boolean activo() {
        return desvinculadoEn == null;
    }

    /**
     * Resta los pollos muertos de la población actual; no toca la población inicial ni los demás atributos. La
     * población puede llegar a cero y nunca queda negativa.
     */
    public Lote descontar(long cantidad) {
        if (cantidad <= 0) {
            throw new ValidacionNegocioException("CANTIDAD_INVALIDA",
                    "La cantidad de muertos debe ser un entero positivo.");
        }
        if (cantidad > poblacionActual) {
            throw new ValidacionNegocioException("CANTIDAD_EXCEDE_POBLACION",
                    "La cantidad de muertos supera la población actual del lote.");
        }
        return new Lote(id, galponId, nombre, fechaIngreso, poblacionInicial, poblacionActual - cantidad,
                costoTotal, desvinculadoEn, version);
    }

    public Lote desvincular(Instant momento) {
        return new Lote(id, galponId, nombre, fechaIngreso, poblacionInicial, poblacionActual, costoTotal,
                momento, version);
    }

    /** Edad en días: el día de ingreso cuenta como día 1. */
    public int edadEn(LocalDate hoy) {
        return (int) ChronoUnit.DAYS.between(fechaIngreso, hoy) + 1;
    }
}
