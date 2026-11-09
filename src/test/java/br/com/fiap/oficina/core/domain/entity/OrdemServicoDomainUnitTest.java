package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoDomainUnitTest {

    private OsItem item(TipoProduto tipo, int qtd, String preco) {
        return OsItem.builder().produtoId(1L).produtoNome("Filtro").produtoTipo(tipo)
                .quantidade(qtd).precoUnitario(new BigDecimal(preco)).build();
    }

    private OrdemServico os(StatusOS status) {
        return OrdemServico.builder().id(1L).numero("OS1").status(status).build();
    }

    @Test
    @DisplayName("Deve percorrer o fluxo completo até ENTREGUE, registrando as datas")
    void fluxoCompleto() {
        OrdemServico os = os(StatusOS.RECEBIDA);
        LocalDateTime inicio = LocalDateTime.now().minusHours(2);
        LocalDateTime fim = LocalDateTime.now();

        os.iniciarDiagnostico();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        os.aguardarAprovacao();
        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        os.registrarAprovacao();
        assertThat(os.isAprovada()).isTrue();
        assertThat(os.getStatus()).as("segue aguardando até a cobrança existir").isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        os.aguardarPagamento();
        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);
        os.registrarPagamento();
        assertThat(os.getDataPagamento()).isNotNull();
        os.entrarEmExecucao();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataInicio()).as("a fila não conta no tempo de execução (D23)").isNull();
        os.registrarInicioDoReparo(inicio);
        os.finalizar(fim);
        assertThat(os.getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(os.getDataInicio()).isEqualTo(inicio);
        assertThat(os.getDataFim()).isEqualTo(fim);
        os.entregar();
        assertThat(os.getStatus()).isEqualTo(StatusOS.ENTREGUE);
        assertThat(os.getDataEntrega()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = StatusOS.class, names = "FINALIZADA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Só OS FINALIZADA pode ser entregue (D21)")
    void soFinalizadaEntrega(StatusOS status) {
        assertThatThrownBy(() -> os(status).entregar()).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Transição fora de ordem é recusada")
    void foraDeOrdem() {
        assertThatThrownBy(() -> os(StatusOS.RECEBIDA).aguardarAprovacao()).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> os(StatusOS.RECEBIDA).entrarEmExecucao()).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> os(StatusOS.EM_DIAGNOSTICO).iniciarDiagnostico()).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> os(StatusOS.RECEBIDA).reprovar()).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Reprovar deve mover para REPROVADA")
    void reprovar() {
        OrdemServico os = os(StatusOS.AGUARDANDO_APROVACAO);
        os.reprovar();
        assertThat(os.getStatus()).isEqualTo(StatusOS.REPROVADA);
    }

    @Test
    @DisplayName("Cancelar deve mover para CANCELADA (D2, D9)")
    void cancelar() {
        OrdemServico os = os(StatusOS.AGUARDANDO_PAGAMENTO);
        os.cancelar();
        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
    }

    @Test
    @DisplayName("OS sem cobrança termina em FINALIZADA sem dataInicio, fora de tempo-medio (D16)")
    void encerrarSemCobranca() {
        OrdemServico os = os(StatusOS.EM_DIAGNOSTICO);
        assertThat(os.semCobranca()).isTrue();

        os.encerrarSemCobranca(LocalDateTime.now());

        assertThat(os.getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(os.getDataInicio()).isNull();
        assertThat(os.getDataFim()).isNotNull();
    }

    @Test
    @DisplayName("Sem cobrança: sem itens ou total zero, mas não com total positivo (D16)")
    void semCobranca() {
        OrdemServico os = os(StatusOS.EM_DIAGNOSTICO);
        os.adicionarItem(item(TipoProduto.PECA, 1, "0.00"));
        assertThat(os.semCobranca()).isTrue();
        os.adicionarItem(item(TipoProduto.SERVICO, 1, "10.00"));
        assertThat(os.semCobranca()).isFalse();
    }

    @Test
    @DisplayName("Adicionar item deve recalcular o total")
    void adicionarItem() {
        OrdemServico os = os(StatusOS.RECEBIDA);
        os.adicionarItem(item(TipoProduto.PECA, 2, "30.00"));
        assertThat(os.getValorTotal()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Remover item deve recalcular o total")
    void removerItem() {
        OrdemServico os = os(StatusOS.EM_DIAGNOSTICO);
        OsItem a = item(TipoProduto.PECA, 1, "30.00");
        os.adicionarItem(a);
        os.adicionarItem(item(TipoProduto.SERVICO, 1, "20.00"));
        os.removerItem(a);
        assertThat(os.getValorTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Itens congelam ao concluir o diagnóstico e descongelam se o orçamento falhar (D15, D17)")
    void congelamento() {
        OrdemServico os = os(StatusOS.EM_DIAGNOSTICO);
        OsItem existente = item(TipoProduto.PECA, 1, "30.00");
        os.adicionarItem(existente);

        os.congelarItens();
        assertThatThrownBy(() -> os.adicionarItem(item(TipoProduto.PECA, 1, "1.00"))).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> os.removerItem(existente)).isInstanceOf(RegraDeNegocioException.class);

        os.descongelarItens();
        os.adicionarItem(item(TipoProduto.PECA, 1, "1.00"));
        assertThat(os.getItens()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = StatusOS.class, names = {"RECEBIDA", "EM_DIAGNOSTICO"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Itens só mudam até o diagnóstico: inclui OS paga, finalizada e cancelada (D3, D15)")
    void itensBloqueadosDepoisDoDiagnostico(StatusOS status) {
        OrdemServico os = os(status);
        assertThatThrownBy(() -> os.adicionarItem(item(TipoProduto.PECA, 1, "1.00"))).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Mudança de status registra o instante")
    void registraInstante() {
        OrdemServico os = os(StatusOS.RECEBIDA);
        os.iniciarDiagnostico();
        assertThat(os.getStatusAlteradoEm()).isNotNull();
    }

    @Test
    @DisplayName("Tempo no status atual deve contar a partir da última mudança")
    void tempoDesdeMudanca() {
        OrdemServico os = OrdemServico.builder().statusAlteradoEm(LocalDateTime.now().minusHours(2))
                .criadoEm(LocalDateTime.now().minusDays(5)).build();
        assertThat(os.tempoNoStatusAtual()).isBetween(Duration.ofMinutes(119), Duration.ofMinutes(121));
    }

    @Test
    @DisplayName("Tempo no status atual deve usar a criação quando nunca houve transição")
    void tempoDesdeCriacao() {
        OrdemServico os = OrdemServico.builder().criadoEm(LocalDateTime.now().minusHours(3)).build();
        assertThat(os.tempoNoStatusAtual()).isBetween(Duration.ofMinutes(179), Duration.ofMinutes(181));
    }

    @Test
    @DisplayName("Tempo no status atual deve ser zero quando não há datas registradas")
    void tempoZero() {
        assertThat(OrdemServico.builder().build().tempoNoStatusAtual()).isEqualTo(Duration.ZERO);
    }
}
