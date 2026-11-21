package com.unimag.avicontrol.infrastructure.in.rest.mapper.galpon;

import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.TransicionesDisponiblesResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.HistorialEstadoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.HistorialEstadoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.CambioEstadoResponse;
import com.unimag.avicontrol.domain.model.compartido.ZonaNegocio;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase.TransicionesDisponibles;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.DetalleGalpon;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.HistorialLotes;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.Incidencia;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase.ResultadoListado;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.compartido.IncidenciaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponDetalleResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponResumenResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LoteHistorialResponse;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.lote.LoteRestMapper;
import java.time.LocalDate;
import java.util.List;

/** Convierte entre el dominio y los DTO REST. Crece con cada historia que expone nuevas respuestas. */
public final class GalponRestMapper {

    private static final String SIN_COINCIDENCIAS =
            "No se encontraron galpones con ese nombre. Se muestran todos los galpones.";
    private static final String SIN_LOTES = "No existen lotes registrados para este galpón";

    private GalponRestMapper() {
    }

    public static GalponResponse aResponse(Galpon galpon) {
        return new GalponResponse(galpon.id(), galpon.nombre(), galpon.capacidad(), galpon.estado());
    }

    public static GalponPaginaResponse aPagina(ResultadoListado resultado) {
        Pagina<Galpon> p = resultado.pagina();
        List<GalponResumenResponse> contenido = p.contenido().stream()
                .map(g -> new GalponResumenResponse(g.id(), g.nombre(), g.capacidad(), g.estado())).toList();
        return new GalponPaginaResponse(contenido, p.pagina(), p.tamanio(), p.totalElementos(), p.totalPaginas(),
                resultado.sinCoincidencias(), resultado.sinCoincidencias() ? SIN_COINCIDENCIAS : null);
    }

    public static GalponDetalleResponse aDetalle(DetalleGalpon d, LocalDate hoy) {
        Galpon g = d.galpon();
        return new GalponDetalleResponse(g.id(), g.nombre(), g.capacidad(), g.estado(),
                d.loteActivo() == null ? null : LoteRestMapper.aDetalle(d.loteActivo(), hoy),
                d.sinLotes() ? SIN_LOTES : null,
                aIncidencias(d.incidencias()));
    }

    public static LoteHistorialResponse aHistorial(HistorialLotes h, LocalDate hoy) {
        return new LoteHistorialResponse(h.galponId(),
                h.loteActivo() == null ? null : LoteRestMapper.aDetalle(h.loteActivo(), hoy),
                h.lotesAnteriores().stream().map(l -> LoteRestMapper.aDetalle(l, hoy)).toList(),
                aIncidencias(h.incidencias()));
    }

    /** Lista vacía = se omite el campo {@code incidencias} (convención de nulos omitidos). */
    private static List<IncidenciaResponse> aIncidencias(List<Incidencia> incidencias) {
        return incidencias.isEmpty() ? null
                : incidencias.stream().map(i -> new IncidenciaResponse(i.codigo(), i.mensaje())).toList();
    }

    public static CambioEstadoResponse aCambio(HistorialEstado h) {
        return new CambioEstadoResponse(h.galponId(), h.estadoAnterior(), h.estadoNuevo(), h.origen(),
                h.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime());
    }

    public static TransicionesDisponiblesResponse aTransiciones(TransicionesDisponibles t) {
        return new TransicionesDisponiblesResponse(t.galponId(), t.estadoActual(), t.destinos());
    }

    public static HistorialEstadoPaginaResponse aHistorialEstados(Pagina<HistorialEstado> p) {
        List<HistorialEstadoResponse> contenido = p.contenido().stream()
                .map(h -> new HistorialEstadoResponse(h.id(), h.galponId(), h.estadoAnterior(), h.estadoNuevo(),
                        h.fechaHora().atZone(ZonaNegocio.BOGOTA).toOffsetDateTime(), h.origen())).toList();
        return new HistorialEstadoPaginaResponse(contenido, p.pagina(), p.tamanio(), p.totalElementos(),
                p.totalPaginas());
    }
}
