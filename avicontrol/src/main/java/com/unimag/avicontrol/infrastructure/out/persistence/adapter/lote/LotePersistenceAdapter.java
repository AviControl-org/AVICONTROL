package com.unimag.avicontrol.infrastructure.out.persistence.adapter.lote;

import com.unimag.avicontrol.infrastructure.out.persistence.adapter.compartido.RestriccionesUnicas;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.compartido.Pagina;
import com.unimag.avicontrol.domain.model.lote.Lote;
import com.unimag.avicontrol.domain.port.out.lote.LoteRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.lote.LotePersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.lote.LoteJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class LotePersistenceAdapter implements LoteRepository {

    private final LoteJpaRepository jpa;

    public LotePersistenceAdapter(LoteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Lote save(Lote lote) {
        try {
            return LotePersistenceMapper.aDominio(jpa.saveAndFlush(LotePersistenceMapper.aEntidad(lote)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.LOTE_NOMBRE)) {
                throw ConflictoException.nombreDuplicado("Ya existe un lote con ese nombre.");
            }
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.LOTE_ACTIVO_POR_GALPON)) {
                throw new ConflictoException("GALPON_NO_DISPONIBLE",
                        "El galpón seleccionado no está disponible para recibir un lote.");
            }
            throw e;
        }
    }

    @Override
    public Optional<Lote> findById(UUID id) {
        return jpa.findById(id).map(LotePersistenceMapper::aDominio);
    }

    @Override
    public Optional<Lote> findActivoByGalponId(UUID galponId) {
        return jpa.findFirstByGalponIdAndDesvinculadoEnIsNull(galponId).map(LotePersistenceMapper::aDominio);
    }

    @Override
    public boolean existsByNombreClave(String clave) {
        return jpa.existsByClave(clave);
    }

    @Override
    public Pagina<Lote> findAll(int pagina, int tamanio) {
        var peticion = PageRequest.of(pagina - 1, tamanio,
                Sort.by(Sort.Order.desc("fechaIngreso"), Sort.Order.desc("id")));
        var resultado = jpa.findAll(peticion);
        return new Pagina<>(resultado.getContent().stream().map(LotePersistenceMapper::aDominio).toList(),
                pagina, tamanio, resultado.getTotalElements());
    }

    @Override
    public boolean existsByGalponId(UUID galponId) {
        return jpa.existsByGalponId(galponId);
    }

    @Override
    public List<Lote> findByGalponIdOrderByFechaIngresoDesc(UUID galponId) {
        return jpa.findByGalponIdOrderByFechaIngresoDescIdDesc(galponId).stream()
                .map(LotePersistenceMapper::aDominio).toList();
    }
}
