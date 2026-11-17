# Guia para o avaliador — Tech Challenge Fase 4

Índice de onde está cada requisito do enunciado ([`docs/requisitos-fase4.md`](docs/requisitos-fase4.md)). O
estado item a item, com datas e o que ficou de fora, está no [`CHECKLIST.md`](CHECKLIST.md).

## Repositórios

| Repositório | Papel |
|---|---|
| [`fiap-15soat-oficina-os`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-os) (este) | OS, cliente, veículo, usuário e orquestrador da Saga. Também guarda a documentação geral, o `docker-compose`, o BDD, o Postman, a observabilidade e os scripts de deploy |
| [`fiap-15soat-oficina-billing`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing) | Orçamento, aprovação, cobrança e Mercado Pago |
| [`fiap-15soat-oficina-execucao`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao) | Fila de execução, diagnóstico, reparo, produto e estoque |
| [`fiap-15soat-oficina-lambda-auth`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth) | Lambda de autenticação e API Gateway |
| [`fiap-15soat-oficina-infra-k8s`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s) | VPC, EKS, ECR, filas SQS e roles por serviço |
| [`fiap-15soat-oficina-infra-db`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-db) | RDS PostgreSQL (um database por serviço) e DynamoDB |

## Arquitetura

| O quê | Onde |
|---|---|
| Diagrama geral (serviços, bancos, comunicação) | [`docs/arquitetura.md`](docs/arquitetura.md#diagrama-geral) |
| Justificativa da divisão dos microsserviços | [`docs/arquitetura.md`](docs/arquitetura.md#divisão-dos-microsserviços) |
| Tecnologias e por que cada uma | [`docs/arquitetura.md`](docs/arquitetura.md#tecnologias) |
| Diagramas de sequência: Saga feliz, compensações e pagamento | [`docs/sequencias.md`](docs/sequencias.md) |
| Modelo de dados de cada serviço | README de cada serviço, seção "Modelo de dados": [OS](README.md#modelo-de-dados), [Billing](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing#modelo-de-dados), [Execução](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao#modelo-de-dados) |
| Decisões (ADRs) | [`docs/adr/`](docs/adr/README.md): HPA, REST (substituída), Saga orquestrada, SQS |
| Escolhas amplas (RFCs) | [`docs/rfc/`](docs/rfc/README.md): AWS, PostgreSQL, autenticação, DynamoDB |
| Plano aprovado e diferenças na implementação | [`docs/plano-secoes-0-4.md`](docs/plano-secoes-0-4.md) |
| Destino dos 37 endpoints e das 10 entidades do monólito | [`docs/mapa-endpoints-entidades.md`](docs/mapa-endpoints-entidades.md) |

## Requisitos obrigatórios

| Requisito | Onde ver |
|---|---|
| 3 microsserviços, cada um com repositório e banco | Tabela acima; [`docs/arquitetura.md`](docs/arquitetura.md#componentes) |
| Banco SQL e banco NoSQL | PostgreSQL no OS e na Execução ([RFC-0002](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md)), DynamoDB no Billing ([RFC-0004](docs/rfc/0004-banco-nosql-dynamodb.md)) |
| REST síncrono e mensageria | [ADR-0004](docs/adr/0004-mensageria-sqs.md); catálogo de comandos e eventos em [`docs/plano-secoes-0-4.md`](docs/plano-secoes-0-4.md#4-catálogo-de-eventos) |
| Nenhum serviço acessa o banco de outro | [`docs/arquitetura.md`](docs/arquitetura.md#divisão-dos-microsserviços); credenciais por serviço em [`infra-db`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-db) e roles do Pod Identity em [`infra-k8s`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s) |
| Saga com compensação, escolha documentada no README | [README, "Saga orquestrada"](README.md#saga-orquestrada); [ADR-0003](docs/adr/0003-saga-orquestrada.md); [`docs/sequencias.md`](docs/sequencias.md) |
| Integração com o Mercado Pago | [README do Billing](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing); [`docs/sequencias.md`](docs/sequencias.md#3-pagamento-com-o-mercado-pago) |
| Testes unitários em todos os serviços | `src/test` de cada serviço; `mvn verify` em cada repositório |
| Fluxo completo em BDD | [`src/test/resources/features/`](src/test/resources/features/): `fluxo-completo.feature` e `compensacoes.feature` (12 cenários, Cucumber); rodar com `sh scripts/rodar-bdd.sh` |
| Cobertura mínima de 80% por serviço | Gate do JaCoCo no `pom.xml` de cada serviço. Evidência: [README do OS, "Qualidade"](README.md#qualidade-sonarqube) e seção "Cobertura e qualidade" dos READMEs do [Billing](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing#cobertura-e-qualidade) e da [Execução](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao#cobertura-e-qualidade) |
| Qualidade via SonarQube | [README do OS, "Qualidade"](README.md#qualidade-sonarqube): os 3 serviços passam no gate "Sonar way" (`scripts/sonar-local.sh`) |
| Pipeline de CI/CD por serviço | `.github/workflows/ci.yml` em cada serviço (build, testes, cobertura e SonarCloud). **Nunca rodou:** o GitHub Actions das contas está bloqueado por billing; build, testes e deploy rodam pelos scripts (`scripts/deploy-aws.sh`). Ver ponto de atenção 8 do [`CHECKLIST.md`](CHECKLIST.md) |
| `main` protegida | Seção 5 do [`CHECKLIST.md`](CHECKLIST.md), com o estado de cada repositório |
| Dockerfile e manifestos Kubernetes | `Dockerfile` e `k8s/` em cada serviço (`Deployment`, `Service`, `ConfigMap`, `HPA`, probes, requests e limits) |
| Mensageria na infraestrutura | `sqs.tf` em [`infra-k8s`](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s): 3 filas, cada uma com DLQ |
| Monitoramento e observabilidade (New Relic) | [`observability/`](observability/README.md): dashboard e alertas em Terraform; trace pelo SQS; logs com `X-Correlation-Id` e ID da Saga |
| Swagger e Postman | Swagger em cada serviço (`/swagger-ui.html`); collection em [`postman/`](postman/) |

## Rodar

| O quê | Como |
|---|---|
| Sistema inteiro local, com os 12 cenários BDD | `sh scripts/gerar-chaves-locais.sh` e `sh scripts/rodar-bdd.sh` (Docker; Billing e Execução na pasta vizinha) |
| Testes e cobertura de um serviço | `mvn verify` no repositório |
| Qualidade dos 3 serviços | `sh scripts/sonar-local.sh` |
| Postman | Descrição da collection em [`postman/`](postman/) |
| Subir e destruir na AWS | `scripts/subir-aws.sh` e `scripts/destruir-aws.sh` (seção "Ambiente na AWS" do [`CHECKLIST.md`](CHECKLIST.md)) |
