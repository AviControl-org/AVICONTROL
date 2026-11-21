package com.unimag.avicontrol.infrastructure.out.persistence.entity.galpon;

import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.OrigenCambioEstado;
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
@Table(name = "historial_estado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEstadoEntity {

    @Id
    private UUID id;

    private UUID galponId;

    @Enumerated(EnumType.STRING)
    private EstadoGalpon estadoAnterior;

    @Enumerated(EnumType.STRING)
    private EstadoGalpon estadoNuevo;

    private Instant fechaHora;

    @Enumerated(EnumType.STRING)
    private OrigenCambioEstado origen;
}
