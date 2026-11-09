package com.unimag.avicontrol.infrastructure.out.persistence.repository.mantenimiento;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.mantenimiento.AlertaMantenimientoEntity;
import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaMantenimientoJpaRepository extends JpaRepository<AlertaMantenimientoEntity, UUID> {

    boolean existsByGalponIdAndEstado(UUID galponId, EstadoAlertaMantenimiento estado);

    Page<AlertaMantenimientoEntity> findByEstado(EstadoAlertaMantenimiento estado, Pageable pageable);

    Page<AlertaMantenimientoEntity> findByEstadoAndGalponId(EstadoAlertaMantenimiento estado, UUID galponId,
                                                           Pageable pageable);
}
