package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.AutenticacaoInternaGateway;
import br.com.fiap.oficina.core.usecase.AutenticacaoInternaUseCase;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
@Hidden
public class AutenticacaoInternaController {

    public record PrimeiroAcessoRequest(String email, String senhaHash) {}

    private final AutenticacaoInternaUseCase useCase;

    @GetMapping("/funcionarios")
    public AutenticacaoInternaGateway.Funcionario funcionario(@RequestParam String username) {
        return useCase.buscarFuncionario(username)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionário não encontrado"));
    }

    @GetMapping("/clientes/{cpf}")
    public AutenticacaoInternaGateway.ClienteLogin cliente(@PathVariable String cpf) {
        return useCase.buscarCliente(cpf)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
    }

    @PostMapping("/clientes/{cpf}/primeiro-acesso")
    public Map<String, Long> primeiroAcesso(@PathVariable String cpf, @RequestBody PrimeiroAcessoRequest corpo) {
        return useCase.definirSenhaPrimeiroAcesso(cpf, corpo.email(), corpo.senhaHash())
                .map(id -> Map.of("id", id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Primeiro acesso não permitido"));
    }
}
