package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.EstadoSaga;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Saga {

    private UUID id;
    private Long osId;
    private EstadoSaga estado;
    private Instant prazoEm;
    private String motivo;
    private String correlationId;
    private UUID orcamentoId;
    private BigDecimal valor;

    public boolean terminal() {
        return estado == EstadoSaga.CONCLUIDA || estado == EstadoSaga.REPROVADA
                || estado == EstadoSaga.COMPENSADA || estado == EstadoSaga.COMPENSACAO_FALHOU;
    }
}
