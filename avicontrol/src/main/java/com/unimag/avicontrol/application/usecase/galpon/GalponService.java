package com.unimag.avicontrol.application.usecase.galpon;

import com.unimag.avicontrol.domain.port.out.galpon.HistorialEstadoRepository;
import com.unimag.avicontrol.domain.policy.galpon.TransicionesPermitidas;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
import com.unimag.avicontrol.domain.model.galpon.HistorialEstado;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.application.port.in.galpon.ActualizarEstadoUseCase;
import com.unimag.avicontrol.application.port.in.galpon.ConsultarGalponUseCase;
import com.unimag.avicontrol.application.port.in.galpon.RegistrarGalponUseCase;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.exception.RecursoNoEncontradoException;
import com.unimag.avicontrol.domain.exception.ValidacionNegocioException;
import com.unimag.avicontrol.domain.model.compartido.NombreNormalizado;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del galpón. Esta clase crece con cada historia: por ahora registra (US1), consulta (US3) y actualiza el
 * estado (US4); editar se agregan con sus planes, junto con sus dependencias.
 */
@Service
public class GalponService implements RegistrarGalponUseCase, ConsultarGalponUseCase, ActualizarEstadoUseCase {

    private final GalponRepository galpones;
    private final LoteRepository lotes;
    private final HistorialEstadoRepository historialEstados;
    private final GalponEstadoService estados;

    public GalponService(GalponRepository galpones, LoteRepository lotes, HistorialEstadoRepository historialEstados,
                         GalponEstadoService estados) {
        this.galpones = galpones;
        this.lotes = lotes;
        this.historialEstados = historialEstados;
        this.estados = estados;
    }

    @Override
    @Transactional
    public Galpon registrar(RegistrarGalponUseCase.Comando comando) {
        NombreNormalizado nombre = NombreNormalizado.de(comando.nombre());
        if (comando.capacidad() <= 0) {
            throw new ValidacionNegocioException("CAPACIDAD_INVALIDA",
                    "La capacidad debe ser un número entero mayor que cero.");
        }
        if (galpones.existsByNombreClave(nombre.clave())) {
            throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
        }
        // Si dos registros llegan a la vez, el índice único lower(nombre) lo rechaza y el adaptador
        // de persistencia lo traduce a este mismo NOMBRE_DUPLICADO.
        return galpones.save(Galpon.nuevo(nombre, comando.capacidad()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoListado listar(ConsultaGalpones consulta) {
        Pagina<Galpon> pagina = galpones.search(consulta);
        if (pagina.totalElementos() == 0 && consulta.nombre() != null) {
            // FR-005: sin coincidencias se muestran todos, conservando el filtro de estado y el orden (FR-019)
            return new ResultadoListado(galpones.search(consulta.sinNombre()), true);
        }
        return new ResultadoListado(pagina, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleGalpon detalle(UUID galponId) {
        Galpon galpon = galponConsultado(galponId);
        Lote activo = lotes.findActivoByGalponId(galponId).orElse(null);
        boolean sinLotes = activo == null && !lotes.existsByGalponId(galponId);
        return new DetalleGalpon(galpon, activo, sinLotes, incidenciasDe(activo));
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialLotes historialLotes(UUID galponId) {
        galponConsultado(galponId);
        List<Lote> todos = lotes.findByGalponIdOrderByFechaIngresoDesc(galponId);
        Lote activo = todos.stream().filter(Lote::activo).findFirst().orElse(null);
        List<Lote> anteriores = todos.stream().filter(l -> !l.activo()).toList();
        List<Incidencia> incidencias = new ArrayList<>(incidenciasDe(activo));
        anteriores.forEach(l -> incidencias.addAll(incidenciasDe(l)));
        return new HistorialLotes(galponId, activo, anteriores, incidencias);
    }

    private Galpon galponConsultado(UUID galponId) {
        return galpones.findById(galponId).orElseThrow(() -> new RecursoNoEncontradoException(
                "GALPON_NO_ENCONTRADO", "El galpón consultado no existe."));
    }

    /** Un campo vacío del lote no tumba la consulta: se omite en la respuesta y se informa aquí (FR-016). */
    private static List<Incidencia> incidenciasDe(Lote lote) {
        List<Incidencia> incidencias = new ArrayList<>();
        if (lote == null) {
            return incidencias;
        }
        if (lote.nombre() == null || lote.nombre().isBlank()) {
            incidencias.add(new Incidencia("CAMPO_NO_DISPONIBLE", "El nombre del lote no está disponible."));
        }
        if (lote.fechaIngreso() == null) {
            incidencias.add(new Incidencia("CAMPO_NO_DISPONIBLE",
                    "La fecha de ingreso del lote no está disponible; no se puede calcular la edad."));
        }
        return incidencias;
    }

    @Override
    @Transactional
    public HistorialEstado actualizarEstado(UUID galponId, EstadoGalpon estadoEsperado, EstadoGalpon estadoDestino) {
        Galpon galpon = galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        if (galpon.estado() != estadoEsperado) {
            throw new ConflictoException("ESTADO_ESPERADO_CAMBIADO",
                    "El estado del galpón cambió. Actualiza la información e inténtalo de nuevo.");
        }
        if (!TransicionesPermitidas.destinosManuales(galpon.estado()).contains(estadoDestino)) {
            // Existe en la matriz pero no es del administrador por este servicio -> origen; si no existe -> transición
            throw TransicionesPermitidas.existe(galpon.estado(), estadoDestino)
                    ? TransicionesPermitidas.origenNoAutorizado(OrigenCambioEstado.ADMINISTRADOR)
                    : TransicionesPermitidas.transicionNoPermitida();
        }
        // El estado se escribe solo en GalponEstadoService, que lo vuelve a validar bajo el bloqueo del galpón.
        return estados.solicitarCambio(galponId, estadoEsperado, estadoDestino, OrigenCambioEstado.ADMINISTRADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public TransicionesDisponibles transiciones(UUID galponId) {
        Galpon galpon = galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        return new TransicionesDisponibles(galponId, galpon.estado(),
                TransicionesPermitidas.destinosManuales(galpon.estado()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<HistorialEstado> historial(UUID galponId, int pagina, int tamanio) {
        galpones.findById(galponId)
                .orElseThrow(() -> new RecursoNoEncontradoException("GALPON_NO_ENCONTRADO", "El galpón no existe."));
        return historialEstados.findByGalponId(galponId, pagina, tamanio);
    }
}
