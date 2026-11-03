package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.ServicoIndisponivelException;
import br.com.fiap.oficina.core.domain.mensageria.EventoSaga;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.usecase.OrquestradorSagaUseCase;
import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.dto.request.CancelarOsRequest;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = {"ADMIN", "RECEPCAO", "OPERADOR"})
class OrdemServicoControllerIT {

    private static final String ORCAMENTO = UUID.randomUUID().toString();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrquestradorSagaUseCase orquestrador;
    @MockBean ProdutoGateway produtoGateway;

    private Long clienteId;
    private Long veiculoId;

    @BeforeEach
    void setUp() throws Exception {
        clienteId = criar("/api/clientes", new ClienteRequest("Maria", "71498053297", TipoDocumento.CPF, null, "maria@email.com", null));
        veiculoId = criar("/api/veiculos", new VeiculoRequest("ABC1234", "Toyota", "Corolla", 2021, "Prata", null));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(new Produto(1L, "Filtro de Ar", TipoProduto.PECA, new BigDecimal("30.00"))));
    }

    private Long criar(String rota, Object corpo) throws Exception {
        String resposta = mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    private Long criarOs(boolean comItem) throws Exception {
        List<OrdemServicoRequest.OsItemRequest> itens = comItem
                ? List.of(new OrdemServicoRequest.OsItemRequest(1L, 2, null, null)) : null;
        return criar("/api/ordens-servico", new OrdemServicoRequest(clienteId, veiculoId, "Barulho", null, itens));
    }

    private void evento(Long osId, String tipo, Map<String, Object> payload) throws Exception {
        orquestrador.processar(new EventoSaga(UUID.randomUUID(), tipo, orquestrador.buscarPorOs(osId).getId(), osId, payload));
    }

    private void ateAguardarDecisao(Long osId) throws Exception {
        String agora = Instant.now().toString();
        evento(osId, "DiagnosticoIniciado", Map.of("iniciadoEm", agora));
        evento(osId, "DiagnosticoConcluido", Map.of("concluidoEm", agora));
        evento(osId, "OrcamentoGerado", Map.of("orcamentoId", ORCAMENTO));
    }

    private void ateEmReparo(Long osId) throws Exception {
        ateAguardarDecisao(osId);
        evento(osId, "OrcamentoAprovado", Map.of());
        evento(osId, "EstoqueBaixado", Map.of());
        evento(osId, "CobrancaGerada", Map.of("expiraEm", Instant.now().plusSeconds(3600).toString()));
        evento(osId, "PagamentoConfirmado", Map.of());
        evento(osId, "ReparoEnfileirado", Map.of());
    }

    @Test
    @DisplayName("Fluxo completo pela Saga até ENTREGUE, com histórico e tempo médio")
    void fluxoAteEntregue() throws Exception {
        Long os = criarOs(true);
        mockMvc.perform(get("/api/ordens-servico/" + os))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.valorTotal").value(60.00))
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Filtro de Ar"));
        mockMvc.perform(get("/api/ordens-servico/" + os + "/saga")).andExpect(jsonPath("$.estado").value("AGUARDANDO_DIAGNOSTICO"));

        ateEmReparo(os);
        mockMvc.perform(get("/api/ordens-servico/" + os)).andExpect(jsonPath("$.status").value("EM_EXECUCAO"));

        Instant inicio = Instant.now().minusSeconds(7200);
        evento(os, "ReparoIniciado", Map.of("iniciadoEm", inicio.toString()));
        evento(os, "ReparoFinalizado", Map.of("finalizadoEm", inicio.plusSeconds(7200).toString()));
        mockMvc.perform(get("/api/ordens-servico/" + os)).andExpect(jsonPath("$.status").value("FINALIZADA"));
        mockMvc.perform(get("/api/ordens-servico/metricas/tempo-medio")).andExpect(jsonPath("$.tempoMedioHoras").value(2.0));

        mockMvc.perform(patch("/api/ordens-servico/" + os + "/avancar-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENTREGUE"));
        mockMvc.perform(get("/api/ordens-servico/" + os + "/historico"))
                .andExpect(jsonPath("$[0].para").value("RECEBIDA"))
                .andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    @DisplayName("Avançar status fora de FINALIZADA responde 422 (D21)")
    void avancarRecusado() throws Exception {
        Long os = criarOs(false);
        mockMvc.perform(patch("/api/ordens-servico/" + os + "/avancar-status")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PATCH /aprovar responde 202; fora de hora responde 409 (D23)")
    void aprovar() throws Exception {
        Long os = criarOs(true);
        String corpo = objectMapper.writeValueAsString(new AprovarOsRequest(true));
        mockMvc.perform(patch("/api/ordens-servico/" + os + "/aprovar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());

        ateAguardarDecisao(os);
        mockMvc.perform(patch("/api/ordens-servico/" + os + "/aprovar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));
    }

    @Test
    @DisplayName("Cancelar com reparo em andamento responde 202; fora do reparo responde 422; sem motivo responde 400 (D22)")
    void cancelar() throws Exception {
        Long os = criarOs(true);
        String corpo = objectMapper.writeValueAsString(new CancelarOsRequest("Cliente desistiu"));
        mockMvc.perform(post("/api/ordens-servico/" + os + "/cancelar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/ordens-servico/" + os + "/cancelar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());

        ateEmReparo(os);
        mockMvc.perform(post("/api/ordens-servico/" + os + "/cancelar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/ordens-servico/" + os + "/saga")).andExpect(jsonPath("$.estado").value("CANCELANDO_REPARO"));
    }

    @Test
    @WithMockUser(roles = "RECEPCAO")
    @DisplayName("Sem perfil: aprovar, cancelar, avançar, itens e reprocessar respondem 403")
    void perfis() throws Exception {
        mockMvc.perform(patch("/api/ordens-servico/1/avancar-status")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/ordens-servico/1/aprovar").contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ordens-servico/1/cancelar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ordens-servico/1/itens/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ordens-servico/1/saga/reprocessar")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reprocessar Saga sem falha responde 422")
    void reprocessar() throws Exception {
        Long os = criarOs(false);
        mockMvc.perform(post("/api/ordens-servico/" + os + "/saga/reprocessar")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve buscar por número, listar por status e por cliente; inexistente responde 404")
    void consultas() throws Exception {
        Long os = criarOs(false);
        String numero = objectMapper.readTree(mockMvc.perform(get("/api/ordens-servico/" + os)).andReturn().getResponse().getContentAsString()).get("numero").asText();

        mockMvc.perform(get("/api/ordens-servico/numero/" + numero)).andExpect(jsonPath("$.id").value(os));
        mockMvc.perform(get("/api/ordens-servico").param("status", "RECEBIDA")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/ordens-servico").param("clienteId", String.valueOf(clienteId))).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/ordens-servico/99999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Listagem padrão ordena por status e oculta encerradas, inclusive canceladas")
    void listagemPadrao() throws Exception {
        Long recebida = criarOs(true);
        Long emDiagnostico = criarOs(true);
        Long cancelada = criarOs(true);
        evento(emDiagnostico, "DiagnosticoIniciado", Map.of("iniciadoEm", Instant.now().toString()));
        ateAguardarDecisao(cancelada);
        evento(cancelada, "OrcamentoExpirado", Map.of());

        mockMvc.perform(get("/api/ordens-servico"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(emDiagnostico))
                .andExpect(jsonPath("$[1].id").value(recebida));
    }

    @Test
    @DisplayName("Itens mudam até o diagnóstico e congelam depois (D15)")
    void itens() throws Exception {
        Long os = criarOs(false);
        String item = objectMapper.writeValueAsString(new OrdemServicoRequest.OsItemRequest(1L, 1, null, "Item"));

        String resposta = mockMvc.perform(post("/api/ordens-servico/" + os + "/itens").contentType(MediaType.APPLICATION_JSON).content(item))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Filtro de Ar"))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(resposta).get("itens").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/ordens-servico/" + os + "/itens/99999")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ordens-servico/" + os + "/itens/" + itemId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));

        mockMvc.perform(post("/api/ordens-servico/" + os + "/itens").contentType(MediaType.APPLICATION_JSON).content(item)).andExpect(status().isOk());
        evento(os, "DiagnosticoIniciado", Map.of("iniciadoEm", Instant.now().toString()));
        evento(os, "DiagnosticoConcluido", Map.of("concluidoEm", Instant.now().toString()));
        mockMvc.perform(post("/api/ordens-servico/" + os + "/itens").contentType(MediaType.APPLICATION_JSON).content(item))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Execução fora do ar responde 503; abrir OS sem itens continua funcionando")
    void execucaoFora() throws Exception {
        when(produtoGateway.buscarPorId(1L)).thenThrow(new ServicoIndisponivelException("Execução fora", null));
        String corpo = objectMapper.writeValueAsString(new OrdemServicoRequest(clienteId, veiculoId, "Barulho", null,
                List.of(new OrdemServicoRequest.OsItemRequest(1L, 1, null, null))));

        mockMvc.perform(post("/api/ordens-servico").contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isServiceUnavailable());
        criarOs(false);
    }

    @Test
    @DisplayName("OS sem itens encerra em FINALIZADA e não entra no tempo médio (D16)")
    void semItens() throws Exception {
        Long os = criarOs(false);
        evento(os, "DiagnosticoIniciado", Map.of("iniciadoEm", Instant.now().toString()));
        evento(os, "DiagnosticoConcluido", Map.of("concluidoEm", Instant.now().toString()));

        mockMvc.perform(get("/api/ordens-servico/" + os)).andExpect(jsonPath("$.status").value("FINALIZADA"));
        mockMvc.perform(get("/api/ordens-servico/metricas/tempo-medio")).andExpect(jsonPath("$.tempoMedioHoras").value(0.0));
    }
}
