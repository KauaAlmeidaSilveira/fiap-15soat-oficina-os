package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.Role;
import br.com.fiap.oficina.dataprovider.persistence.entity.User;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.RoleRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mesmos casos do teste de repositório que a Lambda tinha, agora atrás da rota interna. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AutenticacaoInternaControllerIT {

    private static final String CPF = "52998224725";
    private static final String CORPO = "{\"email\":\"JOAO@email.com\",\"senhaHash\":\"$2a$hash\"}";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ClienteRepository clienteRepository;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setName(Role.Values.ADMIN.name());
        role = roleRepository.save(role);
        User user = new User();
        user.setUsername("kaua@gmail.com");
        user.setPassword("$2a$senha");
        user.setRoles(Set.of(role));
        userRepository.save(user);
        clienteRepository.save(Cliente.builder().nome("João").cpfCnpj(CPF).tipoDocumento(TipoDocumento.CPF).email("joao@email.com").build());
    }

    @Test
    @DisplayName("Rotas internas exigem token")
    void exigeToken() throws Exception {
        mockMvc.perform(get("/internal/auth/funcionarios").param("username", "kaua@gmail.com")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/auth/clientes/" + CPF)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Funcionário vem com hash e roles; inexistente responde 404")
    void funcionario() throws Exception {
        mockMvc.perform(get("/internal/auth/funcionarios").param("username", "kaua@gmail.com").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaHash").value("$2a$senha"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        mockMvc.perform(get("/internal/auth/funcionarios").param("username", "nao@existe.com").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cliente vem com id e status; inexistente responde 404")
    void cliente() throws Exception {
        mockMvc.perform(get("/internal/auth/clientes/" + CPF).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));
        mockMvc.perform(get("/internal/auth/clientes/71498053297").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Primeiro acesso grava a senha uma vez, ignorando caixa do e-mail")
    void primeiroAcesso() throws Exception {
        mockMvc.perform(post("/internal/auth/clientes/" + CPF + "/primeiro-acesso").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber());
        assertThat(clienteRepository.findByCpfCnpj(CPF).orElseThrow().getSenhaHash()).isEqualTo("$2a$hash");

        mockMvc.perform(post("/internal/auth/clientes/" + CPF + "/primeiro-acesso").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Primeiro acesso recusa e-mail diferente e cliente inativo")
    void primeiroAcessoRecusado() throws Exception {
        mockMvc.perform(post("/internal/auth/clientes/" + CPF + "/primeiro-acesso").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outro@email.com\",\"senhaHash\":\"x\"}"))
                .andExpect(status().isNotFound());

        Cliente cliente = clienteRepository.findByCpfCnpj(CPF).orElseThrow();
        cliente.setStatus(StatusCliente.INATIVO);
        clienteRepository.save(cliente);
        mockMvc.perform(post("/internal/auth/clientes/" + CPF + "/primeiro-acesso").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());
    }
}
