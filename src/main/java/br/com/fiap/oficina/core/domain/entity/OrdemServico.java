package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdemServico {

    private Long id;
    private String numero;
    private Cliente cliente;
    private Veiculo veiculo;

    @Builder.Default
    private StatusOS status = StatusOS.RECEBIDA;

    private String descricaoProblema;
    private String observacoes;

    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private LocalDateTime dataInicio;
    private LocalDateTime dataFim;
    private LocalDateTime dataEntrega;
    private LocalDateTime dataAprovacao;
    private LocalDateTime dataPagamento;
    private boolean itensCongelados;
    private LocalDateTime statusAlteradoEm;
    private LocalDateTime criadoEm;

    @Builder.Default
    private List<OsItem> itens = new ArrayList<>();

    /** Único avanço manual que resta: diagnóstico e reparo são do serviço de Execução (D21). */
    public void entregar() {
        if (status != StatusOS.FINALIZADA) {
            throw new RegraDeNegocioException(
                    "A OS precisa estar FINALIZADA para ser entregue. Diagnóstico e reparo avançam pelas rotas de Execução.");
        }
        dataEntrega = LocalDateTime.now();
        mudarPara(StatusOS.ENTREGUE);
    }

    public void iniciarDiagnostico() {
        exigir(StatusOS.RECEBIDA);
        mudarPara(StatusOS.EM_DIAGNOSTICO);
    }

    public void aguardarAprovacao() {
        exigir(StatusOS.EM_DIAGNOSTICO);
        mudarPara(StatusOS.AGUARDANDO_APROVACAO);
    }

    public void registrarAprovacao() {
        exigir(StatusOS.AGUARDANDO_APROVACAO);
        dataAprovacao = LocalDateTime.now();
    }

    public void aguardarPagamento() {
        exigir(StatusOS.AGUARDANDO_APROVACAO);
        mudarPara(StatusOS.AGUARDANDO_PAGAMENTO);
    }

    public void registrarPagamento() {
        exigir(StatusOS.AGUARDANDO_PAGAMENTO);
        dataPagamento = LocalDateTime.now();
    }

    public void entrarEmExecucao() {
        exigir(StatusOS.AGUARDANDO_PAGAMENTO);
        mudarPara(StatusOS.EM_EXECUCAO);
    }

    public void registrarInicioDoReparo(LocalDateTime inicio) {
        exigir(StatusOS.EM_EXECUCAO);
        dataInicio = inicio;
    }

    public void finalizar(LocalDateTime fim) {
        exigir(StatusOS.EM_EXECUCAO);
        dataFim = fim;
        mudarPara(StatusOS.FINALIZADA);
    }

    /** OS sem itens ou com total zero termina no diagnóstico (D16): dataInicio vazia a mantém fora de tempo-medio. */
    public void encerrarSemCobranca(LocalDateTime fim) {
        exigir(StatusOS.EM_DIAGNOSTICO);
        dataFim = fim;
        mudarPara(StatusOS.FINALIZADA);
    }

    public void reprovar() {
        exigir(StatusOS.AGUARDANDO_APROVACAO);
        mudarPara(StatusOS.REPROVADA);
    }

    public void cancelar() {
        mudarPara(StatusOS.CANCELADA);
    }

    public void congelarItens() {
        itensCongelados = true;
    }

    public void descongelarItens() {
        itensCongelados = false;
    }

    public boolean semCobranca() {
        return itens.isEmpty() || valorTotal == null || valorTotal.signum() == 0;
    }

    public void adicionarItem(OsItem item) {
        exigirItensEditaveis();
        itens.add(item);
        recalcularTotal();
    }

    public void removerItem(OsItem item) {
        exigirItensEditaveis();
        itens.remove(item);
        recalcularTotal();
    }

    public void recalcularTotal() {
        valorTotal = itens.stream().map(OsItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Duration tempoNoStatusAtual() {
        LocalDateTime desde = statusAlteradoEm != null ? statusAlteradoEm : criadoEm;
        return desde != null ? Duration.between(desde, LocalDateTime.now()) : Duration.ZERO;
    }

    public boolean isAprovada() {
        return dataAprovacao != null;
    }

    private void exigirItensEditaveis() {
        boolean editavel = !itensCongelados && (status == StatusOS.RECEBIDA || status == StatusOS.EM_DIAGNOSTICO);
        if (!editavel) {
            throw new RegraDeNegocioException("Os itens da OS não podem mais mudar depois de concluído o diagnóstico.");
        }
    }

    private void exigir(StatusOS esperado) {
        if (status != esperado) {
            throw new RegraDeNegocioException("A OS " + id + " precisa estar " + esperado + ", mas está " + status + ".");
        }
    }

    private void mudarPara(StatusOS novo) {
        status = novo;
        statusAlteradoEm = LocalDateTime.now();
    }
}
