package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.gateway.AutenticacaoInternaGateway;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AutenticacaoInternaGatewayImpl implements AutenticacaoInternaGateway {

    private final UserRepository userRepository;
    private final ClienteRepository clienteRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarFuncionario(String username) {
        return userRepository.findByUsername(username)
                .map(u -> new Funcionario(u.getUsername(), u.getPassword(),
                        u.getRoles().stream().map(r -> r.getName()).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClienteLogin> buscarCliente(String cpf) {
        return clienteRepository.findByCpfCnpj(cpf)
                .map(c -> new ClienteLogin(c.getId(), c.getStatus().name(), c.getSenhaHash()));
    }

    /** Mesmas condições do UPDATE que a Lambda fazia: cliente ativo, e-mail confere e sem senha ainda. */
    @Override
    @Transactional
    public Optional<Long> definirSenhaPrimeiroAcesso(String cpf, String email, String senhaHash) {
        return clienteRepository.findByCpfCnpj(cpf)
                .filter(c -> c.getStatus() == StatusCliente.ATIVO)
                .filter(c -> c.getEmail() != null && c.getEmail().equalsIgnoreCase(email))
                .filter(c -> c.getSenhaHash() == null)
                .map(c -> {
                    c.setSenhaHash(senhaHash);
                    return c.getId();
                });
    }
}
