package com.unimag.avicontrol.infrastructure.out.persistence.adapter.sanitario;

import com.unimag.avicontrol.domain.model.sanitario.RegistroProcesamiento;
import com.unimag.avicontrol.domain.port.out.sanitario.RegistroProcesamientoRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.compartido.AlertaPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.sanitario.RegistroProcesamientoJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RegistroProcesamientoPersistenceAdapter implements RegistroProcesamientoRepository {

    private final RegistroProcesamientoJpaRepository jpa;

    public RegistroProcesamientoPersistenceAdapter(RegistroProcesamientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RegistroProcesamiento save(RegistroProcesamiento registro) {
        return AlertaPersistenceMapper.aDominio(jpa.saveAndFlush(AlertaPersistenceMapper.aEntidad(registro)));
    }

    @Override
    public Optional<RegistroProcesamiento> findById(UUID alertaId) {
        return jpa.findById(alertaId).map(AlertaPersistenceMapper::aDominio);
    }
}
