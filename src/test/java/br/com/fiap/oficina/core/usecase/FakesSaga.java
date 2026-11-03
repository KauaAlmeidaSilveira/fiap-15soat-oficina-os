package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.HistoricoStatus;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.mensageria.ContextoSaga;
import br.com.fiap.oficina.core.gateway.MensageriaGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.SagaGateway;
import br.com.fiap.oficina.core.gateway.TransacaoGateway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class FakesSaga {

    private FakesSaga() {}

    record Comando(MensageriaGateway.Destino destino, String tipo, ContextoSaga contexto, Map<String, Object> payload) {}

    static class Mensageria implements MensageriaGateway {
        final List<Comando> comandos = new ArrayList<>();

        @Override
        public void enviarComando(Destino destino, String tipo, ContextoSaga contexto, Map<String, Object> payload) {
            comandos.add(new Comando(destino, tipo, contexto, payload));
        }

        List<String> tipos() {
            return comandos.stream().map(Comando::tipo).toList();
        }

        Comando ultimo() {
            return comandos.get(comandos.size() - 1);
        }
    }

    static class Sagas implements SagaGateway {
        final Map<UUID, Saga> sagas = new HashMap<>();
        final Set<UUID> inbox = new HashSet<>();

        @Override
        public Saga salvar(Saga saga) {
            sagas.put(saga.getId(), saga);
            return saga;
        }

        @Override
        public Optional<Saga> travarPorId(UUID id) {
            return Optional.ofNullable(sagas.get(id));
        }

        @Override
        public Optional<Saga> travarPorOs(Long osId) {
            return buscarPorOs(osId);
        }

        @Override
        public Optional<Saga> buscarPorOs(Long osId) {
            return sagas.values().stream().filter(s -> osId.equals(s.getOsId())).findFirst();
        }

        @Override
        public List<UUID> listarVencidas(Instant agora, int limite) {
            return sagas.values().stream()
                    .filter(s -> s.getPrazoEm() != null && !s.getPrazoEm().isAfter(agora))
                    .map(Saga::getId).limit(limite).toList();
        }

        @Override
        public boolean registrarMensagemSeNova(UUID mensagemId) {
            return inbox.add(mensagemId);
        }
    }

    static class Ordens implements OrdemServicoGateway {
        final Map<Long, OrdemServico> ordens = new HashMap<>();
        final List<HistoricoStatus> historico = new ArrayList<>();

        @Override
        public OrdemServico salvar(OrdemServico os) {
            ordens.put(os.getId(), os);
            return os;
        }

        @Override
        public Optional<OrdemServico> buscarPorId(Long id) {
            return Optional.ofNullable(ordens.get(id));
        }

        @Override
        public Optional<OrdemServico> buscarPorNumero(String numero) {
            return Optional.empty();
        }

        @Override
        public List<OrdemServico> listarAtivas() {
            return List.of();
        }

        @Override
        public List<OrdemServico> listarPorClienteId(Long clienteId) {
            return List.of();
        }

        @Override
        public List<OrdemServico> listarPorStatus(StatusOS status) {
            return List.of();
        }

        @Override
        public List<OrdemServico> listarComTempoDeExecucao() {
            return List.of();
        }

        @Override
        public void registrarHistorico(HistoricoStatus h) {
            historico.add(h);
        }

        @Override
        public List<HistoricoStatus> listarHistorico(Long osId) {
            return historico;
        }
    }

    static class SemTransacao implements TransacaoGateway {
        @Override
        public <T> T executar(Supplier<T> trabalho) {
            return trabalho.get();
        }
    }
}
