# ADRs — Architecture Decision Records

Registro das decisões arquiteturais permanentes do projeto. Formato curto (MADR): Status,
Contexto, Decisão, Consequências, Alternativas consideradas.

| # | Título | Status | Origem |
|---|---|---|---|
| [0001](0001-uso-de-hpa-para-escalabilidade.md) | Uso de HPA para escalabilidade do cluster Kubernetes | Aceito | Fase 3, atualizada |
| [0002](0002-comunicacao-sincrona-via-rest.md) | Comunicação síncrona via REST/HTTP | Substituído pela 0004 | Fase 3, histórico |
| [0003](0003-saga-orquestrada.md) | Saga orquestrada, com o orquestrador no OS Service | Aceito | Fase 4 |
| [0004](0004-mensageria-sqs.md) | Mensageria assíncrona com Amazon SQS | Aceito | Fase 4 |

Só entram ADRs de decisões já implementadas — uma recomendação ainda não adotada não vira
ADR até o time de fato decidir e implementar. As da Fase 3 foram migradas do repositório
`fiap-15soat-oficina-api` com o texto original e uma seção "Na Fase 4" no fim.
