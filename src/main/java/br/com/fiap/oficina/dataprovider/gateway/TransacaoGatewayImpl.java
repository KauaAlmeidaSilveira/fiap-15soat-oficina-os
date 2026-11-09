package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.gateway.TransacaoGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Diverge do padrão de transação por método de gateway: a Saga precisa de fronteira única. */
@Component
public class TransacaoGatewayImpl implements TransacaoGateway {

    private final TransactionTemplate template;

    public TransacaoGatewayImpl(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        return template.execute(status -> trabalho.get());
    }
}
