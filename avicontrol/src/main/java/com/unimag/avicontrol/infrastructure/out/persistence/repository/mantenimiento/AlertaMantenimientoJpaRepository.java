package com.unimag.avicontrol.infrastructure.out.persistence.repository.mantenimiento;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.mantenimiento.AlertaMantenimientoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaMantenimientoJpaRepository extends JpaRepository<AlertaMantenimientoEntity, UUID> {
}
