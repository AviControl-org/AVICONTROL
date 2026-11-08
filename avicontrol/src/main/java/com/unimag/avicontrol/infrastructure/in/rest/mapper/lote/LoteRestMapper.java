package com.unimag.avicontrol.infrastructure.in.rest.mapper.lote;

import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LotePaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LoteResponse;

/** Convierte entre el dominio y los DTO REST del lote. Crece con cada historia que expone nuevas respuestas. */
public final class LoteRestMapper {

    private LoteRestMapper() {
    }

    public static LoteResponse aResponse(Lote l) {
        return new LoteResponse(l.id(), l.nombre(), l.galponId(), l.fechaIngreso(), l.poblacionInicial(),
                l.poblacionActual(), l.costoTotal(),
                l.desvinculadoEn() == null ? null : l.desvinculadoEn().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime());
    }

    public static LotePaginaResponse aPagina(Pagina<Lote> p) {
        return new LotePaginaResponse(p.contenido().stream().map(LoteRestMapper::aResponse).toList(), p.pagina(),
                p.tamanio(), p.totalElementos(), p.totalPaginas());
    }
}
