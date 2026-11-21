package com.unimag.avicontrol.infrastructure.out.persistence.adapter.galpon;

import com.unimag.avicontrol.infrastructure.out.persistence.adapter.compartido.RestriccionesUnicas;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.galpon.ConsultaGalpones;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.galpon.GalponPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon.GalponJpaRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class GalponPersistenceAdapter implements GalponRepository {

    private final GalponJpaRepository jpa;

    public GalponPersistenceAdapter(GalponJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Galpon save(Galpon galpon) {
        try {
            // saveAndFlush: la violación del índice único debe aparecer aquí, no al confirmar la transacción.
            return GalponPersistenceMapper.aDominio(jpa.saveAndFlush(GalponPersistenceMapper.aEntidad(galpon)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.GALPON_NOMBRE)) {
                throw ConflictoException.nombreDuplicado("Ya existe un galpón con ese nombre.");
            }
            throw e;
        }
    }

    @Override
    public Optional<Galpon> findById(UUID id) {
        return jpa.findById(id).map(GalponPersistenceMapper::aDominio);
    }

    @Override
    public Optional<Galpon> findByIdForUpdate(UUID id) {
        return jpa.findByIdForUpdate(id).map(GalponPersistenceMapper::aDominio);
    }

    @Override
    public boolean existsByNombreClave(String clave) {
        return jpa.existsByClave(clave);
    }

    @Override
    public boolean existsByNombreClaveAndIdNot(String clave, UUID id) {
        return jpa.existsByClaveAndIdNot(clave, id);
    }

    @Override
    public Pagina<Galpon> search(ConsultaGalpones c) {
        List<EstadoGalpon> estados = c.estado() == null ? Arrays.asList(EstadoGalpon.values()) : List.of(c.estado());
        Sort.Direction sentido = c.ascendente() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order principal = new Sort.Order(sentido, switch (c.campo()) {
            case NOMBRE -> "nombre";
            case CAPACIDAD -> "capacidad";
            case ESTADO -> "estado";
        });
        if (c.campo() == ConsultaGalpones.CampoOrden.NOMBRE) {
            principal = principal.ignoreCase();
        }
        // Siempre se desempata por id: la paginación no repite ni omite galpones.
        var peticion = PageRequest.of(c.pagina() - 1, c.tamanio(), Sort.by(principal, Sort.Order.asc("id")));
        var resultado = jpa.buscar(estados, patron(c.nombre()), peticion);
        return new Pagina<>(resultado.getContent().stream().map(GalponPersistenceMapper::aDominio).toList(),
                c.pagina(), c.tamanio(), resultado.getTotalElements());
    }

    /** Búsqueda parcial sin distinguir mayúsculas; {@code %}, {@code _} y {@code \} del texto se escapan para no actuar de comodín. */
    private static String patron(String nombre) {
        if (nombre == null) {
            return "%";
        }
        String escapado = nombre.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
