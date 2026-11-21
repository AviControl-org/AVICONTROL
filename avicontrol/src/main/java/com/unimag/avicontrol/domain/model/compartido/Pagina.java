package com.unimag.avicontrol.domain.model.compartido;

import java.util.List;

/** Resultado paginado en tipos del dominio; `pagina` empieza en 1. */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public int totalPaginas() {
        return tamanio <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamanio);
    }
}
