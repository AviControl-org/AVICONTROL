package com.unimag.avicontrol.domain.model.galpon;

/** Criterios del listado de galpones; {@code nombre} y {@code estado} en null significan "sin filtro". */
public record ConsultaGalpones(String nombre, EstadoGalpon estado, CampoOrden campo, boolean ascendente,
                               int pagina, int tamanio) {

    public enum CampoOrden { NOMBRE, CAPACIDAD, ESTADO }

    public ConsultaGalpones sinNombre() {
        return new ConsultaGalpones(null, estado, campo, ascendente, pagina, tamanio);
    }
}
