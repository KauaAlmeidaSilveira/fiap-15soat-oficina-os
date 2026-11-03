package br.com.fiap.oficina.entrypoint.mensageria;

import br.com.fiap.oficina.core.domain.mensageria.EventoSaga;
import br.com.fiap.oficina.core.usecase.OrquestradorSagaUseCase;
import br.com.fiap.oficina.dataprovider.mensageria.Envelope;
import br.com.fiap.oficina.entrypoint.filter.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.mensageria.habilitada", havingValue = "true")
@RequiredArgsConstructor
public class EventosSagaListener {

    private final OrquestradorSagaUseCase orquestrador;
    private final ObjectMapper objectMapper;

    @SqsListener("${app.mensageria.fila-eventos}")
    public void receber(String corpo) throws JsonProcessingException {
        Envelope envelope = objectMapper.readValue(corpo, Envelope.class);
        MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        MDC.put("sagaId", String.valueOf(envelope.sagaId()));
        try {
            Map<String, Object> payload = objectMapper.convertValue(envelope.payload(), new TypeReference<>() {});
            orquestrador.processar(new EventoSaga(envelope.messageId(), envelope.tipo(), envelope.sagaId(), envelope.osId(), payload));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
            MDC.remove("sagaId");
        }
    }
}
