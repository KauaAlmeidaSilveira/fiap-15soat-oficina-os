package br.com.fiap.oficina.core.gateway;

import java.util.function.Supplier;

/** Fronteira atômica da Saga: OS, saga, inbox e outbox gravam juntos ou não gravam. */
public interface TransacaoGateway {

    <T> T executar(Supplier<T> trabalho);
}
