package com.unimag.avicontrol.infrastructure.out.persistence.repository.sanitario;

import com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario.RegistroProcesamientoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroProcesamientoJpaRepository extends JpaRepository<RegistroProcesamientoEntity, UUID> {
}
