package br.com.fiap.oficina.core.gateway;

import java.util.List;
import java.util.Optional;

public interface AutenticacaoInternaGateway {

    record Funcionario(String username, String senhaHash, List<String> roles) {}

    record ClienteLogin(Long id, String status, String senhaHash) {}

    Optional<Funcionario> buscarFuncionario(String username);

    Optional<ClienteLogin> buscarCliente(String cpf);

    Optional<Long> definirSenhaPrimeiroAcesso(String cpf, String email, String senhaHash);
}
