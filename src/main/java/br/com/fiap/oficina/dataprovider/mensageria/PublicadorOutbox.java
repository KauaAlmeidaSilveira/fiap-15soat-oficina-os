package br.com.fiap.oficina.dataprovider.mensageria;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.mensageria.habilitada", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PublicadorOutbox {

    private final OutboxRepository outboxRepository;
    private final SqsTemplate sqsTemplate;

    @Scheduled(fixedDelayString = "${app.mensageria.outbox-intervalo-ms:500}")
    @Transactional
    public void publicarPendentes() {
        for (OutboxMensagem mensagem : outboxRepository.travarPendentes()) {
            sqsTemplate.send(mensagem.getFila(), mensagem.getCorpo());
            mensagem.setPublicadoEm(Instant.now());
            log.info("Mensagem {} ({}) publicada na fila {}", mensagem.getId(), mensagem.getTipo(), mensagem.getFila());
        }
    }
}
