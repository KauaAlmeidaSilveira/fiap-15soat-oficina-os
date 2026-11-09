package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaGateway {

    Saga salvar(Saga saga);

    /** Trava a saga até o fim da transação, serializando os eventos da mesma OS. */
    Optional<Saga> travarPorId(UUID id);

    Optional<Saga> travarPorOs(Long osId);

    Optional<Saga> buscarPorOs(Long osId);

    List<UUID> listarVencidas(Instant agora, int limite);

    boolean registrarMensagemSeNova(UUID mensagemId);
}
