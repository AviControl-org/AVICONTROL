package com.unimag.avicontrol.infrastructure.out.persistence.adapter.sanitario;

import java.util.List;
import java.time.Instant;
import com.unimag.avicontrol.domain.model.sanitario.AlertaVaciadoSanitario;
import com.unimag.avicontrol.domain.port.out.sanitario.AlertaVaciadoSanitarioRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.compartido.AlertaPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.sanitario.AlertaVaciadoSanitarioJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertaVaciadoPersistenceAdapter implements AlertaVaciadoSanitarioRepository {

    private final AlertaVaciadoSanitarioJpaRepository jpa;

    public AlertaVaciadoPersistenceAdapter(AlertaVaciadoSanitarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AlertaVaciadoSanitario save(AlertaVaciadoSanitario alerta) {
        return AlertaPersistenceMapper.aDominio(jpa.saveAndFlush(AlertaPersistenceMapper.aEntidad(alerta)));
    }

    @Override
    public Optional<AlertaVaciadoSanitario> findById(UUID alertaId) {
        return jpa.findById(alertaId).map(AlertaPersistenceMapper::aDominio);
    }

    @Override
    public List<UUID> findGalponIdsConVaciadoCumplido(Instant limite) {
        return jpa.findGalponIdsConVaciadoCumplido(limite);
    }
}
