package br.com.fiap.oficina.core.domain.mensageria;

import java.util.Map;
import java.util.UUID;

public record EventoSaga(UUID messageId, String tipo, UUID sagaId, Long osId, Map<String, Object> payload) {}
