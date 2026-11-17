# RFC-0002: Escolha do banco de dados — Amazon RDS PostgreSQL

- **Status:** Aceito
- **Data:** 2026-09-14

> Migrado da Fase 3 (repositório `fiap-15soat-oficina-api`, congelado na pasta `legado/`). O texto original segue abaixo sem alteração; a seção **Na Fase 4**, no fim, registra o que mudou com a divisão em microsserviços.

> Este RFC também cumpre o requisito de "justificativa formal para a escolha do banco de
> dados" pedido separadamente em [`docs/requisitos-fase3.md`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-api/blob/master/docs/requisitos-fase3.md) — é a mesma decisão, tratada
> num documento só em vez de duplicar o texto em dois lugares.

## Resumo

O banco de dados gerenciado do projeto é **Amazon RDS para PostgreSQL 16**, provisionado
via Terraform no repositório `fiap-15soat-oficina-infra-db`.

## Motivação / Contexto

Duas razões concretas, nessa ordem:

1. **Continuidade**: a aplicação já usa PostgreSQL como banco de produção desde a Fase 2
   (perfil `default`, ver `README.md` — "Decisão de banco de dados"). Trocar o motor de
   banco agora, na Fase 3, reintroduziria risco (compatibilidade de tipos, sintaxe SQL,
   comportamento de índices) sem nenhum ganho correspondente — nenhum requisito desta fase
   pede um banco diferente.
2. **RDS como gerenciado nativo da nuvem já escolhida** (ver RFC-0001): usar RDS em vez de
   um PostgreSQL self-managed em EC2 elimina a necessidade de o time administrar patching
   de sistema operacional, backup manual e recuperação de falha — tudo isso vem coberto
   pelo serviço gerenciado, com o time só cuidando do schema e das credenciais de acesso.

### Justificativa formal quanto a "consistência e performance" (exigência do desafio)

- **Consistência — nível de infraestrutura**: RDS garante backups automáticos e
  point-in-time recovery nativos, e suporta Multi-AZ (não habilitado neste projeto por
  custo, mas disponível caso o orçamento permita) para failover automático.
- **Consistência — nível de modelo**: garantida pelo que já está implementado, sem
  ferramenta adicional:
  - Chaves estrangeiras obrigatórias entre as entidades centrais — `OrdemServico` →
    `Cliente`/`Veiculo`, `OsItem` → `OrdemServico`/`Produto`, `ClienteVeiculo` →
    `Cliente`/`Veiculo` — todas mapeadas com `@JoinColumn(nullable = false)`.
  - Restrições de unicidade no schema: `Cliente.cpfCnpj`, `OrdemServico.numero`,
    `Veiculo.placa`, `User.username`, e o vínculo 1:1 de `SaldoEstoque` por produto.
  - Fronteira transacional em cada gateway de persistência (`@Transactional` em
    `ClienteGatewayImpl`, `VeiculoGatewayImpl`, `ProdutoGatewayImpl`,
    `OrdemServicoGatewayImpl`, `EstoqueGatewayImpl`, `UsuarioGatewayImpl`).
  - Schema gerado diretamente a partir das entidades JPA (Hibernate), o que mantém
    modelo e banco sempre sincronizados entre deploys.
- **Performance**: RDS permite trocar a classe de instância e o tipo/tamanho de storage
  (gp3, io2) sem migração de dados, e expõe métricas nativas (CloudWatch) para
  monitoramento de uso. No lado da aplicação, todo relacionamento `@ManyToOne` usa
  `FetchType.LAZY` por padrão, evitando carregar entidades relacionadas sem necessidade; o
  pool de conexões é o HikariCP padrão do Spring Boot, sem tuning customizado.

## Alternativas consideradas

- **Amazon Aurora PostgreSQL** — compatível com Postgres, mas os recursos que o diferenciam
  (réplicas de leitura de baixa latência, escala de armazenamento quase ilimitada) não têm
  uso real no volume de dados de uma oficina simulada; custo mais alto sem benefício
  correspondente neste contexto.
- **MySQL / SQL Server (via RDS)** — trocaria o motor sem necessidade; todo o código já
  parte da sintaxe e dos tipos do PostgreSQL.
- **DynamoDB (ou outro NoSQL gerenciado)** — descartado por natureza: o domínio é
  fortemente relacional (Cliente, Veículo, Ordem de Serviço e Produto se relacionam por
  chave estrangeira, com consultas que atravessam essas entidades), o que um modelo
  chave-valor/documento não representa bem sem duplicação ou lógica de aplicação extra.

## Consequências

- Mesma dependência de credenciais temporárias da conta AWS Academy descrita no RFC-0001
  (o `infra-db` usa a mesma conta e as mesmas credenciais de curto prazo).
- Custo do RDS soma ao do EKS dentro do mesmo teto de créditos do Academy — motivo pelo
  qual Multi-AZ e réplicas de leitura não foram habilitadas.

## Na Fase 4

- **Continua PostgreSQL 16 no RDS** para os serviços cujo domínio é relacional: OS (`oficina_os`) e Execução
  (`oficina_execucao`).
- **Banco por serviço:** uma instância RDS com dois databases, cada um com um usuário dono só do próprio
  (`infra-db/scripts/criar-databases.sh`). Nenhum serviço tem credencial do banco do outro. Uma instância por
  serviço foi descartada por custo, sem ganho de isolamento para este volume.
- **Chaves estrangeiras que cruzavam serviços saíram:** `os_item` guarda cópia de nome, tipo e preço do produto
  (dono: Execução), e `movimentacao_estoque` guarda o `osId` sem FK. As FKs de `ordem_servico` para cliente,
  veículo e usuário ficaram, porque os três continuam no OS.
- **O Billing não usa PostgreSQL:** orçamento e cobrança foram para o DynamoDB ([RFC-0004](0004-banco-nosql-dynamodb.md)).
  A alternativa "DynamoDB" descartada acima continua descartada para OS e Execução, pelos mesmos motivos.
- **Consistência com outros serviços:** sai a transação única do monólito e entra a Saga
  ([ADR-0003](../adr/0003-saga-orquestrada.md)), com outbox e inbox na mesma transação do PostgreSQL.
