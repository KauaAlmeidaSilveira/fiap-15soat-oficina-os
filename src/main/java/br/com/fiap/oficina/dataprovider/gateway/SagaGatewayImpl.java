package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Saga;
import br.com.fiap.oficina.core.gateway.SagaGateway;
import br.com.fiap.oficina.dataprovider.mensageria.MensagemProcessada;
import br.com.fiap.oficina.dataprovider.mensageria.MensagemProcessadaRepository;
import br.com.fiap.oficina.dataprovider.persistence.entity.SagaEntity;
import br.com.fiap.oficina.dataprovider.persistence.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaGatewayImpl implements SagaGateway {

    private final SagaRepository repository;
    private final MensagemProcessadaRepository inbox;

    @Override
    @Transactional
    public Saga salvar(Saga saga) {
        SagaEntity entity = repository.findById(saga.getId()).orElseGet(SagaEntity::new);
        entity.setId(saga.getId());
        entity.setOsId(saga.getOsId());
        entity.setEstado(saga.getEstado());
        entity.setPrazoEm(saga.getPrazoEm());
        entity.setMotivo(saga.getMotivo());
        entity.setCorrelationId(saga.getCorrelationId());
        entity.setOrcamentoId(saga.getOrcamentoId());
        entity.setValor(saga.getValor());
        return paraDominio(repository.save(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Saga> travarPorId(UUID id) {
        return repository.travarPorId(id).map(this::paraDominio);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Saga> travarPorOs(Long osId) {
        return repository.travarPorOs(osId).map(this::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Saga> buscarPorOs(Long osId) {
        return repository.findByOsId(osId).map(this::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listarVencidas(Instant agora, int limite) {
        return repository.listarVencidas(agora, PageRequest.of(0, limite));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrarMensagemSeNova(UUID mensagemId) {
        if (inbox.existsById(mensagemId)) {
            return false;
        }
        inbox.save(new MensagemProcessada(mensagemId, Instant.now()));
        return true;
    }

    private Saga paraDominio(SagaEntity e) {
        return Saga.builder()
                .id(e.getId())
                .osId(e.getOsId())
                .estado(e.getEstado())
                .prazoEm(e.getPrazoEm())
                .motivo(e.getMotivo())
                .correlationId(e.getCorrelationId())
                .orcamentoId(e.getOrcamentoId())
                .valor(e.getValor())
                .build();
    }
}
