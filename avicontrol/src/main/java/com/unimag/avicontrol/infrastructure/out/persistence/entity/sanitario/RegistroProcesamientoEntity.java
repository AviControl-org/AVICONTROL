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
@Table(name = "registro_procesamiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistroProcesamientoEntity {

    @Id
    private UUID alertaId;

    private UUID galponId;

    private UUID loteId;

    private long cantidadMuertos;

    private Instant fechaHoraEvento;

    private Instant procesadoEn;
}
