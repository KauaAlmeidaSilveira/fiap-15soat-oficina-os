package br.com.fiap.oficina.dataprovider.persistence.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "os_historico_status")
@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HistoricoStatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "os_id", nullable = false)
    private Long osId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_de")
    private StatusOS de;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_para", nullable = false)
    private StatusOS para;

    @Column(length = 500)
    private String motivo;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime em;
}
