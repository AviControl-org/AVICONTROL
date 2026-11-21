package com.unimag.avicontrol.infrastructure.in.rest.controller.mantenimiento;

import com.unimag.avicontrol.application.port.in.mantenimiento.AlertaMantenimientoUseCase;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.ParametrosConsulta;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AlertaMantenimientoPaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AlertaMantenimientoResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AtenderAlertaRequest;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.AtenderAlertaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.mantenimiento.GenerarAlertaMantenimientoRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.mantenimiento.AlertaMantenimientoRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST: valida la forma, delega en el puerto y mapea. No contiene reglas de negocio. */
@RestController
@RequestMapping("/api/v1/alertas-mantenimiento")
public class AlertaMantenimientoController {

    private final AlertaMantenimientoUseCase alertas;

    public AlertaMantenimientoController(AlertaMantenimientoUseCase alertas) {
        this.alertas = alertas;
    }

    @PostMapping
    public ResponseEntity<AlertaMantenimientoResponse> generar(
            @Valid @RequestBody GenerarAlertaMantenimientoRequest request) {
        AlertaMantenimiento alerta = alertas.generar(new AlertaMantenimientoUseCase.Comando(
                request.galponId(), request.descripcion(), request.severidad(), request.tipo(), request.tecnicoId()));
        return ResponseEntity.created(URI.create("/api/v1/alertas-mantenimiento/" + alerta.id()))
                .body(AlertaMantenimientoRestMapper.aResponse(alerta));
    }

    @GetMapping
    public AlertaMantenimientoPaginaResponse listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) UUID galponId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + ParametrosConsulta.TAMANIO_PAGINA_POR_DEFECTO) int size) {
        return AlertaMantenimientoRestMapper.aPagina(alertas.listar(
                ParametrosConsulta.estadoAlerta(estado), galponId,
                ParametrosConsulta.pagina(page), ParametrosConsulta.tamanio(size)));
    }

    @PatchMapping("/{alertaId}")
    public AtenderAlertaResponse atender(@PathVariable UUID alertaId,
                                         @Valid @RequestBody AtenderAlertaRequest request) {
        return AlertaMantenimientoRestMapper.aAtencion(alertas.atender(alertaId));
    }
}
