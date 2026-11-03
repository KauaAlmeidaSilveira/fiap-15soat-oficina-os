package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase.ItemNovo;
import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.dto.request.CancelarOsRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.HistoricoStatusResponse;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.dto.response.SagaResponse;
import br.com.fiap.oficina.entrypoint.controller.mapper.OrdemServicoDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
@Tag(name = "Ordens de Serviço", description = "Gestão completa de ordens de serviço")
public class OrdemServicoController {

    private final OrdemServicoUseCase ordemServicoUseCase;
    private final OrdemServicoDtoMapper mapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Criar nova Ordem de Serviço")
    public ResponseEntity<OrdemServicoResponse> criar(@Valid @RequestBody OrdemServicoRequest request) {
        List<ItemNovo> itens = request.itens() == null ? List.of() : request.itens().stream()
                .map(i -> new ItemNovo(i.produtoId(), i.quantidade(), i.precoUnitario(), i.observacao()))
                .toList();
        var criada = ordemServicoUseCase.criar(request.clienteId(), request.veiculoId(),
                request.descricaoProblema(), request.observacoes(), itens);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(criada));
    }

    @GetMapping
    @Operation(summary = "Listar OS. Sem filtros: retorna apenas as OS ativas " +
            "(oculta Finalizada, Entregue, Reprovada e Cancelada), ordenadas por status " +
            "(Em Execução > Aguardando Pagamento > Aguardando Aprovação > Diagnóstico > Recebida) e, dentro do mesmo " +
            "status, mais antigas primeiro. Com '?status=' ou '?clienteId=' retorna o filtro " +
            "explícito, incluindo status finalizados.")
    public ResponseEntity<List<OrdemServicoResponse>> listar(
            @RequestParam(required = false) StatusOS status,
            @RequestParam(required = false) Long clienteId) {
        List<OrdemServicoResponse> resultado;
        if (status != null) {
            resultado = ordemServicoUseCase.listarPorStatus(status).stream().map(mapper::toResponse).toList();
        } else if (clienteId != null) {
            resultado = ordemServicoUseCase.listarPorCliente(clienteId).stream().map(mapper::toResponse).toList();
        } else {
            resultado = ordemServicoUseCase.listarTodas().stream().map(mapper::toResponse).toList();
        }
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar OS por ID")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.buscarPorId(id)));
    }

    @GetMapping("/numero/{numero}")
    @Operation(summary = "Buscar OS por número (acesso do cliente)")
    public ResponseEntity<OrdemServicoResponse> buscarPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.buscarPorNumero(numero)));
    }

    @PatchMapping("/{id}/avancar-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Registrar a entrega: leva a OS de FINALIZADA a ENTREGUE. Diagnóstico e reparo avançam pelas rotas de Execução.")
    public ResponseEntity<OrdemServicoResponse> avancarStatus(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.avancarStatus(id)));
    }

    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Registra a decisão do cliente sobre o orçamento. O resultado é assíncrono: acompanhe em /{id}/saga.")
    public ResponseEntity<OrdemServicoResponse> aprovar(
            @PathVariable Long id,
            @Valid @RequestBody AprovarOsRequest request,
            Authentication autenticacao) {
        return ResponseEntity.accepted().body(mapper.toResponse(ordemServicoUseCase.aprovar(id, request.aprovado(), autenticacao.getName())));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Cancela a OS com reparo em andamento: reembolso integral e devolução das peças. O resultado é assíncrono.")
    public ResponseEntity<OrdemServicoResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelarOsRequest request,
            Authentication autenticacao) {
        return ResponseEntity.accepted().body(mapper.toResponse(ordemServicoUseCase.cancelar(id, request.motivo(), autenticacao.getName())));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de status da OS")
    public ResponseEntity<List<HistoricoStatusResponse>> historico(@PathVariable Long id) {
        return ResponseEntity.ok(ordemServicoUseCase.listarHistorico(id).stream()
                .map(h -> new HistoricoStatusResponse(h.de(), h.para(), h.motivo(), h.em()))
                .toList());
    }

    @GetMapping("/{id}/saga")
    @Operation(summary = "Estado da Saga da OS")
    public ResponseEntity<SagaResponse> saga(@PathVariable Long id) {
        var saga = ordemServicoUseCase.buscarSaga(id);
        return ResponseEntity.ok(new SagaResponse(saga.getId(), saga.getOsId(), saga.getEstado(), saga.getPrazoEm(), saga.getMotivo(), saga.getCorrelationId()));
    }

    @PostMapping("/{id}/saga/reprocessar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tenta de novo o estorno de uma Saga em COMPENSACAO_FALHOU")
    public ResponseEntity<Void> reprocessarSaga(@PathVariable Long id) {
        ordemServicoUseCase.reprocessarSaga(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/itens")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Adicionar item à OS")
    public ResponseEntity<OrdemServicoResponse> adicionarItem(
            @PathVariable Long id,
            @Valid @RequestBody OrdemServicoRequest.OsItemRequest itemRequest) {
        var itemNovo = new ItemNovo(itemRequest.produtoId(), itemRequest.quantidade(),
                itemRequest.precoUnitario(), itemRequest.observacao());
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.adicionarItem(id, itemNovo)));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Remover item da OS, enquanto o diagnóstico não foi concluído")
    public ResponseEntity<OrdemServicoResponse> removerItem(
            @PathVariable Long id,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.removerItem(id, itemId)));
    }

    @GetMapping("/metricas/tempo-medio")
    @Operation(summary = "Tempo médio de execução das OS em horas")
    public ResponseEntity<Map<String, Object>> tempoMedio() {
        Double tempo = ordemServicoUseCase.tempoMedioExecucao();
        return ResponseEntity.ok(Map.of("tempoMedioHoras", tempo != null ? tempo : 0.0));
    }
}
