package br.com.fiap.oficina.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarOsRequest(
        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        String motivo
) {}
