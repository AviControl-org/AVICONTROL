package com.unimag.avicontrol.infrastructure.in.rest.controller.galpon;

import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.GalponResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.galpon.RegistrarGalponRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.galpon.GalponRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/galpones")
public class GalponController {

    private final RegistrarGalponUseCase registrarGalpon;

    public GalponController(RegistrarGalponUseCase registrarGalpon) {
        this.registrarGalpon = registrarGalpon;
    }

    @PostMapping
    public ResponseEntity<GalponResponse> registrar(@Valid @RequestBody RegistrarGalponRequest request) {
        Galpon galpon = registrarGalpon.registrar(
                new RegistrarGalponUseCase.Comando(request.nombre(), request.capacidad()));
        return ResponseEntity.created(URI.create("/api/v1/galpones/" + galpon.id()))
                .body(GalponRestMapper.aResponse(galpon));
    }
}
