package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.mensageria.ContextoSaga;

import java.util.Map;

public interface MensageriaGateway {

    enum Destino { BILLING, EXECUCAO }

    void enviarComando(Destino destino, String tipo, ContextoSaga contexto, Map<String, Object> payload);
}
