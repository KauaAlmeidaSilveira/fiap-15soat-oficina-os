package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.core.domain.enums.EstadoSaga;

import java.time.Instant;
import java.util.UUID;

public record SagaResponse(UUID id, Long osId, EstadoSaga estado, Instant prazoEm, String motivo, String correlationId) {}
