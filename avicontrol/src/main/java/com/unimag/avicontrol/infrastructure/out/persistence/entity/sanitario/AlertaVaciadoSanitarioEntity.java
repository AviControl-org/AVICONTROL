package com.unimag.avicontrol.infrastructure.out.persistence.entity.sanitario;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alerta_vaciado_sanitario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertaVaciadoSanitarioEntity {

    @Id
    private UUID alertaId;

    private UUID galponId;

    private UUID loteId;

    private Instant fechaHoraEvento;

    private Instant fechaHoraRecepcion;

    private String resultado;
}
