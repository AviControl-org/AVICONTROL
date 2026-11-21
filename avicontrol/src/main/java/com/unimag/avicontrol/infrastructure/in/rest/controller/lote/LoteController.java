package com.unimag.avicontrol.infrastructure.in.rest.controller.lote;

import com.unimag.avicontrol.application.port.in.lote.ListarLotesUseCase;
import com.unimag.avicontrol.application.port.in.lote.RegistrarLoteUseCase;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.infrastructure.in.rest.compartido.ParametrosConsulta;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LotePaginaResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.LoteResponse;
import com.unimag.avicontrol.infrastructure.in.rest.dto.lote.RegistrarLoteRequest;
import com.unimag.avicontrol.infrastructure.in.rest.mapper.lote.LoteRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lotes")
public class LoteController {

    private final RegistrarLoteUseCase registrarLote;
    private final ListarLotesUseCase listarLotes;

    public LoteController(RegistrarLoteUseCase registrarLote, ListarLotesUseCase listarLotes) {
        this.registrarLote = registrarLote;
        this.listarLotes = listarLotes;
    }

    @PostMapping
    public ResponseEntity<LoteResponse> registrar(@Valid @RequestBody RegistrarLoteRequest request) {
        Lote lote = registrarLote.registrar(new RegistrarLoteUseCase.Comando(request.galponId(), request.nombre(),
                request.fechaIngreso(), request.poblacionInicial(), request.costoTotal()));
        return ResponseEntity.created(URI.create("/api/v1/lotes/" + lote.id())).body(LoteRestMapper.aResponse(lote));
    }

    @GetMapping
    public LotePaginaResponse listar(@RequestParam(defaultValue = "1") int page) {
        return LoteRestMapper.aPagina(
                listarLotes.listar(ParametrosConsulta.pagina(page), ParametrosConsulta.TAMANIO_PAGINA_FIJO));
    }
}
