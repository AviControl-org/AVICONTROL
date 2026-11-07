package com.unimag.avicontrol.infrastructure.out.persistence.repository.sanitario;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario.AlertaVaciadoSanitarioEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaVaciadoSanitarioJpaRepository extends JpaRepository<AlertaVaciadoSanitarioEntity, UUID> {
}
