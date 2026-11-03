package br.com.fiap.oficina.dataprovider.persistence.entity;

import br.com.fiap.oficina.core.domain.enums.EstadoSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SagaEntity {

    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false, unique = true)
    private Long osId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoSaga estado;

    @Column(name = "prazo_em")
    private Instant prazoEm;

    @Column(length = 500)
    private String motivo;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "orcamento_id")
    private UUID orcamentoId;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Version
    private Long versao;
}
