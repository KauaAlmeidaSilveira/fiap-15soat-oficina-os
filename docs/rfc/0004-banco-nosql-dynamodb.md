# RFC-0004: Banco NoSQL do Billing — Amazon DynamoDB

- **Status:** Aceito
- **Data:** 2026-11-16 (decisão D6 de 02/11/2026, implementada em 08/11/2026 e validada na AWS em 15/11/2026)

## Resumo

O Billing Service guarda orçamento, cobrança, outbox e inbox no **Amazon DynamoDB**, numa tabela única
`oficina-billing` (`infra-db/dynamodb.tf`). OS e Execução continuam em PostgreSQL
([RFC-0002](0002-escolha-do-banco-de-dados-rds-postgresql.md)).

## Motivação / Contexto

- O enunciado pede banco por serviço e ao menos um banco não relacional (`docs/requisitos-fase4.md`).
- O Billing é o serviço com o dado menos relacional: orçamento e cobrança são documentos lidos sempre pela OS, sem
  junções. Os itens do orçamento são uma cópia congelada no fim do diagnóstico (D15), não uma relação viva com
  produto.
- As duas regras críticas do Billing são de **"primeira escrita vence"**:
  - a decisão do orçamento sai de `ENVIADO` uma vez só, venha do link do cliente, do `PATCH /aprovar` da oficina ou
    do prazo de 7 dias; a segunda tentativa vira 409;
  - o pagamento confirma uma cobrança uma vez só, mesmo com webhook repetido.

## Decisão

DynamoDB, tabela única, chave `pk` + `sk`:

| Item | `pk` | `sk` | Uso |
|---|---|---|---|
| Orçamento | `OS#<osId>` | `ORCAMENTO` | Itens, valor, cliente, veículo, prazo e decisão, em `dados`; `status` fora do documento |
| Cobrança | `OS#<osId>` | `COBRANCA` | Order do Mercado Pago, `qr_data`, valor, prazo e pagamento |
| Outbox | `OUTBOX#<uuid>` | `MSG` | Evento gravado na mesma transação do estado |
| Inbox | `INBOX#<messageId>` | `MSG` | Comando já processado, com TTL de 14 dias |

- **Escritas condicionais** sobre `status` garantem as duas regras de "primeira vence", sem lock.
- **`TransactWriteItems`** grava a mudança de status, o evento na outbox e o registro na inbox juntos.
- **GSI esparso `outbox-pendente`** (`outboxPendente` + `criadoEm`): só os eventos não publicados aparecem nele.
  Cada réplica reserva o item por 30 s com escrita condicional antes de publicar.
- **Acesso** pela role do Billing no EKS Pod Identity, com permissão só nesta tabela.
- Localmente e nos testes, LocalStack (Testcontainers e `docker-compose` do OS).

Diagrama do modelo no [README do Billing](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing#modelo-de-dados).

## Alternativas consideradas

- **MongoDB** (plano B da D6): modelo de documento equivalente, mas exigiria rodar um cluster no EKS ou contratar
  Atlas fora da AWS. O DynamoDB é gerenciado, cobrado por uso e alcançado por IAM, sem senha.
- **PostgreSQL também no Billing:** funcionaria, mas não atenderia ao requisito de NoSQL, e o Billing não usa
  junções que justifiquem SQL.
- **Uma tabela por entidade:** orçamento e cobrança são lidos juntos pela mesma OS; a tabela única com `pk` da OS
  resolve as duas leituras por chave e mantém uma só permissão de IAM.
- **Redis/ElastiCache:** chave-valor em memória, sem a durabilidade e as transações condicionais de que a decisão e
  o pagamento precisam.

## Consequências

- Consultas só por chave: não há "listar todos os orçamentos aprovados". As rotas do Billing consultam por OS
  (`GET /api/orcamentos/{osId}`, `GET /api/pagamentos/{osId}`); uma listagem nova exigiria outro índice.
- Mudança de formato do documento `dados` é responsabilidade do código: não há migração de esquema.
- O webhook do Mercado Pago repetido não paga duas vezes, porque a condição de status falha na segunda escrita.
- A publicação da outbox usa outro mecanismo que no PostgreSQL (reserva por escrita condicional em vez de
  `SKIP LOCKED`), com teste próprio (`PublicadorOutboxIT`).
- Validado na AWS em 15/11/2026: tabela criada pelo `infra-db`, acesso pela role do Pod Identity e o fluxo completo
  gravando orçamento, cobrança e outbox.
