package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.HistoricoStatus;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.domain.enums.EstadoSaga;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.domain.mensageria.ContextoSaga;
import br.com.fiap.oficina.core.domain.mensageria.EventoSaga;
import br.com.fiap.oficina.core.gateway.CorrelacaoGateway;
import br.com.fiap.oficina.core.gateway.MensageriaGateway;
import br.com.fiap.oficina.core.gateway.MensageriaGateway.Destino;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.SagaGateway;
import br.com.fiap.oficina.core.gateway.TransacaoGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Orquestrador da Saga da OS: cada método roda em uma transação com saga, OS, inbox e outbox. */
public class OrquestradorSagaUseCase {

    private static final System.Logger LOG = System.getLogger(OrquestradorSagaUseCase.class.getName());
    private static final int LOTE_VENCIDAS = 20;

    public record Prazos(Duration etapa, Duration decisao, Duration margemQr) {}

    private final SagaGateway sagaGateway;
    private final OrdemServicoGateway osGateway;
    private final MensageriaGateway mensageria;
    private final TransacaoGateway transacao;
    private final CorrelacaoGateway correlacao;
    private final MetricasGateway metricas;
    private final Prazos prazos;
    private final Clock clock;

    public OrquestradorSagaUseCase(SagaGateway sagaGateway, OrdemServicoGateway osGateway, MensageriaGateway mensageria,
                                   TransacaoGateway transacao, CorrelacaoGateway correlacao, MetricasGateway metricas,
                                   Prazos prazos, Clock clock) {
        this.sagaGateway = sagaGateway;
        this.osGateway = osGateway;
        this.mensageria = mensageria;
        this.transacao = transacao;
        this.correlacao = correlacao;
        this.metricas = metricas;
        this.prazos = prazos;
        this.clock = clock;
    }

    // --- Entradas ---

    public void iniciar(OrdemServico os) {
        Saga saga = Saga.builder()
                .id(UUID.randomUUID())
                .osId(os.getId())
                .estado(EstadoSaga.AGUARDANDO_DIAGNOSTICO)
                .correlationId(correlacao.atual())
                .build();
        sagaGateway.salvar(saga);
        Map<String, Object> payload = new HashMap<>();
        payload.put("numero", os.getNumero());
        payload.put("descricaoProblema", os.getDescricaoProblema());
        mensageria.enviarComando(Destino.EXECUCAO, "EnfileirarDiagnostico", contexto(saga, null), payload);
        LOG.log(System.Logger.Level.INFO, "Saga " + saga.getId() + " iniciada para a OS " + os.getId());
    }

