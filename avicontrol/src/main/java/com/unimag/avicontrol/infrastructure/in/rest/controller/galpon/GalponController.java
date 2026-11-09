package com.unimag.avicontrol.infrastructure.in.rest.controller.galpon;

import org.springframework.web.bind.annotation.PatchMapping;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.TransicionesDisponiblesResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.HistorialEstadoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.CambioEstadoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.ActualizarEstadoRequest;
import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.galpon.EditarGalponUseCase;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.EditarGalponRequest;
import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.policy.compartido.ReglasFecha;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.ParametrosConsulta;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponDetalleResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.RegistrarGalponRequest;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LoteHistorialResponse;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.galpon.GalponRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST: valida la forma, delega en el puerto y mapea. No contiene reglas de negocio. */
@RestController
@RequestMapping("/api/v1/galpones")
public class GalponController {

    private final RegistrarGalponUseCase registrarGalpon;
    private final ConsultarGalponUseCase consultarGalpon;
    private final ActualizarEstadoUseCase actualizarEstado;
    private final EditarGalponUseCase editarGalpon;
    private final Clock clock;

    public GalponController(RegistrarGalponUseCase registrarGalpon, ConsultarGalponUseCase consultarGalpon,
                            ActualizarEstadoUseCase actualizarEstado, EditarGalponUseCase editarGalpon, Clock clock) {
        this.registrarGalpon = registrarGalpon;
        this.consultarGalpon = consultarGalpon;
        this.actualizarEstado = actualizarEstado;
        this.editarGalpon = editarGalpon;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<GalponResponse> registrar(@Valid @RequestBody RegistrarGalponRequest request) {
        Galpon galpon = registrarGalpon.registrar(
                new RegistrarGalponUseCase.Comando(request.nombre(), request.capacidad()));
        return ResponseEntity.created(URI.create("/api/v1/galpones/" + galpon.id()))
                .body(GalponRestMapper.aResponse(galpon));
    }

    @GetMapping
    public GalponPaginaResponse listar(@RequestParam(required = false) String nombre,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(defaultValue = "nombre,asc") String orden,
                                       @RequestParam(defaultValue = "1") int page) {
        ParametrosConsulta.Orden ordenado = ParametrosConsulta.orden(orden);
        ConsultaGalpones consulta = new ConsultaGalpones(
                nombre == null || nombre.isBlank() ? null : nombre.strip(),
                ParametrosConsulta.estadoGalpon(estado),
                ordenado.campo(), ordenado.ascendente(),
                ParametrosConsulta.pagina(page), ParametrosConsulta.TAMANIO_PAGINA_FIJO);
        return GalponRestMapper.aPagina(consultarGalpon.listar(consulta));
    }

    @GetMapping("/{galponId}")
    public GalponDetalleResponse detalle(@PathVariable UUID galponId) {
        return GalponRestMapper.aDetalle(consultarGalpon.detalle(galponId), ReglasFecha.hoy(clock));
    }

    @GetMapping("/{galponId}/lotes")
    public LoteHistorialResponse historialLotes(@PathVariable UUID galponId) {
        return GalponRestMapper.aHistorial(consultarGalpon.historialLotes(galponId), ReglasFecha.hoy(clock));
    }

    @GetMapping("/{galponId}/transiciones")
    public TransicionesDisponiblesResponse transiciones(@PathVariable UUID galponId) {
        return GalponRestMapper.aTransiciones(actualizarEstado.transiciones(galponId));
    }

    @PatchMapping("/{galponId}/estado")
    public CambioEstadoResponse actualizarEstado(@PathVariable UUID galponId,
                                                 @Valid @RequestBody ActualizarEstadoRequest request) {
        return GalponRestMapper.aCambio(
                actualizarEstado.actualizarEstado(galponId, request.estadoEsperado(), request.estadoDestino()));
    }

    @GetMapping("/{galponId}/historial-estados")
    public HistorialEstadoPaginaResponse historialEstados(
            @PathVariable UUID galponId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + ParametrosConsulta.TAMANIO_PAGINA_POR_DEFECTO) int size) {
        return GalponRestMapper.aHistorialEstados(
                actualizarEstado.historial(galponId, ParametrosConsulta.pagina(page), ParametrosConsulta.tamanio(size)));
    }

    @PatchMapping("/{galponId}")
    public GalponResponse editar(@PathVariable UUID galponId, @Valid @RequestBody EditarGalponRequest request) {
        Galpon galpon = editarGalpon.editar(galponId,
                new EditarGalponUseCase.Comando(request.nombre(), request.capacidad()));
        return GalponRestMapper.aResponse(galpon);
    }
}
