package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.HistoricoStatus;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoUseCaseUnitTest {

    @Mock OrdemServicoGateway osGateway;
    @Mock ClienteGateway clienteGateway;
    @Mock VeiculoGateway veiculoGateway;
    @Mock ProdutoGateway produtoGateway;
    @Mock OrquestradorSagaUseCase orquestrador;
    @Mock MetricasGateway metricas;

    private OrdemServicoUseCase useCase;
    private final Produto peca = new Produto(1L, "Filtro", TipoProduto.PECA, new BigDecimal("50.00"));
    private final Produto servico = new Produto(2L, "Balanceamento", TipoProduto.SERVICO, new BigDecimal("80.00"));

    @BeforeEach
    void setUp() {
        useCase = new OrdemServicoUseCase(osGateway, clienteGateway, veiculoGateway, produtoGateway, orquestrador,
                new FakesSaga.SemTransacao(), metricas);
    }

    private Cliente cliente() {
        return Cliente.builder().id(1L).nome("Maria").status(StatusCliente.ATIVO).build();
    }

    private OrdemServico os(StatusOS status) {
        return OrdemServico.builder().id(9L).numero("OS9").status(status).cliente(cliente())
                .veiculo(Veiculo.builder().id(1L).build()).itens(new ArrayList<>()).build();
    }

    private void salvarDevolveAOs() {
        when(osGateway.salvar(any())).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            if (os.getId() == null) {
                os.setId(9L);
            }
            return os;
        });
    }

    private void comClienteEVeiculo() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(cliente()));
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(Veiculo.builder().id(1L).build()));
    }

    @Test
    @DisplayName("Deve criar OS com status RECEBIDA, histórico, métrica e Saga iniciada na mesma operação")
    void deveCriar() {
        comClienteEVeiculo();
        salvarDevolveAOs();

        OrdemServico criada = useCase.criar(1L, 1L, "Barulho", null, null);

        assertThat(criada.getStatus()).isEqualTo(StatusOS.RECEBIDA);
        verify(orquestrador).iniciar(criada);
        verify(metricas).ordemServicoCriada();
        ArgumentCaptor<HistoricoStatus> historico = ArgumentCaptor.forClass(HistoricoStatus.class);
        verify(osGateway).registrarHistorico(historico.capture());
        assertThat(historico.getValue().para()).isEqualTo(StatusOS.RECEBIDA);
    }

    @Test
    @DisplayName("Deve copiar nome, tipo e preço do produto e calcular o total")
    void deveCopiarProduto() {
        comClienteEVeiculo();
        salvarDevolveAOs();
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(produtoGateway.buscarPorId(2L)).thenReturn(Optional.of(servico));

        OrdemServico criada = useCase.criar(1L, 1L, "Barulho", null, List.of(
                new OrdemServicoUseCase.ItemNovo(1L, 2, null, null),
                new OrdemServicoUseCase.ItemNovo(2L, 1, new BigDecimal("100.00"), "Com desconto")));

        assertThat(criada.getValorTotal()).isEqualByComparingTo("200.00");
        assertThat(criada.getItens().get(0).getProdutoNome()).isEqualTo("Filtro");
        assertThat(criada.getItens().get(0).getProdutoTipo()).isEqualTo(TipoProduto.PECA);
    }

    @Test
    @DisplayName("Não deve abrir OS para cliente inativo")
    void clienteInativo() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(Cliente.builder().id(1L).status(StatusCliente.INATIVO).build()));

        assertThatThrownBy(() -> useCase.criar(1L, 1L, "x", null, null)).isInstanceOf(RegraDeNegocioException.class);
        verify(orquestrador, never()).iniciar(any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar com cliente ou produto inexistente")
    void inexistentes() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.criar(1L, 1L, "x", null, null)).isInstanceOf(RecursoNaoEncontradoException.class);

        comClienteEVeiculo();
        when(produtoGateway.buscarPorId(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.criar(1L, 1L, "x", null, List.of(new OrdemServicoUseCase.ItemNovo(5L, 1, null, null))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Avançar status só registra a entrega, com métrica e histórico (D21)")
    void avancarEntrega() {
        OrdemServico os = os(StatusOS.FINALIZADA);
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os));
        salvarDevolveAOs();

        assertThat(useCase.avancarStatus(9L).getStatus()).isEqualTo(StatusOS.ENTREGUE);

        verify(metricas).transicaoDeStatus(eq(StatusOS.FINALIZADA), eq(StatusOS.ENTREGUE), any(Duration.class));
        verify(osGateway).registrarHistorico(any());
    }

    @Test
    @DisplayName("Avançar fora de FINALIZADA é recusado: o atalho de aprovação não existe mais (D11, D21)")
    void avancarRecusado() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.AGUARDANDO_APROVACAO)));

        assertThatThrownBy(() -> useCase.avancarStatus(9L)).isInstanceOf(RegraDeNegocioException.class);
        verify(osGateway, never()).salvar(any());
    }

    @Test
    @DisplayName("Aprovar e cancelar delegam à Saga, sem mudar o status na hora (D11, D22)")
    void delegaASaga() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.AGUARDANDO_APROVACAO)));

        assertThat(useCase.aprovar(9L, true, "operador").getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        verify(orquestrador).solicitarDecisao(9L, true, "operador");

        useCase.cancelar(9L, "Desistiu", "operador");
        verify(orquestrador).solicitarCancelamento(9L, "Desistiu", "operador");

        useCase.reprocessarSaga(9L);
        verify(orquestrador).reprocessar(9L);
    }

    @Test
    @DisplayName("Deve adicionar item à OS em diagnóstico")
    void adicionarItem() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.EM_DIAGNOSTICO)));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        salvarDevolveAOs();

        assertThat(useCase.adicionarItem(9L, new OrdemServicoUseCase.ItemNovo(1L, 1, null, null)).getItens()).hasSize(1);
    }

    @Test
    @DisplayName("Não deve adicionar nem remover item depois do diagnóstico (D3, D15)")
    void itensBloqueados() {
        OrdemServico paga = os(StatusOS.EM_EXECUCAO);
        paga.getItens().add(OsItem.builder().id(5L).produtoId(1L).produtoTipo(TipoProduto.PECA).build());
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(paga));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));

        assertThatThrownBy(() -> useCase.adicionarItem(9L, new OrdemServicoUseCase.ItemNovo(1L, 1, null, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> useCase.removerItem(9L, 5L)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Deve remover item; item de outra OS responde não encontrado")
    void removerItem() {
        OrdemServico os = os(StatusOS.RECEBIDA);
        os.getItens().add(OsItem.builder().id(5L).produtoId(1L).produtoTipo(TipoProduto.PECA).quantidade(1).precoUnitario(BigDecimal.TEN).build());
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os));
        salvarDevolveAOs();

        assertThatThrownBy(() -> useCase.removerItem(9L, 99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(useCase.removerItem(9L, 5L).getItens()).isEmpty();
    }

    @Test
    @DisplayName("Deve listar apenas OS ativas, ordenadas por status e mais antigas primeiro")
    void listagem() {
        LocalDateTime agora = LocalDateTime.now();
        OrdemServico recebida = os(StatusOS.RECEBIDA);
        recebida.setCriadoEm(agora);
        OrdemServico pagamento = os(StatusOS.AGUARDANDO_PAGAMENTO);
        pagamento.setCriadoEm(agora);
        OrdemServico execucaoNova = os(StatusOS.EM_EXECUCAO);
        execucaoNova.setCriadoEm(agora);
        OrdemServico execucaoAntiga = os(StatusOS.EM_EXECUCAO);
        execucaoAntiga.setCriadoEm(agora.minusDays(1));
        when(osGateway.listarAtivas()).thenReturn(List.of(recebida, pagamento, execucaoNova, execucaoAntiga));

        assertThat(useCase.listarTodas()).containsExactly(execucaoAntiga, execucaoNova, pagamento, recebida);
    }

    @Test
    @DisplayName("Deve listar OS por status e por cliente")
    void listarFiltros() {
        when(osGateway.listarPorStatus(StatusOS.CANCELADA)).thenReturn(List.of(os(StatusOS.CANCELADA)));
        when(osGateway.listarPorClienteId(1L)).thenReturn(List.of());

        assertThat(useCase.listarPorStatus(StatusOS.CANCELADA)).hasSize(1);
        assertThat(useCase.listarPorCliente(1L)).isEmpty();
    }

    @Test
    @DisplayName("Deve consultar histórico e Saga")
    void consultas() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.RECEBIDA)));
        when(osGateway.listarHistorico(9L)).thenReturn(List.of());
        Saga saga = Saga.builder().osId(9L).build();
        when(orquestrador.buscarPorOs(9L)).thenReturn(saga);

        assertThat(useCase.listarHistorico(9L)).isEmpty();
        assertThat(useCase.buscarSaga(9L)).isSameAs(saga);
    }

    @Test
    @DisplayName("Deve buscar por número; inexistente lança exceção")
    void buscarPorNumero() {
        when(osGateway.buscarPorNumero("OS9")).thenReturn(Optional.of(os(StatusOS.RECEBIDA)));
        when(osGateway.buscarPorNumero("X")).thenReturn(Optional.empty());

        assertThat(useCase.buscarPorNumero("OS9").getNumero()).isEqualTo("OS9");
        assertThatThrownBy(() -> useCase.buscarPorNumero("X")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Tempo médio conta do início do reparo até o fim; sem OS responde nulo")
    void tempoMedio() {
        when(osGateway.listarComTempoDeExecucao()).thenReturn(List.of());
        assertThat(useCase.tempoMedioExecucao()).isNull();

        LocalDateTime inicio = LocalDateTime.now();
        OrdemServico os = os(StatusOS.FINALIZADA);
        os.setDataInicio(inicio);
        os.setDataFim(inicio.plusHours(2));
        when(osGateway.listarComTempoDeExecucao()).thenReturn(List.of(os));
        assertThat(useCase.tempoMedioExecucao()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Deve devolver a OS quando ela pertence ao cliente autenticado")
    void doCliente() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.RECEBIDA)));

        assertThat(useCase.buscarDoCliente(9L, 1L).getId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("Deve responder como inexistente a OS de outro cliente")
    void deOutroCliente() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.of(os(StatusOS.RECEBIDA)));

        assertThatThrownBy(() -> useCase.buscarDoCliente(9L, 2L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando a OS não existe")
    void inexistente() {
        when(osGateway.buscarPorId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.buscarPorId(9L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
