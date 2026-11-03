package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Produto;

import java.util.Optional;

public interface ProdutoGateway {

    Optional<Produto> buscarPorId(Long id);
}
