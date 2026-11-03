package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.HistoricoStatus;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.gateway.TransacaoGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OrdemServicoUseCase {

    private static final System.Logger LOG = System.getLogger(OrdemServicoUseCase.class.getName());

    private static final Map<StatusOS, Integer> PRIORIDADE_LISTAGEM = Map.of(
            StatusOS.EM_EXECUCAO, 1,
            StatusOS.AGUARDANDO_PAGAMENTO, 2,
            StatusOS.AGUARDANDO_APROVACAO, 3,
            StatusOS.EM_DIAGNOSTICO, 4,
            StatusOS.RECEBIDA, 5);

    private final OrdemServicoGateway osGateway;
    private final ClienteGateway clienteGateway;
    private final VeiculoGateway veiculoGateway;
    private final ProdutoGateway produtoGateway;
    private final OrquestradorSagaUseCase orquestrador;
    private final TransacaoGateway transacao;
    private final MetricasGateway metricasGateway;

    public OrdemServicoUseCase(OrdemServicoGateway osGateway, ClienteGateway clienteGateway,
                               VeiculoGateway veiculoGateway, ProdutoGateway produtoGateway,
                               OrquestradorSagaUseCase orquestrador, TransacaoGateway transacao,
                               MetricasGateway metricasGateway) {
        this.osGateway = osGateway;
        this.clienteGateway = clienteGateway;
        this.veiculoGateway = veiculoGateway;
        this.produtoGateway = produtoGateway;
        this.orquestrador = orquestrador;
        this.transacao = transacao;
        this.metricasGateway = metricasGateway;
    }

    public record ItemNovo(Long produtoId, Integer quantidade, BigDecimal precoUnitario, String observacao) {}

    public OrdemServico criar(Long clienteId, Long veiculoId, String descricaoProblema,
                              String observacoes, List<ItemNovo> itens) {
        LOG.log(System.Logger.Level.INFO,
                "Recebida solicitação de abertura de OS para o cliente " + clienteId + " e veículo " + veiculoId);
        Cliente cliente = clienteGateway.buscarPorId(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        if (!cliente.estaAtivo()) {
            throw new RegraDeNegocioException("Não é possível abrir ordem de serviço para cliente inativo.");
        }
        Veiculo veiculo = veiculoGateway.buscarPorId(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", veiculoId));

        OrdemServico os = OrdemServico.builder()
                .numero("OS" + System.currentTimeMillis())
                .cliente(cliente)
                .veiculo(veiculo)
                .status(StatusOS.RECEBIDA)
                .descricaoProblema(descricaoProblema)
                .observacoes(observacoes)
                .itens(new ArrayList<>())
                .build();

        if (itens != null) {
            for (ItemNovo in : itens) {
                os.getItens().add(construirItem(in));
            }
            os.recalcularTotal();
        }
        LOG.log(System.Logger.Level.INFO,
                "Processando abertura da OS " + os.getNumero() + " com " + os.getItens().size() + " item(ns)");
        OrdemServico criada = transacao.executar(() -> {
            OrdemServico salva = osGateway.salvar(os);
            osGateway.registrarHistorico(new HistoricoStatus(salva.getId(), null, StatusOS.RECEBIDA, "OS aberta", LocalDateTime.now()));
            orquestrador.iniciar(salva);
            return salva;
        });
        metricasGateway.ordemServicoCriada();
        LOG.log(System.Logger.Level.INFO,
                "OS " + criada.getNumero() + " criada com sucesso, status " + criada.getStatus());
        return criada;
    }

    public OrdemServico buscarPorId(Long id) {
        return osGateway.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", id));
    }

    public OrdemServico buscarPorNumero(String numero) {
        return osGateway.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada com número: " + numero));
    }

    public OrdemServico buscarDoCliente(Long osId, Long clienteId) {
        return osGateway.buscarPorId(osId)
                .filter(os -> os.getCliente() != null && clienteId.equals(os.getCliente().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", osId));
    }

    public List<OrdemServico> listarTodas() {
        return osGateway.listarAtivas().stream()
                .sorted(Comparator
                        .comparing((OrdemServico os) -> PRIORIDADE_LISTAGEM.get(os.getStatus()))
                        .thenComparing(OrdemServico::getCriadoEm))
                .toList();
    }

    public List<OrdemServico> listarPorCliente(Long clienteId) {
        return osGateway.listarPorClienteId(clienteId);
    }

    public List<OrdemServico> listarPorStatus(StatusOS status) {
        return osGateway.listarPorStatus(status);
    }

    public List<HistoricoStatus> listarHistorico(Long osId) {
        buscarPorId(osId);
        return osGateway.listarHistorico(osId);
    }

    public Saga buscarSaga(Long osId) {
        return orquestrador.buscarPorOs(osId);
    }

    /** Só registra a entrega: os demais avanços pertencem à Saga e ao serviço de Execução (D21). */
    public OrdemServico avancarStatus(Long id) {
        OrdemServico os = buscarPorId(id);
        StatusOS anterior = os.getStatus();
        LOG.log(System.Logger.Level.INFO,
                "Recebida solicitação para avançar status da OS " + os.getNumero() + " (status atual: " + anterior + ")");
        Duration tempoNoStatusAnterior = os.tempoNoStatusAtual();
        os.entregar();
        OrdemServico salva = transacao.executar(() -> {
            OrdemServico gravada = osGateway.salvar(os);
            osGateway.registrarHistorico(new HistoricoStatus(gravada.getId(), anterior, gravada.getStatus(), "Veículo entregue", LocalDateTime.now()));
            return gravada;
        });
        metricasGateway.transicaoDeStatus(anterior, salva.getStatus(), tempoNoStatusAnterior);
        LOG.log(System.Logger.Level.INFO,
                "Ciclo de vida da OS " + salva.getNumero() + " concluído: entregue ao cliente");
        return salva;
    }

    public OrdemServico aprovar(Long id, boolean aprovado, String usuario) {
        OrdemServico os = buscarPorId(id);
        LOG.log(System.Logger.Level.INFO,
                "Recebida decisão de aprovação da OS " + os.getNumero() + ": aprovado=" + aprovado);
        orquestrador.solicitarDecisao(id, aprovado, usuario);
        return os;
    }

    public OrdemServico cancelar(Long id, String motivo, String usuario) {
        OrdemServico os = buscarPorId(id);
        LOG.log(System.Logger.Level.INFO, "Recebida solicitação de cancelamento da OS " + os.getNumero() + ": " + motivo);
        orquestrador.solicitarCancelamento(id, motivo, usuario);
        return os;
    }

    public void reprocessarSaga(Long osId) {
        orquestrador.reprocessar(osId);
    }

    public OrdemServico adicionarItem(Long osId, ItemNovo itemNovo) {
        LOG.log(System.Logger.Level.INFO, "Recebida solicitação para adicionar item à OS " + osId);
        OrdemServico os = buscarPorId(osId);
        OsItem item = construirItem(itemNovo);
        os.adicionarItem(item);
        OrdemServico salva = osGateway.salvar(os);
        LOG.log(System.Logger.Level.INFO,
                "Item adicionado à OS " + salva.getNumero() + ": produto " + item.getProdutoNome());
        return salva;
    }

    public OrdemServico removerItem(Long osId, Long itemId) {
        LOG.log(System.Logger.Level.INFO, "Recebida solicitação para remover item " + itemId + " da OS " + osId);
        OrdemServico os = buscarPorId(osId);
        OsItem item = os.getItens().stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado na OS informada"));
        os.removerItem(item);
        OrdemServico salva = osGateway.salvar(os);
        LOG.log(System.Logger.Level.INFO,
                "Item removido da OS " + salva.getNumero() + ": produto " + item.getProdutoNome());
        return salva;
    }

    public Double tempoMedioExecucao() {
        List<OrdemServico> finalizadas = osGateway.listarComTempoDeExecucao();
        if (finalizadas.isEmpty()) {
            return null;
        }
        double somaHoras = finalizadas.stream()
                .mapToDouble(os -> Duration.between(os.getDataInicio(), os.getDataFim()).getSeconds() / 3600.0)
                .sum();
        return somaHoras / finalizadas.size();
    }

    private OsItem construirItem(ItemNovo in) {
        Produto produto = produtoGateway.buscarPorId(in.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", in.produtoId()));
        return OsItem.builder()
                .produtoId(produto.id())
                .produtoNome(produto.nome())
                .produtoTipo(produto.tipo())
                .quantidade(in.quantidade())
                .precoUnitario(in.precoUnitario() != null ? in.precoUnitario() : produto.precoUnitario())
                .observacao(in.observacao())
                .build();
    }
}
