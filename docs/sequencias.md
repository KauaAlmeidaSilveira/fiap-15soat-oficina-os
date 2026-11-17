# Diagramas de sequência — Fase 4

Três fluxos, desenhados a partir do código (`OrquestradorSagaUseCase` no OS, `BillingUseCase` no Billing e
`ExecucaoController` na Execução). Estados da Saga em `MAIÚSCULAS`; entre parênteses, o status da OS.

Convenções:

- **Seta cheia entre serviços** (`->>`): comando ou evento por SQS. Quem publica grava a mensagem na outbox,
  na mesma transação da mudança de estado; quem consome confere a inbox por `messageId` antes de agir.
- **Seta tracejada** (`-->>`): resposta HTTP.
- Comandos vão do OS para `oficina-execucao-comandos` ou `oficina-billing-comandos`. Eventos voltam para
  `oficina-os-saga-eventos`. Catálogo completo (C1 a C12 e eventos) em
  [plano-secoes-0-4.md](plano-secoes-0-4.md#4-catálogo-de-eventos).
- O API Gateway e o JWT ficam fora dos diagramas: toda chamada de pessoa entra pelo Gateway, que valida o token
  antes de rotear ao NLB do serviço ([arquitetura.md](arquitetura.md)).

## 1. Saga: fluxo feliz

Da abertura da OS à entrega, com orçamento aprovado pelo link do e-mail e pagamento pelo QR.

```mermaid
sequenceDiagram
    autonumber
    actor Rec as Recepção
    actor Op as Operador
    actor Cli as Cliente
    participant OS as OS Service<br/>(orquestrador)
    participant EX as Execução
    participant BI as Billing
    participant MP as Mercado Pago

    Rec->>OS: POST /api/ordens-servico
    Note over OS: OS (RECEBIDA)<br/>Saga AGUARDANDO_DIAGNOSTICO
    OS-->>Rec: 201
    OS->>EX: C1 EnfileirarDiagnostico
    EX->>OS: DiagnosticoEnfileirado

    Op->>EX: POST /api/execucoes/{osId}/diagnostico/iniciar
    EX->>OS: DiagnosticoIniciado
    Note over OS: EM_DIAGNOSTICO (EM_DIAGNOSTICO)

    Op->>OS: POST /api/ordens-servico/{id}/itens
    OS->>EX: GET /internal/produtos/{id} (REST, token de serviço)
    EX-->>OS: nome, tipo e preço (cópia no item)

    Op->>EX: POST /api/execucoes/{osId}/diagnostico/concluir
    EX->>OS: DiagnosticoConcluido
    Note over OS: itens congelados (D15)<br/>GERANDO_ORCAMENTO, prazo 30 min
    OS->>BI: C2 GerarOrcamento (itens, cliente, veículo)
    BI->>BI: grava orçamento no DynamoDB
    BI-)Cli: e-mail com o orçamento e o link de aprovação (best-effort)
    BI->>OS: OrcamentoGerado
    Note over OS: AGUARDANDO_DECISAO (AGUARDANDO_APROVACAO)<br/>prazo 7 dias (D9)

    Cli->>BI: GET /aprovacao-os?token=... (aprovar)
    BI->>BI: escrita condicional ENVIADO → APROVADO
    BI-->>Cli: página de confirmação
    BI->>OS: OrcamentoAprovado
    Note over OS: BAIXANDO_ESTOQUE, grava dataAprovacao

    OS->>EX: C5 BaixarEstoque (só PECA)
    EX->>EX: trava saldos e baixa tudo ou nada
    EX->>OS: EstoqueBaixado
    Note over OS: GERANDO_COBRANCA

    OS->>BI: C6 GerarCobranca
    BI->>MP: POST /v1/orders (QR dinâmico, X-Idempotency-Key)
    MP-->>BI: order + qr_data
    BI-)Cli: e-mail com QR e copia-e-cola (3 tentativas)
    BI->>OS: CobrancaGerada (expiraEm)
    Note over OS: AGUARDANDO_PAGAMENTO (AGUARDANDO_PAGAMENTO)<br/>prazo = validade do QR + 5 min

    Cli->>MP: paga o QR no app
    MP->>BI: POST /webhooks/mercado-pago
    BI->>MP: GET /v1/orders/{id}
    MP-->>BI: processed
    BI->>OS: PagamentoConfirmado
    Note over OS: ENVIANDO_PARA_EXECUCAO, itens bloqueados (D3)

    OS->>EX: C7 EnfileirarReparo
    EX->>OS: ReparoEnfileirado
    Note over OS: EM_REPARO (EM_EXECUCAO)

    Op->>EX: POST /api/execucoes/{osId}/reparo/iniciar
    EX->>OS: ReparoIniciado
    Note over OS: grava dataInicio
    Op->>EX: POST /api/execucoes/{osId}/reparo/finalizar
    EX->>OS: ReparoFinalizado
    Note over OS: CONCLUIDA (FINALIZADA)

    Rec->>OS: PATCH /api/ordens-servico/{id}/avancar-status
    Note over OS: OS (ENTREGUE), fora da Saga (D21)
    OS-->>Rec: 200
```

A oficina também pode decidir pelo cliente com `PATCH /api/ordens-servico/{id}/aprovar` (202): o OS envia
C3 `RegistrarDecisaoOrcamento` e o Billing aplica a mesma escrita condicional do link (D11). OS sem itens ou com
total zero vai direto de `DiagnosticoConcluido` para `CONCLUIDA` (D16).

## 2. Saga: fluxos com compensação

Compensações rodam na ordem inversa dos passos e são idempotentes. Os dois casos abaixo cobrem a cadeia mais
curta e a mais longa; a tabela completa (F1 a F10) está no [README](../README.md#saga-orquestrada).

### 2.1 QR expirado sem pagamento (F6)

O cliente não paga dentro da validade do QR. O orquestrador detecta pelo prazo persistido na tabela `saga`, sem
depender do webhook.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant OS as OS Service<br/>(orquestrador)
    participant EX as Execução
    participant BI as Billing
    participant MP as Mercado Pago

    Note over OS: AGUARDANDO_PAGAMENTO<br/>estoque já baixado, QR enviado
    OS->>OS: agendador encontra o prazo vencido (validade + 5 min)
    OS->>BI: C8 EncerrarCobranca (motivo: QR expirado)
    BI->>MP: GET /v1/orders/{id}
    MP-->>BI: created
    BI->>MP: POST /v1/orders/{id}/cancel
    MP-->>BI: cancelada
    BI->>OS: PagamentoFalhou (CANCELADO)
    Note over OS: DEVOLVENDO_ESTOQUE

    OS->>EX: C10 DevolverEstoque
    EX->>EX: estorna as movimentações da baixa
    EX->>OS: EstoqueDevolvido
    Note over OS: CANCELANDO_ORCAMENTO

    OS->>BI: C11 CancelarOrcamento (avisarCliente = true, D20)
    BI-)Cli: e-mail avisando o cancelamento
    BI->>OS: OrcamentoCancelado
    Note over OS: COMPENSADA (CANCELADA)
```

Se a consulta mostrar a order `processed` (o cliente pagou no último instante), o Billing responde
`PagamentoConfirmado` e a OS segue o fluxo feliz. Pagamento que chega depois da Saga compensada é reembolsado
(F8, D19).

### 2.2 Cancelamento com reparo em andamento (F10)

A oficina cancela uma OS já paga e em reparo. É a cadeia completa: cancelar o reparo, reembolsar, devolver as
peças e cancelar o orçamento (D22).

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    actor Cli as Cliente
    participant OS as OS Service<br/>(orquestrador)
    participant EX as Execução
    participant BI as Billing
    participant MP as Mercado Pago

    Note over OS: EM_REPARO (EM_EXECUCAO)<br/>estoque baixado, pagamento confirmado
    Op->>OS: POST /api/ordens-servico/{id}/cancelar (motivo)
    OS-->>Op: 202
    Note over OS: CANCELANDO_REPARO
    OS->>EX: C12 CancelarReparo

    alt reparo ainda não finalizado
        EX->>OS: ReparoCancelado
        Note over OS: ESTORNANDO_PAGAMENTO
        OS->>BI: C9 EstornarPagamento
        BI->>MP: POST /v1/orders/{id}/refund (integral)
        alt reembolso aceito
            MP-->>BI: ok
            BI->>OS: PagamentoEstornado
            Note over OS: DEVOLVENDO_ESTOQUE
            OS->>EX: C10 DevolverEstoque (todas as peças)
            EX->>OS: EstoqueDevolvido
            Note over OS: CANCELANDO_ORCAMENTO
            OS->>BI: C11 CancelarOrcamento (avisarCliente = true)
            BI-)Cli: e-mail avisando o cancelamento
            BI->>OS: OrcamentoCancelado
            Note over OS: COMPENSADA (CANCELADA)
        else Mercado Pago recusa ou está fora
            MP-->>BI: erro
            BI->>OS: EstornoFalhou
            Note over OS: COMPENSACAO_FALHOU, OS sem mudança<br/>alerta no New Relic e reprocessamento por<br/>POST /api/ordens-servico/{id}/saga/reprocessar (ADMIN)
        end
    else reparo finalizado antes do cancelamento
        EX->>OS: ReparoJaFinalizado
        Note over OS: CONCLUIDA (FINALIZADA), sem reembolso
    end
```

## 3. Pagamento com o Mercado Pago

Visão do Billing: criação da order, envio do QR, webhook e reembolso. O webhook nunca confirma pagamento pelo
corpo: ele só diz qual order consultar.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant OS as OS Service
    participant BI as Billing
    participant DB as DynamoDB<br/>oficina-billing
    participant MP as Mercado Pago<br/>API de Orders
    participant SMTP as Gmail SMTP

    OS->>BI: C6 GerarCobranca (orcamentoId, valor)
    BI->>DB: já existe cobrança da OS?
    alt cobrança já existe (comando repetido)
        BI->>OS: responde com o resultado já gravado
    else primeira vez
        BI->>MP: POST /v1/orders<br/>type qr, modo dynamic, external_reference = osId<br/>X-Idempotency-Key = id da cobrança
        alt Mercado Pago indisponível
            MP-->>BI: erro
            BI->>DB: outbox: CobrancaFalhou
            BI->>OS: CobrancaFalhou (vira F5 no orquestrador)
        else order criada
            MP-->>BI: id da order + qr_data
            loop até 3 tentativas
                BI->>SMTP: e-mail com imagem do QR e copia-e-cola
            end
            BI->>DB: transação: cobrança CRIADA + outbox CobrancaGerada<br/>(ou CobrancaFalhou se o e-mail falhou)
            BI->>OS: CobrancaGerada (mpOrderId, expiraEm)
        end
    end

    Cli->>MP: paga o QR no app
    MP->>BI: POST /webhooks/mercado-pago?data.id=...&type=order<br/>x-signature, x-request-id
    alt assinatura presente e inválida
        BI-->>MP: 401
    else assinatura válida ou ausente
        BI->>MP: GET /v1/orders/{id}
        alt order não existe (404)
            MP-->>BI: 404
            BI-->>MP: 200 (ignora, evita reenvio inútil)
        else order encontrada
            MP-->>BI: status
            BI->>DB: escrita condicional CRIADA → PAGA<br/>+ outbox PagamentoConfirmado
            Note right of DB: webhook repetido não passa<br/>na condição e não duplica
            BI-->>MP: 200
            BI->>OS: PagamentoConfirmado (mpOrderId, paymentId, valor)
        end
    end
    Note over BI,MP: falha temporária na consulta responde 5xx<br/>e o Mercado Pago reenvia

    opt compensação depois do pagamento (F7, F8, F10)
        OS->>BI: C9 EstornarPagamento
        BI->>MP: POST /v1/orders/{id}/refund<br/>X-Idempotency-Key = estorno-{id da cobrança}
        MP-->>BI: ok
        BI->>DB: cobrança ESTORNADA + outbox PagamentoEstornado
        BI->>OS: PagamentoEstornado
    end
```

A assinatura do webhook é conferida com o segredo do painel (`AssinaturaWebhook`). Em 15/11/2026, com um webhook
real, confirmou-se que o Mercado Pago assina o `data.id` na grafia original da order; o Billing aceita as duas
grafias. A API é a mesma para teste e produção (`api.mercadopago.com`): o ambiente vem da credencial.