    public void processar(EventoSaga evento) {
        transacao.executar(() -> {
            if (!sagaGateway.registrarMensagemSeNova(evento.messageId())) {
                LOG.log(System.Logger.Level.INFO, "Evento " + evento.messageId() + " (" + evento.tipo() + ") da OS "
                        + evento.osId() + " já processado");
                return null;
            }
            Saga saga = sagaGateway.travarPorId(evento.sagaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Saga " + evento.sagaId() + " da OS " + evento.osId() + " não encontrada"));
            aplicar(saga, evento);
            return null;
        });
    }

    public void solicitarDecisao(Long osId, boolean aprovado, String usuario) {
        transacao.executar(() -> {
            Saga saga = travarPorOs(osId);
            if (saga.getEstado() != EstadoSaga.AGUARDANDO_DECISAO) {
                throw new IllegalStateException("O orçamento da OS " + osId + " não está aguardando decisão.");
            }
            mensageria.enviarComando(Destino.BILLING, "RegistrarDecisaoOrcamento", contexto(saga, null),
                    Map.of("aprovado", aprovado, "origem", "OFICINA", "usuario", String.valueOf(usuario)));
            return null;
        });
    }

    public void solicitarCancelamento(Long osId, String motivo, String usuario) {
        transacao.executar(() -> {
            Saga saga = travarPorOs(osId);
            if (saga.getEstado() != EstadoSaga.EM_REPARO) {
                throw new RegraDeNegocioException(
                        "Só é possível cancelar a OS " + osId + " com o reparo em andamento.");
            }
            saga.setMotivo(motivo);
            cancelarReparo(saga, null, osGateway.buscarPorId(osId).orElseThrow());
            LOG.log(System.Logger.Level.INFO, "Cancelamento da OS " + osId + " solicitado por " + usuario + ": " + motivo);
            return null;
        });
    }

    public void reprocessar(Long osId) {
        transacao.executar(() -> {
            Saga saga = travarPorOs(osId);
            if (saga.getEstado() != EstadoSaga.COMPENSACAO_FALHOU) {
                throw new RegraDeNegocioException(
                        "A Saga da OS " + osId + " não está em COMPENSACAO_FALHOU.");
            }
            estornar(saga, null);
            return null;
        });
    }

    public Saga buscarPorOs(Long osId) {
        return sagaGateway.buscarPorOs(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Saga da OS", osId));
    }

    public int processarPrazosVencidos() {
        List<UUID> vencidas = sagaGateway.listarVencidas(clock.instant(), LOTE_VENCIDAS);
        vencidas.forEach(this::aoVencerPrazo);
        return vencidas.size();
    }

    // --- Prazos ---

    private void aoVencerPrazo(UUID sagaId) {
        transacao.executar(() -> {
            Saga saga = sagaGateway.travarPorId(sagaId).orElseThrow();
            // Outra réplica ou um evento pode ter avançado a saga entre a listagem e a trava.
            if (saga.getPrazoEm() == null || saga.getPrazoEm().isAfter(clock.instant())) {
                return null;
            }
            OrdemServico os = osGateway.buscarPorId(saga.getOsId()).orElseThrow();
            LOG.log(System.Logger.Level.WARNING, "Prazo vencido na Saga " + saga.getId() + " da OS " + saga.getOsId()
                    + " em " + saga.getEstado());
            saga.setMotivo("Prazo esgotado em " + saga.getEstado());
            switch (saga.getEstado()) {
                case GERANDO_ORCAMENTO -> voltarAoDiagnostico(saga, os);
                case AGUARDANDO_DECISAO -> {
                    saga.setPrazoEm(null);
                    mensageria.enviarComando(Destino.BILLING, "ExpirarOrcamento", contexto(saga, null),
                            Map.of("orcamentoId", saga.getOrcamentoId().toString()));
                    sagaGateway.salvar(saga);
                }
                // A baixa pode ter acontecido sem a resposta chegar: devolver é seguro, pois é idempotente.
                case BAIXANDO_ESTOQUE -> devolverEstoque(saga, null);
                case GERANDO_COBRANCA -> encerrarCobranca(saga, null);
                case AGUARDANDO_PAGAMENTO -> {
                    saga.setPrazoEm(null);
                    mensageria.enviarComando(Destino.BILLING, "EncerrarCobranca", contexto(saga, null),
                            Map.of("motivo", "QR expirado"));
                    sagaGateway.salvar(saga);
                }
                // O enfileiramento pode chegar depois: cancelar antes evita item fantasma na fila.
                case ENVIANDO_PARA_EXECUCAO -> cancelarReparo(saga, null, os);
                default -> {
                    saga.setPrazoEm(null);
                    sagaGateway.salvar(saga);
                }
            }
            return null;
        });
    }

    // --- Máquina de estados ---

    private void aplicar(Saga saga, EventoSaga e) {
        // Reembolso em qualquer fase de compensação ou terminal: o dinheiro já entrou (D19).
        if ("EstornoFalhou".equals(e.tipo())) {
            falharCompensacao(saga, e);
            return;
        }
        if ("PagamentoConfirmado".equals(e.tipo()) && pagamentoTardio(saga.getEstado())) {
            LOG.log(System.Logger.Level.WARNING, "Pagamento tardio da OS " + saga.getOsId() + ": reembolsando");
            mensageria.enviarComando(Destino.BILLING, "EstornarPagamento", contexto(saga, e),
                    Map.of("motivo", "Pagamento depois do cancelamento"));
            return;
        }

        OrdemServico os = osGateway.buscarPorId(saga.getOsId()).orElseThrow();
        switch (saga.getEstado() + ":" + e.tipo()) {
            case "AGUARDANDO_DIAGNOSTICO:DiagnosticoEnfileirado" -> { }
            case "AGUARDANDO_DIAGNOSTICO:DiagnosticoIniciado" -> {
                mudarOs(os, OrdemServico::iniciarDiagnostico, "Diagnóstico iniciado");
                mudarSaga(saga, EstadoSaga.EM_DIAGNOSTICO, null);
            }
            case "EM_DIAGNOSTICO:DiagnosticoConcluido" -> concluirDiagnostico(saga, os, e);
            case "GERANDO_ORCAMENTO:OrcamentoGerado" -> {
                saga.setOrcamentoId(UUID.fromString(texto(e, "orcamentoId")));
                mudarOs(os, OrdemServico::aguardarAprovacao, "Orçamento enviado ao cliente");
                mudarSaga(saga, EstadoSaga.AGUARDANDO_DECISAO, clock.instant().plus(prazos.decisao()));
            }
            case "GERANDO_ORCAMENTO:OrcamentoFalhou" -> {
                saga.setMotivo(texto(e, "motivo"));
                voltarAoDiagnostico(saga, os);
            }
            case "AGUARDANDO_DECISAO:OrcamentoAprovado" -> {
                mudarOs(os, OrdemServico::registrarAprovacao, "Orçamento aprovado");
                mensageria.enviarComando(Destino.EXECUCAO, "BaixarEstoque", contexto(saga, e), Map.of("itens", pecas(os)));
                mudarSaga(saga, EstadoSaga.BAIXANDO_ESTOQUE, prazoDeEtapa());
            }
            case "AGUARDANDO_DECISAO:OrcamentoRecusado" -> {
                mudarOs(os, OrdemServico::reprovar, "Orçamento recusado");
                mudarSaga(saga, EstadoSaga.REPROVADA, null);
            }
            case "AGUARDANDO_DECISAO:OrcamentoExpirado" -> {
                saga.setMotivo("Cliente não respondeu no prazo");
                mudarOs(os, OrdemServico::cancelar, saga.getMotivo());
                mudarSaga(saga, EstadoSaga.COMPENSADA, null);
            }
            case "BAIXANDO_ESTOQUE:EstoqueBaixado" -> {
                mensageria.enviarComando(Destino.BILLING, "GerarCobranca", contexto(saga, e), Map.of(
                        "orcamentoId", saga.getOrcamentoId().toString(), "valor", os.getValorTotal()));
                saga.setValor(os.getValorTotal());
                mudarSaga(saga, EstadoSaga.GERANDO_COBRANCA, prazoDeEtapa());
            }
            case "BAIXANDO_ESTOQUE:EstoqueInsuficiente" -> {
                saga.setMotivo("Estoque insuficiente");
                cancelarOrcamento(saga, e);
            }
            case "GERANDO_COBRANCA:CobrancaGerada" -> {
                mudarOs(os, OrdemServico::aguardarPagamento, "QR de pagamento enviado ao cliente");
                mudarSaga(saga, EstadoSaga.AGUARDANDO_PAGAMENTO, Instant.parse(texto(e, "expiraEm")).plus(prazos.margemQr()));
            }
            case "GERANDO_COBRANCA:CobrancaFalhou" -> {
                saga.setMotivo(texto(e, "motivo"));
                encerrarCobranca(saga, e);
            }
            case "AGUARDANDO_PAGAMENTO:PagamentoConfirmado" -> {
                mudarOs(os, OrdemServico::registrarPagamento, "Pagamento confirmado");
                mensageria.enviarComando(Destino.EXECUCAO, "EnfileirarReparo", contexto(saga, e), Map.of("numero", os.getNumero()));
                mudarSaga(saga, EstadoSaga.ENVIANDO_PARA_EXECUCAO, prazoDeEtapa());
            }
            case "AGUARDANDO_PAGAMENTO:PagamentoFalhou" -> {
                saga.setMotivo("Pagamento não realizado: " + texto(e, "motivo"));
                devolverEstoque(saga, e);
            }
            case "ENCERRANDO_COBRANCA:PagamentoFalhou" -> devolverEstoque(saga, e);
            case "ENCERRANDO_COBRANCA:PagamentoConfirmado" -> estornar(saga, e);
            case "ENVIANDO_PARA_EXECUCAO:ReparoEnfileirado" -> {
                mudarOs(os, OrdemServico::entrarEmExecucao, "Reparo na fila de execução");
                mudarSaga(saga, EstadoSaga.EM_REPARO, null);
            }
            case "ENVIANDO_PARA_EXECUCAO:ReparoRecusado" -> {
                saga.setMotivo("Falha ao enviar para execução: " + texto(e, "motivo"));
                estornar(saga, e);
            }
            case "EM_REPARO:ReparoIniciado", "CANCELANDO_REPARO:ReparoIniciado" -> {
                if (os.getStatus() == StatusOS.EM_EXECUCAO) {
                    os.registrarInicioDoReparo(data(texto(e, "iniciadoEm")));
                    osGateway.salvar(os);
                }
            }
            case "EM_REPARO:ReparoFinalizado", "CANCELANDO_REPARO:ReparoFinalizado", "CANCELANDO_REPARO:ReparoJaFinalizado" -> {
                LocalDateTime fim = data(texto(e, "finalizadoEm"));
                mudarOs(os, o -> o.finalizar(fim), "Reparo finalizado");
                mudarSaga(saga, EstadoSaga.CONCLUIDA, null);
            }
            case "CANCELANDO_REPARO:ReparoCancelado" -> estornar(saga, e);
            case "ESTORNANDO_PAGAMENTO:PagamentoEstornado" -> devolverEstoque(saga, e);
            case "DEVOLVENDO_ESTOQUE:EstoqueDevolvido" -> cancelarOrcamento(saga, e);
            case "CANCELANDO_ORCAMENTO:OrcamentoCancelado" -> {
                mudarOs(os, OrdemServico::cancelar, saga.getMotivo());
                mudarSaga(saga, EstadoSaga.COMPENSADA, null);
            }
            default -> {
                // Duplicada com outro ID ou fora de ordem: registra e confirma, sem mudar o estado.
                LOG.log(System.Logger.Level.WARNING, "Evento " + e.tipo() + " ignorado na Saga " + saga.getId()
                        + " da OS " + saga.getOsId() + " em " + saga.getEstado());
                metricas.eventoForaDeEstado(e.tipo(), saga.getEstado().name());
            }
        }
    }

    private boolean pagamentoTardio(EstadoSaga estado) {
        return switch (estado) {
            case DEVOLVENDO_ESTOQUE, CANCELANDO_ORCAMENTO, COMPENSADA, REPROVADA -> true;
            default -> false;
        };
    }

    private void concluirDiagnostico(Saga saga, OrdemServico os, EventoSaga e) {
        if (os.semCobranca()) {
            LocalDateTime fim = data(texto(e, "concluidoEm"));
            mudarOs(os, o -> o.encerrarSemCobranca(fim), "Diagnóstico concluído sem itens a cobrar");
            mudarSaga(saga, EstadoSaga.CONCLUIDA, null);
            return;
        }
        os.congelarItens();
        osGateway.salvar(os);
        mensageria.enviarComando(Destino.BILLING, "GerarOrcamento", contexto(saga, e), dadosDoOrcamento(os));
        mudarSaga(saga, EstadoSaga.GERANDO_ORCAMENTO, prazoDeEtapa());
    }

    private void voltarAoDiagnostico(Saga saga, OrdemServico os) {
        LOG.log(System.Logger.Level.ERROR, "Falha ao gerar o orçamento da OS " + saga.getOsId() + ": " + saga.getMotivo());
        metricas.falhaDeIntegracao("saga-orcamento", "GERACAO_FALHOU");
        os.descongelarItens();
        osGateway.salvar(os);
        mudarSaga(saga, EstadoSaga.EM_DIAGNOSTICO, null);
    }

    private void cancelarReparo(Saga saga, EventoSaga causa, OrdemServico os) {
        mensageria.enviarComando(Destino.EXECUCAO, "CancelarReparo", contexto(saga, causa), Map.of(
                "numero", os.getNumero(), "motivo", String.valueOf(saga.getMotivo())));
        mudarSaga(saga, EstadoSaga.CANCELANDO_REPARO, null);
    }

    private void encerrarCobranca(Saga saga, EventoSaga causa) {
        mensageria.enviarComando(Destino.BILLING, "EncerrarCobranca", contexto(saga, causa),
                Map.of("motivo", String.valueOf(saga.getMotivo())));
        mudarSaga(saga, EstadoSaga.ENCERRANDO_COBRANCA, null);
    }

    private void estornar(Saga saga, EventoSaga causa) {
        mensageria.enviarComando(Destino.BILLING, "EstornarPagamento", contexto(saga, causa),
                Map.of("motivo", String.valueOf(saga.getMotivo())));
        mudarSaga(saga, EstadoSaga.ESTORNANDO_PAGAMENTO, null);
    }

    private void devolverEstoque(Saga saga, EventoSaga causa) {
        mensageria.enviarComando(Destino.EXECUCAO, "DevolverEstoque", contexto(saga, causa),
                Map.of("motivo", String.valueOf(saga.getMotivo())));
        mudarSaga(saga, EstadoSaga.DEVOLVENDO_ESTOQUE, null);
    }

    private void cancelarOrcamento(Saga saga, EventoSaga causa) {
        // Toda compensação que passa por aqui é posterior à aprovação: o cliente é avisado (D20).
        mensageria.enviarComando(Destino.BILLING, "CancelarOrcamento", contexto(saga, causa), Map.of(
                "orcamentoId", String.valueOf(saga.getOrcamentoId()),
                "motivo", String.valueOf(saga.getMotivo()),
                "avisarCliente", true));
        mudarSaga(saga, EstadoSaga.CANCELANDO_ORCAMENTO, null);
    }

    private void falharCompensacao(Saga saga, EventoSaga e) {
        LOG.log(System.Logger.Level.ERROR, "Estorno falhou na Saga " + saga.getId() + " da OS " + saga.getOsId()
                + ": intervenção necessária. " + texto(e, "motivo"));
        metricas.falhaDeIntegracao("saga-compensacao", "ESTORNO_FALHOU");
        saga.setMotivo(texto(e, "motivo"));
        mudarSaga(saga, EstadoSaga.COMPENSACAO_FALHOU, null);
    }

    // --- Apoio ---

    private void mudarOs(OrdemServico os, Consumer<OrdemServico> acao, String motivo) {
        StatusOS anterior = os.getStatus();
        Duration tempoNoStatusAnterior = os.tempoNoStatusAtual();
        acao.accept(os);
        osGateway.salvar(os);
        if (os.getStatus() != anterior) {
            osGateway.registrarHistorico(new HistoricoStatus(os.getId(), anterior, os.getStatus(), motivo, LocalDateTime.now()));
            metricas.transicaoDeStatus(anterior, os.getStatus(), tempoNoStatusAnterior);
            LOG.log(System.Logger.Level.INFO, "OS " + os.getNumero() + " avançou de " + anterior + " para " + os.getStatus());
        }
    }

    private void mudarSaga(Saga saga, EstadoSaga novo, Instant prazo) {
        EstadoSaga anterior = saga.getEstado();
        saga.setEstado(novo);
        saga.setPrazoEm(prazo);
        sagaGateway.salvar(saga);
        metricas.sagaTransicao(anterior.name(), novo.name());
        LOG.log(System.Logger.Level.INFO, "Saga " + saga.getId() + " da OS " + saga.getOsId() + ": " + anterior + " -> " + novo);
    }

    private Saga travarPorOs(Long osId) {
        return sagaGateway.travarPorOs(osId).orElseThrow(() -> new RecursoNaoEncontradoException("Saga da OS", osId));
    }

    private Instant prazoDeEtapa() {
        return clock.instant().plus(prazos.etapa());
    }

    private ContextoSaga contexto(Saga saga, EventoSaga causa) {
        return new ContextoSaga(saga.getId(), saga.getOsId(), saga.getCorrelationId(), causa == null ? null : causa.messageId());
    }

    private List<Map<String, Object>> pecas(OrdemServico os) {
        return os.getItens().stream()
                .filter(i -> i.getProdutoTipo() == TipoProduto.PECA)
                .map(i -> Map.<String, Object>of("produtoId", i.getProdutoId(), "quantidade", i.getQuantidade()))
                .toList();
    }

    private Map<String, Object> dadosDoOrcamento(OrdemServico os) {
        Map<String, Object> cliente = new HashMap<>();
        cliente.put("nome", os.getCliente().getNome());
        cliente.put("email", os.getCliente().getEmail());
        Map<String, Object> veiculo = new HashMap<>();
        veiculo.put("marca", os.getVeiculo().getMarca());
        veiculo.put("modelo", os.getVeiculo().getModelo());
        veiculo.put("placa", os.getVeiculo().getPlaca());
        List<Map<String, Object>> itens = os.getItens().stream()
                .map(i -> Map.<String, Object>of("nomeProduto", i.getProdutoNome(), "quantidade", i.getQuantidade(),
                        "precoUnitario", i.getPrecoUnitario().toPlainString()))
                .toList();
        BigDecimal total = os.getValorTotal();
        return Map.of("numero", os.getNumero(), "valorTotal", total.toPlainString(),
                "cliente", cliente, "veiculo", veiculo, "itens", itens);
    }

    private String texto(EventoSaga e, String chave) {
        return String.valueOf(e.payload().get(chave));
    }

    private LocalDateTime data(String instante) {
        return LocalDateTime.ofInstant(Instant.parse(instante), ZoneId.systemDefault());
    }
}
