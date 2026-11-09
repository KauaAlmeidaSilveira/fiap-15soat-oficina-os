package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;

import java.time.LocalDateTime;

public record HistoricoStatus(Long osId, StatusOS de, StatusOS para, String motivo, LocalDateTime em) {}
