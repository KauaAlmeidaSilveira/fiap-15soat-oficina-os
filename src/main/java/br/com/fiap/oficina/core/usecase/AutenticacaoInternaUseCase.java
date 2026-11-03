package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.gateway.AutenticacaoInternaGateway;

import java.util.Optional;

/** Apoio à Lambda de autenticação, que deixou de acessar o banco (D4). */
public class AutenticacaoInternaUseCase {

    private final AutenticacaoInternaGateway gateway;

    public AutenticacaoInternaUseCase(AutenticacaoInternaGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<AutenticacaoInternaGateway.Funcionario> buscarFuncionario(String username) {
        return gateway.buscarFuncionario(username);
    }

    public Optional<AutenticacaoInternaGateway.ClienteLogin> buscarCliente(String cpf) {
        return gateway.buscarCliente(cpf);
    }

    public Optional<Long> definirSenhaPrimeiroAcesso(String cpf, String email, String senhaHash) {
        return gateway.definirSenhaPrimeiroAcesso(cpf, email, senhaHash);
    }
}
