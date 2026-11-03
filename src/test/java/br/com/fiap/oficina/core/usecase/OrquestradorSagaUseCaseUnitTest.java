package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.EstadoSaga;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.domain.mensageria.EventoSaga;
import br.com.fiap.oficina.core.gateway.MensageriaGateway.Destino;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrquestradorSagaUseCaseUnitTest {

    private static final Long OS = 7L;
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");
    private static final String ORCAMENTO = UUID.randomUUID().toString();

    @Mock MetricasGateway metricas;

    private FakesSaga.Mensageria mensageria;
    private FakesSaga.Sagas sagas;
    private FakesSaga.Ordens ordens;
    private MutavelClock clock;
    private OrquestradorSagaUseCase orquestrador;

    private static final class MutavelClock extends Clock {
        Instant agora = AGORA;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @BeforeEach
    void setUp() {
        mensageria = new FakesSaga.Mensageria();
        sagas = new FakesSaga.Sagas();
        ordens = new FakesSaga.Ordens();
        clock = new MutavelClock();
        orquestrador = new OrquestradorSagaUseCase(sagas, ordens, mensageria, new FakesSaga.SemTransacao(), () -> "corr-1", metricas,
                new OrquestradorSagaUseCase.Prazos(Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofMinutes(5)), clock);
    }

    private OrdemServico novaOs(boolean comItens) {
        List<OsItem> itens = new ArrayList<>();
        if (comItens) {
            itens.add(OsItem.builder().id(1L).produtoId(1L).produtoNome("Filtro").produtoTipo(TipoProduto.PECA)
                    .quantidade(2).precoUnitario(new BigDecimal("50.00")).build());
            itens.add(OsItem.builder().id(2L).produtoId(6L).produtoNome("Mão de obra").produtoTipo(TipoProduto.SERVICO)
                    .quantidade(1).precoUnitario(new BigDecimal("80.00")).build());
        }
        OrdemServico os = OrdemServico.builder().id(OS).numero("OS1").status(StatusOS.RECEBIDA)
                .cliente(Cliente.builder().nome("Maria").email("maria@email.com").build())
                .veiculo(Veiculo.builder().marca("Toyota").modelo("Corolla").placa("ABC1234").build())
                .descricaoProblema("Barulho").itens(itens).build();
        os.recalcularTotal();
        ordens.salvar(os);
        orquestrador.iniciar(os);
        return os;
    }

    private Saga saga() {
        return sagas.buscarPorOs(OS).orElseThrow();
    }

    private StatusOS statusOs() {
        return ordens.ordens.get(OS).getStatus();
    }

    private void evento(String tipo, Map<String, Object> payload) {
        orquestrador.processar(new EventoSaga(UUID.randomUUID(), tipo, saga().getId(), OS, payload));
    }

    private void evento(String tipo) {
        evento(tipo, Map.of());
    }

    private void vencerPrazo() {
        clock.agora = saga().getPrazoEm().plusSeconds(1);
        orquestrador.processarPrazosVencidos();
    }

    private void ateAguardarDecisao() {
        novaOs(true);
        evento("DiagnosticoEnfileirado");
        evento("DiagnosticoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));
        evento("OrcamentoGerado", Map.of("orcamentoId", ORCAMENTO, "valorTotal", "180.00", "expiraEm", AGORA.toString()));
    }

    private void ateBaixarEstoque() {
        ateAguardarDecisao();
        evento("OrcamentoAprovado", Map.of("orcamentoId", ORCAMENTO, "origem", "CLIENTE"));
    }

    private void ateGerarCobranca() {
        ateBaixarEstoque();
        evento("EstoqueBaixado", Map.of("movimentacoes", List.of()));
    }

    private void ateAguardarPagamento() {
        ateGerarCobranca();
        evento("CobrancaGerada", Map.of("mpOrderId", "ORD1", "expiraEm", AGORA.plus(Duration.ofHours(24)).toString()));
    }

    private void ateEnviarParaExecucao() {
        ateAguardarPagamento();
        evento("PagamentoConfirmado", Map.of("mpOrderId", "ORD1"));
    }

    private void ateEmReparo() {
        ateEnviarParaExecucao();
        evento("ReparoEnfileirado");
    }

    private void compensarAteOFim() {
        evento("EstoqueDevolvido", Map.of("movimentacoes", List.of()));
        evento("OrcamentoCancelado", Map.of("orcamentoId", ORCAMENTO));
    }

    private void assertCompensada() {
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.COMPENSADA);
        assertThat(statusOs()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga().getPrazoEm()).isNull();
    }

    @Test
    @DisplayName("Fluxo feliz: da abertura até a finalização, um comando por passo")
    void fluxoFeliz() {
        ateEmReparo();
        assertThat(statusOs()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.EM_REPARO);

        evento("ReparoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        assertThat(ordens.ordens.get(OS).getDataInicio()).isNotNull();
        evento("ReparoFinalizado", Map.of("finalizadoEm", AGORA.plusSeconds(60).toString()));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CONCLUIDA);
        assertThat(statusOs()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(mensageria.tipos()).containsExactly(
                "EnfileirarDiagnostico", "GerarOrcamento", "BaixarEstoque", "GerarCobranca", "EnfileirarReparo");
        assertThat(ordens.historico).extracting(h -> h.para()).containsExactly(
                StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO, StatusOS.AGUARDANDO_PAGAMENTO, StatusOS.EM_EXECUCAO, StatusOS.FINALIZADA);
    }

    @Test
    @DisplayName("Propaga saga, correlação e causa nos comandos")
    void propagaContexto() {
        ateAguardarDecisao();

        var comando = mensageria.ultimo();
        assertThat(comando.contexto().sagaId()).isEqualTo(saga().getId());
        assertThat(comando.contexto().correlationId()).isEqualTo("corr-1");
        assertThat(comando.contexto().causationId()).isNotNull();
    }

    @Test
    @DisplayName("Concluir o diagnóstico congela os itens e envia o orçamento completo (D15)")
    void orcamentoCompleto() {
        novaOs(true);
        evento("DiagnosticoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));

        assertThat(ordens.ordens.get(OS).isItensCongelados()).isTrue();
        var comando = mensageria.ultimo();
        assertThat(comando.destino()).isEqualTo(Destino.BILLING);
        assertThat(comando.payload()).containsEntry("valorTotal", "180.00").containsKeys("cliente", "veiculo", "itens");
    }

    @Test
    @DisplayName("A baixa leva só as peças, nunca os serviços")
    void baixaSoDePecas() {
        ateBaixarEstoque();

        var comando = mensageria.ultimo();
        assertThat(comando.tipo()).isEqualTo("BaixarEstoque");
        assertThat(comando.payload().get("itens")).isEqualTo(List.of(Map.of("produtoId", 1L, "quantidade", 2)));
        assertThat(ordens.ordens.get(OS).isAprovada()).isTrue();
    }

    @Test
    @DisplayName("OS sem itens encerra em FINALIZADA, sem orçamento nem cobrança (D16)")
    void semItensEncerra() {
        novaOs(false);
        evento("DiagnosticoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CONCLUIDA);
        assertThat(statusOs()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(mensageria.tipos()).containsExactly("EnfileirarDiagnostico");
    }

    @Test
    @DisplayName("F1: recusa leva a REPROVADA sem compensar estoque")
    void f1Recusa() {
        ateAguardarDecisao();
        evento("OrcamentoRecusado", Map.of("orcamentoId", ORCAMENTO));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.REPROVADA);
        assertThat(statusOs()).isEqualTo(StatusOS.REPROVADA);
        assertThat(mensageria.tipos()).doesNotContain("DevolverEstoque");
    }

    @Test
    @DisplayName("F2: falha ao gerar orçamento volta ao diagnóstico, descongela e permite concluir de novo (D17)")
    void f2FalhaOrcamento() {
        novaOs(true);
        evento("DiagnosticoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));
        evento("OrcamentoFalhou", Map.of("motivo", "Falha simulada"));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.EM_DIAGNOSTICO);
        assertThat(statusOs()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(ordens.ordens.get(OS).isItensCongelados()).isFalse();
        verify(metricas).falhaDeIntegracao("saga-orcamento", "GERACAO_FALHOU");

        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.GERANDO_ORCAMENTO);
    }

    @Test
    @DisplayName("F2 por prazo: sem resposta do Billing, volta ao diagnóstico")
    void f2PorPrazo() {
        novaOs(true);
        evento("DiagnosticoIniciado", Map.of("iniciadoEm", AGORA.toString()));
        evento("DiagnosticoConcluido", Map.of("concluidoEm", AGORA.toString()));

        vencerPrazo();

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("F3: em 7 dias pede a expiração; a resposta leva a CANCELADA (D9)")
    void f3SemResposta() {
        ateAguardarDecisao();
        assertThat(saga().getPrazoEm()).isEqualTo(AGORA.plus(Duration.ofDays(7)));

        vencerPrazo();
        assertThat(mensageria.ultimo().tipo()).isEqualTo("ExpirarOrcamento");
        assertThat(saga().getEstado()).as("o Billing decide quem venceu").isEqualTo(EstadoSaga.AGUARDANDO_DECISAO);

        evento("OrcamentoExpirado", Map.of("orcamentoId", ORCAMENTO));
        assertCompensada();
        assertThat(mensageria.tipos()).doesNotContain("DevolverEstoque");
    }

    @Test
    @DisplayName("Aprovação que chega depois de pedir a expiração segue o fluxo normal")
    void aprovacaoVenceExpiracao() {
        ateAguardarDecisao();
        vencerPrazo();

        evento("OrcamentoAprovado", Map.of("orcamentoId", ORCAMENTO));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.BAIXANDO_ESTOQUE);
    }

    @Test
    @DisplayName("F4: estoque insuficiente cancela o orçamento e avisa o cliente, sem devolver nada (D18, D20)")
    void f4EstoqueInsuficiente() {
        ateBaixarEstoque();
        evento("EstoqueInsuficiente", Map.of("faltantes", List.of()));

        assertThat(mensageria.ultimo().tipo()).isEqualTo("CancelarOrcamento");
        assertThat(mensageria.ultimo().payload()).containsEntry("avisarCliente", true).containsEntry("motivo", "Estoque insuficiente");
        evento("OrcamentoCancelado", Map.of());
        assertCompensada();
        assertThat(mensageria.tipos()).doesNotContain("DevolverEstoque");
    }

    @Test
    @DisplayName("F5: falha na cobrança encerra, devolve o estoque e cancela (D18)")
    void f5FalhaCobranca() {
        ateGerarCobranca();
        evento("CobrancaFalhou", Map.of("motivo", "Mercado Pago indisponível"));
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRANDO_COBRANCA);

        evento("PagamentoFalhou", Map.of("motivo", "CANCELADO"));
        assertThat(mensageria.ultimo().tipo()).isEqualTo("DevolverEstoque");
        compensarAteOFim();

        assertCompensada();
        assertThat(mensageria.tipos()).containsSubsequence("EncerrarCobranca", "DevolverEstoque", "CancelarOrcamento");
    }

    @Test
    @DisplayName("F5 por prazo na baixa: devolve por garantia, sem esperar a resposta")
    void f5PrazoNaBaixa() {
        ateBaixarEstoque();

        vencerPrazo();

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.DEVOLVENDO_ESTOQUE);
        compensarAteOFim();
        assertCompensada();
    }

    @Test
    @DisplayName("F5 por prazo na cobrança: encerra a cobrança")
    void f5PrazoNaCobranca() {
        ateGerarCobranca();

        vencerPrazo();

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRANDO_COBRANCA);
    }

    @Test
    @DisplayName("Cobrança encerrada que estava paga vira estorno")
    void encerrandoJaPago() {
        ateGerarCobranca();
        evento("CobrancaFalhou", Map.of("motivo", "E-mail"));

        evento("PagamentoConfirmado", Map.of());

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("F6: pagamento recusado ou QR expirado devolve o estoque e cancela (D2, D10)")
    void f6PagamentoFalhou() {
        ateAguardarPagamento();
        assertThat(statusOs()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);

        evento("PagamentoFalhou", Map.of("motivo", "EXPIRADO"));
        assertThat(mensageria.ultimo().tipo()).isEqualTo("DevolverEstoque");
        compensarAteOFim();

        assertCompensada();
        assertThat(mensageria.tipos()).doesNotContain("EstornarPagamento");
    }

    @Test
    @DisplayName("QR vencido sem webhook: consulta pelo prazo e aguarda a resposta do Billing")
    void qrVencidoSemWebhook() {
        ateAguardarPagamento();
        assertThat(saga().getPrazoEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(5)));

        vencerPrazo();

        assertThat(mensageria.ultimo().tipo()).isEqualTo("EncerrarCobranca");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_PAGAMENTO);

        evento("PagamentoConfirmado", Map.of());
        assertThat(saga().getEstado()).as("o cliente pagou no limite do prazo").isEqualTo(EstadoSaga.ENVIANDO_PARA_EXECUCAO);
    }

    @Test
    @DisplayName("F7: reparo recusado reembolsa, devolve e cancela (D19)")
    void f7ReparoRecusado() {
        ateEnviarParaExecucao();
        evento("ReparoRecusado", Map.of("motivo", "Falha simulada"));
        assertThat(mensageria.ultimo().tipo()).isEqualTo("EstornarPagamento");

        evento("PagamentoEstornado", Map.of());
        assertThat(mensageria.ultimo().tipo()).isEqualTo("DevolverEstoque");
        compensarAteOFim();

        assertCompensada();
    }

    @Test
    @DisplayName("F7 por prazo: cancela o reparo antes de reembolsar, para não deixar item fantasma")
    void f7PorPrazo() {
        ateEnviarParaExecucao();

        vencerPrazo();
        assertThat(mensageria.ultimo().tipo()).isEqualTo("CancelarReparo");

        evento("ReparoCancelado", Map.of());
        assertThat(mensageria.ultimo().tipo()).isEqualTo("EstornarPagamento");
    }

    @Test
    @DisplayName("F8: pagamento depois da compensação reembolsa e mantém CANCELADA (D19)")
    void f8PagamentoTardio() {
        ateAguardarPagamento();
        evento("PagamentoFalhou", Map.of("motivo", "EXPIRADO"));
        compensarAteOFim();

        evento("PagamentoConfirmado", Map.of("mpOrderId", "ORD1"));

        assertThat(mensageria.ultimo().tipo()).isEqualTo("EstornarPagamento");
        assertCompensada();
    }

    @Test
    @DisplayName("F9: estorno falhou deixa a Saga para intervenção, sem cancelar a OS")
    void f9EstornoFalhou() {
        ateEnviarParaExecucao();
        evento("ReparoRecusado", Map.of("motivo", "Falha"));
        StatusOS antes = statusOs();

        evento("EstornoFalhou", Map.of("motivo", "Mercado Pago fora"));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.COMPENSACAO_FALHOU);
        assertThat(statusOs()).isEqualTo(antes);
        verify(metricas).falhaDeIntegracao("saga-compensacao", "ESTORNO_FALHOU");

        orquestrador.reprocessar(OS);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNANDO_PAGAMENTO);
        assertThat(mensageria.ultimo().tipo()).isEqualTo("EstornarPagamento");
    }

    @Test
    @DisplayName("Reprocessar fora de COMPENSACAO_FALHOU é recusado")
    void reprocessarForaDeHora() {
        novaOs(true);

        assertThatThrownBy(() -> orquestrador.reprocessar(OS)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("F10: cancelar com reparo em andamento cancela, reembolsa, devolve e cancela (D22)")
    void f10Cancelamento() {
        ateEmReparo();
        evento("ReparoIniciado", Map.of("iniciadoEm", AGORA.toString()));

        orquestrador.solicitarCancelamento(OS, "Cliente desistiu", "operador");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_REPARO);
        assertThat(mensageria.ultimo().tipo()).isEqualTo("CancelarReparo");

        evento("ReparoCancelado", Map.of());
        assertThat(mensageria.ultimo().tipo()).isEqualTo("EstornarPagamento");
        evento("PagamentoEstornado", Map.of());
        compensarAteOFim();

        assertCompensada();
        assertThat(saga().getMotivo()).isEqualTo("Cliente desistiu");
    }

    @Test
    @DisplayName("F10: se a finalização chegou primeiro, a OS segue para FINALIZADA sem reembolso (D22)")
    void f10FinalizacaoVence() {
        ateEmReparo();
        orquestrador.solicitarCancelamento(OS, "Tarde", "operador");

        evento("ReparoJaFinalizado", Map.of("finalizadoEm", AGORA.toString()));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CONCLUIDA);
        assertThat(statusOs()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(mensageria.tipos()).doesNotContain("EstornarPagamento");
    }

    @Test
    @DisplayName("Cancelar fora do reparo responde erro de regra (D22)")
    void cancelarForaDoReparo() {
        ateAguardarDecisao();

        assertThatThrownBy(() -> orquestrador.solicitarCancelamento(OS, "x", "operador")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("PATCH /aprovar envia a decisão ao Billing; fora de hora responde conflito (D11, D23)")
    void decisaoDaOficina() {
        ateAguardarDecisao();

        orquestrador.solicitarDecisao(OS, true, "operador");
        assertThat(mensageria.ultimo().tipo()).isEqualTo("RegistrarDecisaoOrcamento");
        assertThat(mensageria.ultimo().payload()).containsEntry("aprovado", true).containsEntry("origem", "OFICINA");

        evento("OrcamentoAprovado", Map.of());
        assertThatThrownBy(() -> orquestrador.solicitarDecisao(OS, true, "operador")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mensagem duplicada não tem segundo efeito")
    void duplicada() {
        ateAguardarDecisao();
        int antes = mensageria.comandos.size();
        EventoSaga aprovado = new EventoSaga(UUID.randomUUID(), "OrcamentoAprovado", saga().getId(), OS, Map.of());

        orquestrador.processar(aprovado);
        orquestrador.processar(aprovado);

        assertThat(mensageria.comandos).hasSize(antes + 1);
    }

    @Test
    @DisplayName("Evento fora de ordem é registrado e não muda o estado")
    void foraDeOrdem() {
        novaOs(true);

        evento("EstoqueBaixado", Map.of());

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_DIAGNOSTICO);
        verify(metricas).eventoForaDeEstado("EstoqueBaixado", "AGUARDANDO_DIAGNOSTICO");
    }

    @Test
    @DisplayName("Saga inexistente é erro, para a mensagem ir à DLQ")
    void sagaInexistente() {
        EventoSaga evento = new EventoSaga(UUID.randomUUID(), "EstoqueBaixado", UUID.randomUUID(), OS, Map.of());

        assertThatThrownBy(() -> orquestrador.processar(evento)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> orquestrador.buscarPorOs(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Prazo não vencido não dispara nada")
    void prazoNaoVencido() {
        ateAguardarDecisao();
        int antes = mensageria.comandos.size();

        assertThat(orquestrador.processarPrazosVencidos()).isZero();

        assertThat(mensageria.comandos).hasSize(antes);
    }

    @Test
    @DisplayName("Saga reconstruída sobrevive: o estado vem do gateway, não da memória")
    void estadoPersistido() {
        ateAguardarDecisao();
        OrquestradorSagaUseCase reiniciado = new OrquestradorSagaUseCase(sagas, ordens, mensageria, new FakesSaga.SemTransacao(), () -> "novo", metricas,
                new OrquestradorSagaUseCase.Prazos(Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofMinutes(5)), clock);

        reiniciado.processar(new EventoSaga(UUID.randomUUID(), "OrcamentoAprovado", saga().getId(), OS, Map.of()));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.BAIXANDO_ESTOQUE);
    }
}
