package com.unimag.avicontrol.application.usecase.lote;

import com.unimag.avicontrol.application.port.in.lote.ListarLotesUseCase;
import com.unimag.avicontrol.application.port.in.lote.RegistrarLoteUseCase;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.policy.compartido.ReglasFecha;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso del lote. Crece con cada historia; por ahora registrar (US2) y listar. */
@Service
public class LoteService implements RegistrarLoteUseCase, ListarLotesUseCase {

    private static final String GALPON_NO_DISPONIBLE = "El galpón seleccionado no está disponible para recibir un lote.";

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final GalponEstadoService estados;
    private final Clock clock;

    public LoteService(GalponRepository galpones, LoteRepository lotes, GalponEstadoService estados, Clock clock) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.estados = estados;
        this.clock = clock;
    }

    /**
     * Una sola transacción: si el cambio de estado o el guardado del lote fallan, no queda nada (FR-012).
     * Orden de validación: nombre -> fecha futura -> galpón existe (404) -> nombre único (409) -> galpón DISPONIBLE (409).
     */
    @Override
    @Transactional
    public Lote registrar(RegistrarLoteUseCase.Comando comando) {
        NombreNormalizado nombre = NombreNormalizado.de(comando.nombre());
        if (comando.fechaIngreso().isAfter(ReglasFecha.hoy(clock))) {
            throw new ValidacionNegocioException("FECHA_INGRESO_FUTURA",
                    "La fecha de ingreso no puede ser posterior a hoy.");
        }
        Galpon galpon = galpones.findById(comando.galponId()).orElseThrow(() ->
                new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón seleccionado no existe."));
        if (lotes.existsByNombreClave(nombre.clave())) {
            throw ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre.");
        }
        if (galpon.estado() != EstadoGalpon.DISPONIBLE) {
            throw new ConflictoException("GALPON_NO_DISPONIBLE", GALPON_NO_DISPONIBLE);
        }

        // El cambio de estado va primero: toma el bloqueo del galpón y serializa a dos administradores que
        // registren un lote en el mismo galpón a la vez; el segundo ve el estado ya cambiado.
        try {
            estados.solicitarCambio(galpon.id(), EstadoGalpon.DISPONIBLE, EstadoGalpon.PRODUCTIVO,
                    OrigenCambioEstado.REGISTRO_LOTE);
        } catch (ConflictoException e) {
            if ("ESTADO_ESPERADO_CAMBIADO".equals(e.codigo())) {
                throw new ConflictoException("GALPON_NO_DISPONIBLE", GALPON_NO_DISPONIBLE);
            }
            throw e;
        }
        return lotes.save(Lote.nuevo(galpon.id(), nombre, comando.fechaIngreso(),
                comando.poblacionInicial(), comando.costoTotal()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Lote> listar(int pagina, int tamanio) {
        return lotes.findAll(pagina, tamanio);
    }
}
