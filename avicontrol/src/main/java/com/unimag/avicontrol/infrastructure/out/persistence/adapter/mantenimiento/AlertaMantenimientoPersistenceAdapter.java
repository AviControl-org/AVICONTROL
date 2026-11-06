package com.unimag.avicontrol.infrastructure.out.persistence.adapter.mantenimiento;

import com.unimag.avicontrol.infrastructure.out.persistence.adapter.compartido.RestriccionesUnicas;
import com.unimag.avicontrol.domain.exception.ConflictoException;
import com.unimag.avicontrol.domain.model.mantenimiento.AlertaMantenimiento;
import com.unimag.avicontrol.domain.port.out.mantenimiento.AlertaMantenimientoRepository;
import com.unimag.avicontrol.infrastructure.out.persistence.mapper.compartido.AlertaPersistenceMapper;
import com.unimag.avicontrol.infrastructure.out.persistence.repository.mantenimiento.AlertaMantenimientoJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class AlertaMantenimientoPersistenceAdapter implements AlertaMantenimientoRepository {

    private final AlertaMantenimientoJpaRepository jpa;

    public AlertaMantenimientoPersistenceAdapter(AlertaMantenimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AlertaMantenimiento save(AlertaMantenimiento alerta) {
        try {
            return AlertaPersistenceMapper.aDominio(jpa.saveAndFlush(AlertaPersistenceMapper.aEntidad(alerta)));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesUnicas.viola(e, RestriccionesUnicas.ALERTA_PENDIENTE_POR_GALPON)) {
                throw new ConflictoException("ALERTA_ACTIVA_EXISTENTE", "Ya existe una alerta activa para ese galpón.");
            }
            throw e;
        }
    }

    @Override
    public Optional<AlertaMantenimiento> findById(UUID id) {
        return jpa.findById(id).map(AlertaPersistenceMapper::aDominio);
    }
}
