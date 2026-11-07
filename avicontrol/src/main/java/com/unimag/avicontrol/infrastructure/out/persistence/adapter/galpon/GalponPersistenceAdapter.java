package com.unimag.avicontrol.infrastructure.out.persistence.adapter.galpon;

import com.unimag.avicontrol.infrastructure.out.persistence.adapter.compartido.RestriccionesUnicas;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import com.unimag.avicontrol.domain.port.out.galpon.GalponRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.galpon.GalponPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.galpon.GalponJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
}
