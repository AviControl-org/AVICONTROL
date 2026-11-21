package com.unimag.avicontrol.application.port.in.lote;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.lote.Lote;

public interface ListarLotesUseCase {

    /** Lotes del más reciente al más antiguo; {@code pagina} empieza en 1. */
    Pagina<Lote> listar(int pagina, int tamanio);
}
