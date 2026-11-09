package br.com.fiap.oficina.entrypoint.mensageria;

import br.com.fiap.oficina.core.usecase.OrquestradorSagaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mensageria.habilitada", havingValue = "true")
@RequiredArgsConstructor
public class AgendadorPrazosSaga {

    private final OrquestradorSagaUseCase orquestrador;

    @Scheduled(fixedDelayString = "${app.saga.verificacao-prazos-ms:5000}")
    public void verificar() {
        orquestrador.processarPrazosVencidos();
    }
}
