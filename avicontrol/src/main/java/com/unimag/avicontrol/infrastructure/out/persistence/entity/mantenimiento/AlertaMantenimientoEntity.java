package com.unimag.avicontrol.infrastructure.out.persistence.entity.mantenimiento;

import com.unimag.avicontrol.domain.model.mantenimiento.EstadoAlertaMantenimiento;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alerta_mantenimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertaMantenimientoEntity {

    @Id
    private UUID id;

    private UUID galponId;

    private String descripcion;

    private Instant fechaHora;

    private String tecnicoId;

    @Enumerated(EnumType.STRING)
    private EstadoAlertaMantenimiento estado;

    private String severidad;

    private String tipo;
}
