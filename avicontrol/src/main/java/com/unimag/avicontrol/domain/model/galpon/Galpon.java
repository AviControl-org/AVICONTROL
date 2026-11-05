package com.unimag.avicontrol.domain.model.galpon;

import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import java.util.UUID;


public record Galpon(UUID id, String nombre, long capacidad, EstadoGalpon estado, Long version) {

    public static Galpon nuevo(NombreNormalizado nombre, long capacidad) {
        return new Galpon(UUID.randomUUID(), nombre.valor(), capacidad, EstadoGalpon.DISPONIBLE, null);
    }

    public Galpon conNombre(NombreNormalizado nuevoNombre) {
        return new Galpon(id, nuevoNombre.valor(), capacidad, estado, version);
    }

    public Galpon conCapacidad(long nuevaCapacidad) {
        return new Galpon(id, nombre, nuevaCapacidad, estado, version);
    }

    public Galpon conEstado(EstadoGalpon nuevoEstado) {
        return new Galpon(id, nombre, capacidad, nuevoEstado, version);
    }
}
