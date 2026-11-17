# ADR-0004: Mensageria assíncrona com Amazon SQS

- **Status:** Aceito. Substitui a [ADR-0002](0002-comunicacao-sincrona-via-rest.md)
- **Data:** 2026-11-16 (decisão D5 de 02/11/2026, implementada em 08/11/2026)

## Contexto

A ADR-0002 manteve tudo em REST síncrono porque havia um processo só. Na Fase 4, o fluxo da OS atravessa três
serviços, e a Saga orquestrada ([ADR-0003](0003-saga-orquestrada.md)) precisa de um canal que:

- não perca comando nem evento se o destino estiver fora do ar;
- tente de novo sozinho e separe o que não tem conserto;
- não deixe a API esperando o Mercado Pago ou o operador.

O enunciado pede comunicação entre os microsserviços por REST ou mensageria (`docs/requisitos-fase4.md`).

## Decisão

**Amazon SQS Standard** para todo o fluxo da Saga, e **REST síncrono** só onde quem chama precisa da resposta para
responder ao próprio chamador.

| Fila | Quem consome | Quem publica |
|---|---|---|
| `oficina-execucao-comandos` | Execução | OS (comandos) |
| `oficina-billing-comandos` | Billing | OS (comandos) |
| `oficina-os-saga-eventos` | OS (orquestrador) | Execução e Billing (eventos) |

- Cada fila tem DLQ depois de 5 recebimentos (`maxReceiveCount = 5`), com retenção de 14 dias na DLQ
  (`infra-k8s/sqs.tf`).
- Envelope comum: `messageId`, `tipo`, `versao`, `sagaId`, `osId`, `correlationId`, `causationId`, `ocorridoEm`,
  `payload` e `rastreio` (cabeçalhos do trace distribuído do New Relic).
- **Outbox** na mesma transação do estado: PostgreSQL com publicador agendado e `FOR UPDATE SKIP LOCKED`; DynamoDB
  com `TransactWriteItems` e um índice esparso de pendentes, reservado por 30 s para uma réplica só publicar.
- **Inbox** por `messageId` na mesma transação do efeito, mais guarda de estado no orquestrador.
- Acesso por EKS Pod Identity: cada serviço só lê a própria fila e só escreve nas filas para onde publica.

REST síncrono fica em três pontos:

| Chamada | Por que síncrona |
|---|---|
| OS → Execução `GET /internal/produtos/{id}` | Copiar nome, tipo e preço ao adicionar o item à OS |
| Lambda → OS `/internal/auth/*` | O login precisa da resposta para emitir o token |
| Billing → Mercado Pago | Criar, consultar, cancelar e reembolsar a order |

Timeouts de 1 s para conexão e 2 s para leitura nas chamadas internas, com JWT de serviço de 60 s.

## Consequências

- Um serviço fora do ar não derruba o fluxo: a mensagem espera na fila, e só os prazos da Saga disparam
  compensação.
- Entrega pelo menos uma vez e sem ordem garantida: duplicata e evento fora de ordem são tratados por inbox e guarda
  de estado, não pelo broker.
- Mensagem que falha 5 vezes vai para a DLQ e exige olhar humano. O alerta da DLQ depende de integrar a conta AWS ao
  New Relic, o que ainda não foi feito.
- Mais peças para operar e testar: as filas existem no Terraform, no LocalStack (local e BDD) e na AWS.
- O trace atravessa a fila pelo campo `rastreio` do envelope, aceito no listener com `@Trace(dispatcher = true)`.

## Alternativas consideradas

- **Continuar só com REST (ADR-0002):** cada passo da Saga dependeria do destino no ar naquele instante, e o
  retry e a compensação teriam de ser feitos à mão em cada cliente HTTP.
- **SNS + SQS:** na orquestração cada mensagem tem um destino só; não há fan-out que justifique o tópico.
- **SQS FIFO:** a ordem e a deduplicação seriam parciais de qualquer forma (deduplicação de 5 minutos, ordem por
  grupo). Inbox e guarda de estado já resolvem os dois casos, e a FIFO limitaria a vazão.
- **Kafka (MSK) ou RabbitMQ (Amazon MQ):** broker com servidor para dimensionar e pagar, sem ganho para o volume de
  uma oficina. O SQS é gerenciado, tem DLQ nativa e roda no LocalStack.
