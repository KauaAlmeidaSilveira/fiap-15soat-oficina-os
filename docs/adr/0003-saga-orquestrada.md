# ADR-0003: Saga orquestrada, com o orquestrador no OS Service

- **Status:** Aceito
- **Data:** 2026-11-16 (decisão D5 de 02/11/2026, implementada em 08/11/2026)

## Contexto

Na Fase 3, abrir, orçar, aprovar, baixar estoque e finalizar uma OS acontecia numa transação só do monólito. Na
Fase 4 o processo atravessa três serviços com bancos próprios (OS e Execução em PostgreSQL, Billing em DynamoDB),
e o enunciado pede uma Saga para manter a consistência entre eles (`docs/requisitos-fase4.md`).

O fluxo tem compensações que **dependem da ordem do que já aconteceu**:

- pagamento recusado ou QR expirado: devolver o estoque e cancelar o orçamento (F6);
- falha ao enviar para execução, depois de pago: reembolsar, devolver o estoque e cancelar o orçamento (F7);
- cancelamento com reparo em andamento: cancelar o reparo, reembolsar, devolver todas as peças e cancelar o
  orçamento (F10, D22).

Também há prazos: 7 dias para o cliente decidir (D9), a validade do QR (D14) e 30 minutos por etapa.

## Decisão

**Saga orquestrada**, com o orquestrador dentro do OS Service (`OrquestradorSagaUseCase`).

- O OS envia comandos (C1 a C12) e reage aos eventos de Execução e Billing. Catálogo em
  [plano-secoes-0-4.md](../plano-secoes-0-4.md#4-catálogo-de-eventos).
- **Estado persistido** na tabela `saga` (estado, prazo, motivo e versão para lock otimista) e histórico em
  `saga_transicao`. A Saga continua do ponto em que parou depois de reiniciar o pod; um cenário BDD prova isso.
- **Prazos** vigiados por agendador sobre `prazo_em`. O atraso máximo do SQS (15 min) não atenderia aos 7 dias.
- **Publicação confiável** por outbox na mesma transação da mudança de estado, e **consumo idempotente** por inbox
  com `messageId`, mais guarda de estado: evento fora do estado esperado é registrado e descartado, com uma
  exceção (`PagamentoConfirmado` em Saga compensada vira reembolso, F8).
- **Falha de compensação** (`EstornoFalhou`) leva a `COMPENSACAO_FALHOU`, com alerta no New Relic e reprocessamento
  manual por `POST /api/ordens-servico/{id}/saga/reprocessar` (só `ADMIN`). A OS não é cancelada enquanto o
  reembolso não acontecer.
- `GET /api/ordens-servico/{id}/saga` expõe o estado, para acompanhar as respostas 202.

Fluxos desenhados em [sequencias.md](../sequencias.md); estados em
[plano-secoes-0-4.md](../plano-secoes-0-4.md#5-estados-da-saga).

## Consequências

- O fluxo inteiro fica legível num lugar só: as transições estão em um `switch` de estado e evento.
- Compensar é decidir com base no estado persistido, sem reconstruir o que aconteceu a partir de eventos espalhados.
- O OS conhece os comandos dos outros dois serviços e está no caminho de todo o fluxo. Enquanto ele reinicia, as
  mensagens esperam na fila.
- Toda operação que muda outro serviço passa a ser assíncrona: `PATCH /aprovar` e `POST /cancelar` respondem 202
  (D23), e o resultado se consulta depois.
- Cada falha tem um jeito reprodutível de provocar (interruptor `SIMULAR_FALHA`, só no profile `simulacao`), usado
  pelos 12 cenários BDD e pelo vídeo.

## Alternativas consideradas

- **Coreografia** (cada serviço reage aos eventos dos outros): a regra "reembolsar antes de devolver o estoque"
  ficaria espalhada entre os consumidores dos três serviços, e nenhum deles saberia sozinho o que falta desfazer.
  Os prazos também não teriam dono.
- **Quarto serviço só para orquestrar:** a OS já é dona do processo; outro serviço acrescentaria deploy, banco e
  saltos de rede sem benefício neste volume.
- **Framework de Saga ou de workflow** (Axon, Temporal, AWS Step Functions): mais um componente para operar e
  aprender no prazo do desafio. O estado em tabela, com outbox e inbox, cobre o que o fluxo precisa.
- **Transação distribuída (2PC):** SQS e DynamoDB não participam de XA, e o bloqueio entre serviços é o que a
  divisão quer evitar.
