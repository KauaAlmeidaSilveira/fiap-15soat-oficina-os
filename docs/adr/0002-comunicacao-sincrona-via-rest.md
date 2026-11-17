# ADR-0002: Comunicação síncrona via REST/HTTP

- **Status:** Substituído pela [ADR-0004](0004-mensageria-sqs.md) na Fase 4
- **Data:** 2026-09-14

> Migrado da Fase 3 só como histórico. **Esta decisão não vale mais:** o fluxo da OS passou a ser assíncrono, por SQS, na [ADR-0004](0004-mensageria-sqs.md). REST síncrono continua em três pontos, listados nela.

## Contexto

Todos os pontos de entrada da aplicação — os endpoints administrativos protegidos por JWT
(`/api/clientes`, `/api/veiculos`, `/api/ordens-servico`, `/api/produtos`, `/api/auth`) e o
callback público de aprovação de orçamento por e-mail (`/aprovacao-os`) — já são expostos
como REST sobre HTTP, com JSON como formato de payload, via Spring MVC. Não existe hoje
nenhum canal assíncrono (fila, tópico, webhook de saída) entre a aplicação e outros
sistemas.

## Decisão

Manter **REST/HTTP síncrono** como padrão de comunicação para toda a superfície pública da
aplicação — sem introduzir mensageria (fila ou tópico) para os fluxos internos do domínio.
Isso inclui o fluxo de notificação por e-mail: o disparo do e-mail acontece de forma síncrona
dentro do use case que faz a transição de status da OS, não via evento publicado para um
worker separado.

## Consequências

- Simplicidade operacional: nenhum broker de mensageria para provisionar, manter ou pagar
  dentro do orçamento de créditos do AWS Academy.
- Acoplamento temporal: um pico de carga (ex.: muitas OS mudando de status ao mesmo tempo,
  disparando e-mails) impacta diretamente a latência percebida da API, já que o envio do
  e-mail acontece na mesma requisição que motivou a transição de status.
- Documentação e testes ficam mais simples de raciocinar — o Swagger exigido pelo desafio
  descreve o contrato completo, sem eventos implícitos fora dele.
- Se um componente futuro (ex.: a Lambda de autenticação) precisar de desacoplamento real
  (retry assíncrono, backpressure), isso exige uma nova decisão — este ADR cobre o estado
  atual do sistema, não fecha a porta para revisão.

## Alternativas consideradas

- **Mensageria assíncrona (RabbitMQ, Amazon SQS)** — desacoplaria o envio de e-mail da
  requisição HTTP, mas adiciona um componente de infraestrutura extra e complexidade
  operacional (DLQ, retry, monitoramento da fila) sem benefício claro no volume de OS de
  uma oficina simulada.
- **gRPC** — traria contratos tipados e streaming, mas não há nenhum consumidor interno
  que precise disso hoje; também dificultaria atender ao requisito de documentação via
  Swagger, pensado para REST.
