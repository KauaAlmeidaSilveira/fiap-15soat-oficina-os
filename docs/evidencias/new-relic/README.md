# Evidências do New Relic

Prints da conta 7428180, tirados em 17/11/2026 por volta das 02:45, com o ambiente no ar na AWS. Os dados são da
subida em andamento naquele horário (6 OS criadas, Sagas até `AGUARDANDO_DECISAO`).

| Print | O que mostra |
|---|---|
| [01-dashboard-servicos.jpg](01-dashboard-servicos.jpg) | Dashboard "Oficina Fase 4 - Microsserviços e Saga", aba Serviços: requisições e mensagens por minuto, latência e taxa de erro dos 3 serviços, e as mensagens consumidas do SQS por fila e tipo |
| [02-dashboard-saga.jpg](02-dashboard-saga.jpg) | Aba Saga: OS criadas, Sagas encerradas por desfecho, eventos fora de estado, transições por estado e mensagens publicadas pela outbox |
| [03-dashboard-pagamentos.jpg](03-dashboard-pagamentos.jpg) | Aba Pagamentos: orçamentos gerados e cobranças. Nesta subida nenhum orçamento tinha sido aprovado, então as cobranças estão zeradas |
| [04-dashboard-rastreamento-logs.jpg](04-dashboard-rastreamento-logs.jpg) | Aba Rastreamento: logs agrupados por `correlationId`, com `sagaId` e o número de serviços (3) que escreveram com o mesmo id |
| [05-trace-distribuido-sqs.jpg](05-trace-distribuido-sqs.jpg) | Trace distribuído `65781cc5c0d74c5c3905e417064fb9ba` (66 spans, 5 entidades): `POST /api/execucoes/{osId}/diagnostico/concluir` na Execução → `oficina-os-saga-eventos/DiagnosticoConcluido` no OS → `oficina-billing-comandos/GerarOrcamento` no Billing → `oficina-os-saga-eventos/OrcamentoGerado` no OS, com PostgreSQL e DynamoDB. O trace atravessa o SQS três vezes |
| [06-apm-servicos.jpg](06-apm-servicos.jpg) | APM: `oficina-os`, `oficina-billing` e `oficina-execucao` reportando (tempo de resposta, vazão e taxa de erro) |
| [07-alertas.jpg](07-alertas.jpg) | Condições da policy "Oficina Fase 4 - Operação": cobrança ou estorno, processamento, latência, Saga com compensação que falhou, serviço sem dados e taxa de erro. "Serviço sem reportar dados" aparece desligado de propósito (`alertas_sem_dados`): com o ambiente destruído dispararia sempre. As condições "Oficina API - Operacao" são da Fase 3 |
| [08-pagamentos-com-pagas.jpg](08-pagamentos-com-pagas.jpg) | Aba Pagamentos às 03:58 UTC de 17/11/2026: 13 orçamentos gerados, 6 aprovados, 5 QR gerados e 2 pagos pelo Mercado Pago (conta compradora de teste), com o evento `PagamentoConfirmado` no gráfico |
| [09-saga-concluida.jpg](09-saga-concluida.jpg) | Aba Saga no mesmo horário: 14 OS criadas, 1 Saga concluída (a OS 16, da abertura ao reparo finalizado) e 1 compensada |

Como o trace atravessa a fila: o publicador grava os cabeçalhos do trace no campo `rastreio` do envelope, e o listener
os aceita com `@Trace(dispatcher = true)` (`RastreioDistribuido`). Dashboard e alertas são Terraform em
[`observability/`](../../../observability/README.md).
