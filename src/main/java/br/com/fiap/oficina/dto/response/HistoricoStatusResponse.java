package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.core.domain.enums.StatusOS;

import java.time.LocalDateTime;

public record HistoricoStatusResponse(StatusOS de, StatusOS para, String motivo, LocalDateTime em) {}
