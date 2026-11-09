package br.com.fiap.oficina.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.After;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class PassosSaga {

    private static final Random ALEATORIO = new Random();

    private final SistemaLocal sistema = new SistemaLocal();

    private String emailCliente;
    private Long clienteId;
    private Long veiculoId;
    private Long produtoId;
    private Long osId;
    private boolean simulacaoExecucao;
    private boolean simulacaoBilling;
    private boolean prazoCurto;
    private String idStubReembolso;

    @After
    public void desligarSimulacao() throws Exception {
        if (idStubReembolso != null) {
            sistema.enviar("DELETE", SistemaLocal.MERCADO_PAGO + "/__admin/mappings/" + idStubReembolso, null, false);
        }
        if (prazoCurto) {
            recriarOs("P7D");
        }
        if (simulacaoExecucao) {
            sistema.exigir("DELETE", SistemaLocal.EXECUCAO + "/api/simulacao/falha", null, 204);
        }
        if (simulacaoBilling) {
            sistema.exigir("DELETE", SistemaLocal.BILLING + "/api/simulacao/falha", null, 204);
        }
    }

    @Dado("um cliente com veículo cadastrado")
    public void clienteComVeiculo() {
        emailCliente = "cliente-" + UUID.randomUUID() + "@bdd.local";
        clienteId = sistema.exigir("POST", SistemaLocal.OS + "/api/clientes", Map.of(
                "nome", "Cliente BDD", "cpfCnpj", cpfValido(), "tipoDocumento", "CPF", "email", emailCliente), 201).get("id").asLong();
        veiculoId = sistema.exigir("POST", SistemaLocal.OS + "/api/veiculos", Map.of(
                "placa", placa(), "marca", "Toyota", "modelo", "Corolla", "ano", 2021, "cor", "Prata"), 201).get("id").asLong();
    }

    @Dado("uma peça com saldo {int} e preço {bigdecimal}")
    public void peca(int saldo, BigDecimal preco) {
        produtoId = sistema.exigir("POST", SistemaLocal.EXECUCAO + "/api/produtos", Map.of(
                "nome", "Peça BDD " + UUID.randomUUID(), "tipo", "PECA", "precoUnitario", preco,
                "unidadeMedida", "UN", "quantidadeInicial", saldo), 201).get("id").asLong();
    }

    @Dado("a falha {string} está ligada na Execução")
    public void ligarFalha(String ponto) {
        sistema.exigir("PUT", SistemaLocal.EXECUCAO + "/api/simulacao/falha?ponto=" + ponto, null, 204);
        simulacaoExecucao = true;
    }

    @Dado("a falha {string} está ligada no Billing")
    public void ligarFalhaNoBilling(String ponto) {
        sistema.exigir("PUT", SistemaLocal.BILLING + "/api/simulacao/falha?ponto=" + ponto, null, 204);
        simulacaoBilling = true;
    }

    @Quando("a falha {string} é desligada no Billing")
    public void desligarFalhaNoBilling(String ponto) {
        sistema.exigir("DELETE", SistemaLocal.BILLING + "/api/simulacao/falha", null, 204);
        simulacaoBilling = false;
    }

    @Quando("a oficina inicia e conclui o diagnóstico sem esperar o orçamento")
    public void diagnosticoSemEsperar() {
        iniciarDiagnostico();
        concluirDiagnostico();
    }

    @Quando("a oficina conclui de novo o diagnóstico")
    public void concluirDiagnostico() {
        sistema.exigir("POST", SistemaLocal.EXECUCAO + "/api/execucoes/" + osId + "/diagnostico/concluir", null, 200);
    }

    @Quando("o cliente recusa pelo link recebido por e-mail")
    public void recusarPeloLink() {
        decidirPeloLink("Recusar orçamento");
    }

    @Dado("o prazo de decisão do orçamento é de {int} segundos")
    public void prazoDeDecisao(int segundos) throws Exception {
        prazoCurto = true;
        recriarOs("PT" + segundos + "S");
    }

    @Dado("o Mercado Pago recusa reembolsos")
    public void recusarReembolsos() {
        idStubReembolso = sistema.ler(sistema.enviar("POST", SistemaLocal.MERCADO_PAGO + "/__admin/mappings", Map.of(
                "priority", 1,
                "request", Map.of("method", "POST", "urlPathPattern", "/v1/orders/[^/]+/refund"),
                "response", Map.of("status", 400)), false).corpo()).get("id").asText();
    }

    @Quando("o Mercado Pago volta a aceitar reembolsos")
    public void aceitarReembolsos() {
        sistema.enviar("DELETE", SistemaLocal.MERCADO_PAGO + "/__admin/mappings/" + idStubReembolso, null, false);
        idStubReembolso = null;
    }

    @Quando("o administrador reprocessa a Saga")
    public void reprocessar() {
        sistema.exigir("POST", SistemaLocal.OS + "/api/ordens-servico/" + osId + "/saga/reprocessar", null, 202);
    }

    @Quando("o Mercado Pago notifica um pagamento tardio")
    public void notificarPagamentoTardio() {
        responderOrder("processed");
        notificarOrder();
    }

    @Então("o link de aprovação não vale mais")
    public void linkInvalido() {
        String texto = sistema.esperar("E-mail de orçamento", () -> textoDoEmail("aguardando aprovação"), t -> t != null);
        Matcher link = Pattern.compile("Aprovar orçamento: (\\S+)").matcher(texto);
        assertThat(link.find()).isTrue();
        assertThat(sistema.enviar("GET", link.group(1), null, false).status()).isEqualTo(409);
    }

    private void recriarOs(String prazo) throws Exception {
        ProcessBuilder comando = new ProcessBuilder("docker", "compose", "up", "-d", "--no-deps", "--wait", "oficina-os").inheritIO();
        comando.environment().put("SAGA_PRAZO_DECISAO", prazo);
        assertThat(comando.start().waitFor()).isZero();
    }

    @Quando("o OS Service é reiniciado")
    public void reiniciarOs() throws Exception {
        Process processo = new ProcessBuilder("docker", "compose", "restart", "oficina-os").inheritIO().start();
        assertThat(processo.waitFor()).isZero();
        sistema.esperar("OS Service de volta",
                () -> { try { return sistema.enviar("GET", SistemaLocal.OS + "/actuator/health", null, false).status(); } catch (IllegalStateException e) { return 0; } },
                status -> status == 200);
    }

    @Quando("a recepção abre uma OS com {int} unidades da peça")
    public void abrirOs(int quantidade) {
        osId = sistema.exigir("POST", SistemaLocal.OS + "/api/ordens-servico", Map.of(
                "clienteId", clienteId, "veiculoId", veiculoId, "descricaoProblema", "Barulho ao frear",
                "itens", List.of(Map.of("produtoId", produtoId, "quantidade", quantidade))), 201).get("id").asLong();
    }

    @Quando("a oficina inicia e conclui o diagnóstico")
    public void diagnostico() {
        iniciarDiagnostico();
        concluirDiagnostico();
        // Com prazo curto a OS pode passar por AGUARDANDO_APROVACAO sem ser observada: o e-mail prova o envio.
        sistema.esperar("Orçamento enviado", () -> textoDoEmail("aguardando aprovação"), t -> t != null);
    }

    private void iniciarDiagnostico() {
        // O item da fila chega pela mensageria: repete até o comando ter sido processado.
        sistema.esperar("OS na fila de diagnóstico",
                () -> sistema.enviar("POST", SistemaLocal.EXECUCAO + "/api/execucoes/" + osId + "/diagnostico/iniciar", null, true).status(),
                status -> status == 200);
        esperarOs("EM_DIAGNOSTICO");
    }

    @Quando("o cliente aprova pelo link recebido por e-mail")
    public void aprovarPeloLink() {
        decidirPeloLink("Aprovar orçamento");
    }

    private void decidirPeloLink(String rotulo) {
        String texto = sistema.esperar("E-mail de orçamento",
                () -> textoDoEmail("aguardando aprovação"), t -> t != null);
        Matcher link = Pattern.compile(rotulo + ": (\\S+)").matcher(texto);
        assertThat(link.find()).as("link no e-mail").isTrue();
        SistemaLocal.Resposta resposta = sistema.enviar("GET", link.group(1), null, false);
        assertThat(resposta.status()).isEqualTo(200);
        SistemaLocal.Resposta repetida = sistema.enviar("GET", link.group(1), null, false);
        assertThat(repetida.status()).as("link só decide uma vez").isEqualTo(409);
    }

    @Quando("o Mercado Pago notifica o pagamento")
    public void notificarPagamento() {
        esperarOs("AGUARDANDO_PAGAMENTO");
        responderOrder("processed");
        notificarOrder();
    }

    @Quando("o Mercado Pago notifica que o QR expirou")
    public void notificarExpiracao() {
        responderOrder("expired");
        notificarOrder();
    }

    @Quando("a oficina inicia e finaliza o reparo")
    public void reparo() {
        iniciarReparo();
        esperarDataInicio();
        sistema.exigir("POST", SistemaLocal.EXECUCAO + "/api/execucoes/" + osId + "/reparo/finalizar", null, 200);
    }

    @Quando("a oficina inicia o reparo")
    public void iniciarReparo() {
        sistema.esperar("OS na fila de reparo",
                () -> sistema.enviar("POST", SistemaLocal.EXECUCAO + "/api/execucoes/" + osId + "/reparo/iniciar", null, true).status(),
                status -> status == 200);
    }

    @Quando("a oficina cancela a OS por {string}")
    public void cancelar(String motivo) {
        esperarDataInicio();
        sistema.exigir("POST", SistemaLocal.OS + "/api/ordens-servico/" + osId + "/cancelar", Map.of("motivo", motivo), 202);
    }

    @Quando("a oficina registra a entrega")
    public void entregar() {
        sistema.exigir("PATCH", SistemaLocal.OS + "/api/ordens-servico/" + osId + "/avancar-status", null, 200);
    }

    @Então("a OS fica {string} e a Saga {string}")
    public void estados(String statusOs, String estadoSaga) {
        esperarOs(statusOs);
        sistema.esperar("Saga da OS " + osId + " em " + estadoSaga,
                () -> sistema.exigir("GET", SistemaLocal.OS + "/api/ordens-servico/" + osId + "/saga", null, 200).get("estado").asText(),
                estadoSaga::equals);
    }

    @Então("o saldo da peça é {int}")
    public void saldo(int esperado) {
        sistema.esperar("Saldo da peça " + produtoId,
                () -> sistema.exigir("GET", SistemaLocal.EXECUCAO + "/api/produtos/" + produtoId, null, 200).get("saldoEstoque").asInt(),
                saldo -> saldo == esperado);
    }

    @Então("o cliente recebe o e-mail com o QR de pagamento")
    public void emailQr() {
        String texto = sistema.esperar("E-mail de cobrança", () -> textoDoEmail("Pagamento da OS"), t -> t != null);
        assertThat(texto).contains("Código copia-e-cola");
    }

    @Então("o cliente recebe o aviso de cancelamento")
    public void emailCancelamento() {
        sistema.esperar("Aviso de cancelamento", () -> textoDoEmail("cancelada"), t -> t != null);
    }

    @Então("o pagamento foi reembolsado no Mercado Pago")
    public void reembolsado() {
        sistema.esperar("Reembolso da order " + osId, () -> sistema.enviar("POST", SistemaLocal.MERCADO_PAGO + "/__admin/requests/count",
                        Map.of("method", "POST", "url", "/v1/orders/" + osId + "/refund"), false).corpo(),
                corpo -> sistema.ler(corpo).get("count").asInt() == 1);
    }

    @Então("o histórico da OS tem {int} mudanças de status")
    public void historico(int esperado) {
        JsonNode historico = sistema.exigir("GET", SistemaLocal.OS + "/api/ordens-servico/" + osId + "/historico", null, 200);
        assertThat(historico.size()).isEqualTo(esperado);
    }

    private void esperarOs(String status) {
        sistema.esperar("OS " + osId + " em " + status,
                () -> sistema.exigir("GET", SistemaLocal.OS + "/api/ordens-servico/" + osId, null, 200).get("status").asText(),
                status::equals);
    }

    private void esperarDataInicio() {
        esperarOs("EM_EXECUCAO");
        sistema.esperar("Início do reparo registrado",
                () -> sistema.exigir("GET", SistemaLocal.OS + "/api/ordens-servico/" + osId, null, 200).get("dataInicio"),
                data -> data != null && !data.isNull());
    }

    private void responderOrder(String status) {
        sistema.enviar("POST", SistemaLocal.MERCADO_PAGO + "/__admin/mappings", Map.of(
                "priority", 1,
                "request", Map.of("method", "GET", "url", "/v1/orders/" + osId),
                "response", Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                        "body", "{\"id\":\"" + osId + "\",\"status\":\"" + status + "\",\"external_reference\":\"" + osId
                                + "\",\"transactions\":{\"payments\":[{\"id\":\"PAY-" + osId + "\",\"status\":\"" + status + "\"}]}}")), false);
    }

    private void notificarOrder() {
        SistemaLocal.Resposta resposta = sistema.enviar("POST",
                SistemaLocal.BILLING + "/webhooks/mercado-pago?type=order&data.id=" + osId, null, false);
        assertThat(resposta.status()).isEqualTo(200);
    }

    /** Texto do e-mail do cliente deste cenário cujo assunto contém o trecho; null se ainda não chegou. */
    private String textoDoEmail(String assunto) {
        JsonNode mensagens = sistema.ler(sistema.enviar("GET", SistemaLocal.MAILPIT + "/api/v1/search?query="
                + sistema.codificar("to:\"" + emailCliente + "\""), null, false).corpo()).path("messages");
        for (JsonNode mensagem : mensagens) {
            if (mensagem.path("Subject").asText().contains(assunto)) {
                return sistema.ler(sistema.enviar("GET", SistemaLocal.MAILPIT + "/api/v1/message/" + mensagem.path("ID").asText(), null, false).corpo())
                        .path("Text").asText();
            }
        }
        return null;
    }

    private String placa() {
        return "BDD" + (1000 + ALEATORIO.nextInt(9000));
    }

    private String cpfValido() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = ALEATORIO.nextInt(10);
        }
        d[9] = digito(d, 9);
        d[10] = digito(d, 10);
        StringBuilder cpf = new StringBuilder();
        for (int n : d) {
            cpf.append(n);
        }
        return cpf.toString();
    }

    private int digito(int[] d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += d[i] * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
