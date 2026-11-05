package com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon;

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
@Table(name = "historial_cambio_galpon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCambioGalponEntity {

    @Id
    private UUID id;

    private UUID galponId;

    private String campo;

    private String valorAnterior;

    private String valorNuevo;

    private Instant fechaHora;
}
