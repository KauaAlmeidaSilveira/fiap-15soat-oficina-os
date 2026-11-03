package br.com.fiap.oficina.entrypoint.mensageria;

import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.EstadoSaga;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.core.usecase.OrquestradorSagaUseCase;
import br.com.fiap.oficina.dataprovider.mensageria.Envelope;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class SagaFluxoIT {

    private static final String EVENTOS = "it-saga-eventos";
    private static final String BILLING = "it-billing-comandos";
    private static final String EXECUCAO = "it-execucao-comandos";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqs()) {
            sqs.createQueue(b -> b.queueName(EVENTOS));
            sqs.createQueue(b -> b.queueName(BILLING));
            sqs.createQueue(b -> b.queueName(EXECUCAO));
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("app.mensageria.habilitada", () -> "true");
        registry.add("app.mensageria.fila-eventos", () -> EVENTOS);
        registry.add("app.mensageria.fila-billing", () -> BILLING);
        registry.add("app.mensageria.fila-execucao", () -> EXECUCAO);
        registry.add("app.saga.prazo-etapa", () -> "PT3S");
        registry.add("app.saga.verificacao-prazos-ms", () -> "500");
        registry.add("spring.cloud.aws.sqs.enabled", () -> "true");
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    }

    private static SqsClient sqs() {
        return SqsClient.builder().endpointOverride(localstack.getEndpoint()).region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()))).build();
    }

    @Autowired OrdemServicoUseCase osUseCase;
    @Autowired OrquestradorSagaUseCase orquestrador;
    @Autowired ClienteRepository clienteRepository;
    @Autowired VeiculoRepository veiculoRepository;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProdutoGateway produtoGateway;

    private Envelope esperarComando(SqsClient sqs, String fila, String tipo) throws Exception {
        String url = sqs.getQueueUrl(b -> b.queueName(fila)).queueUrl();
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            for (Message m : sqs.receiveMessage(b -> b.queueUrl(url).waitTimeSeconds(1).maxNumberOfMessages(5)).messages()) {
                sqs.deleteMessage(b -> b.queueUrl(url).receiptHandle(m.receiptHandle()));
                Envelope comando = objectMapper.readValue(m.body(), Envelope.class);
                if (tipo.equals(comando.tipo())) {
                    return comando;
                }
            }
        }
        throw new AssertionError("Comando " + tipo + " não chegou na fila " + fila);
    }

    private void publicarEvento(SqsClient sqs, Envelope causa, String tipo, Object payload) throws Exception {
        Envelope evento = new Envelope(UUID.randomUUID(), tipo, 1, causa.sagaId(), causa.osId(), causa.correlationId(),
                causa.messageId(), Instant.now(), objectMapper.valueToTree(payload));
        String url = sqs.getQueueUrl(b -> b.queueName(EVENTOS)).queueUrl();
        String corpo = objectMapper.writeValueAsString(evento);
        sqs.sendMessage(b -> b.queueUrl(url).messageBody(corpo));
    }

    private void esperarEstado(Long osId, EstadoSaga estado) throws Exception {
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            if (orquestrador.buscarPorOs(osId).getEstado() == estado) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(orquestrador.buscarPorOs(osId).getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("OS atravessa filas reais; sem resposta do Billing, o prazo persistido devolve ao diagnóstico")
    void fluxoPorFilasEPrazo() throws Exception {
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(new Produto(1L, "Filtro", TipoProduto.PECA, new BigDecimal("30.00"))));
        Long clienteId = clienteRepository.save(Cliente.builder().nome("Maria").cpfCnpj("71498053297")
                .tipoDocumento(TipoDocumento.CPF).email("maria@email.com").build()).getId();
        Long veiculoId = veiculoRepository.save(Veiculo.builder().placa("ABC1234").marca("Toyota").modelo("Corolla").ano(2021).cor("Prata").build()).getId();

        Long osId = osUseCase.criar(clienteId, veiculoId, "Barulho", null, List.of(new OrdemServicoUseCase.ItemNovo(1L, 2, null, null))).getId();

        try (SqsClient sqs = sqs()) {
            Envelope enfileirar = esperarComando(sqs, EXECUCAO, "EnfileirarDiagnostico");
            assertThat(enfileirar.osId()).isEqualTo(osId);

            publicarEvento(sqs, enfileirar, "DiagnosticoIniciado", Map.of("iniciadoEm", Instant.now().toString()));
            esperarEstado(osId, EstadoSaga.EM_DIAGNOSTICO);

            publicarEvento(sqs, enfileirar, "DiagnosticoConcluido", Map.of("concluidoEm", Instant.now().toString()));
            Envelope orcamento = esperarComando(sqs, BILLING, "GerarOrcamento");
            assertThat(orcamento.payload().path("valorTotal").asText()).isEqualTo("60.00");

            esperarEstado(osId, EstadoSaga.EM_DIAGNOSTICO);
            assertThat(orquestrador.buscarPorOs(osId).getMotivo()).contains("Prazo esgotado");
        }
    }
}
