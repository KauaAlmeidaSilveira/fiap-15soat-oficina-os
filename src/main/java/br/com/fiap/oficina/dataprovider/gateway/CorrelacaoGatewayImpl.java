package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.gateway.CorrelacaoGateway;
import br.com.fiap.oficina.entrypoint.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelacaoGatewayImpl implements CorrelacaoGateway {

    @Override
    public String atual() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
