# Tech Challenge Fase 4 — regras para o Claude Code

Vale para quem operar a Fase 4 (Kauã ou Júlio). Idioma: pt-BR em conversa, docs, commits e PRs.

Commits: uma linha em Conventional Commits (`feat: ...`, `docs: ...`), sem corpo e sem `Co-Authored-By`.

## Montar a pasta de trabalho

Todos os repositórios lado a lado em `Challenge - Fase 4/`, com o monólito da Fase 3 só como referência:

```bash
mkdir "Challenge - Fase 4" && cd "Challenge - Fase 4"
for r in os billing execucao lambda-auth; do git clone https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-$r.git; done
for r in infra-k8s infra-db; do git clone https://github.com/JulioNCavalcanti/fiap-15soat-oficina-$r.git; done
mkdir legado && git clone https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-api.git legado/fiap-15soat-oficina-api
ln -s fiap-15soat-oficina-os/CLAUDE.md CLAUDE.md   # o Claude lê estas regras abrindo a pasta de cima
```

Chaves e tokens não vão para o git: cada um gera os seus com `sh scripts/gerar-chaves-locais.sh` e
`python3 postman/gerar-tokens.py`, a partir deste repositório. Credenciais do Mercado Pago e do New Relic
ficam no `.env` deste repositório (gitignorado; modelo em `.env.example`).

## Fonte de verdade: `CHECKLIST.md`

- Ao começar, ler a seção **Retomada** e, para AWS, **Ambiente na AWS**. O passo a passo para subir, testar e
  destruir está em `SUBIR-AMBIENTE.md`, na pasta de trabalho (fora do git).
- Plano aprovado e diferenças: `docs/plano-secoes-0-4.md`. Requisitos do enunciado: `docs/requisitos-fase4.md`.
- **O checklist fica sempre atualizado.** Em todo fix, feat, push, PR, merge ou decisão, no mesmo passo:
  o item `[ ]`/`[x]`, a Retomada, a Dimensão (contagem total e por seção) e os parágrafos que ficaram velhos.
  Contar só marcadores de item (`- [x]`) e de célula de tabela (`| [x] |`), nunca o texto que cita o marcador.
- Mudança no checklist vai em branch nova a partir de `main`, com PR. Não empurrar commit em branch de PR já mergeado.

## Ações externas

Push, PR, merge, `terraform apply`, criação de recurso na AWS ou no Mercado Pago e troca de secrets: só com
pedido explícito de quem está operando.

- **GitHub Actions bloqueado** por billing nas contas do Kauã e do Júlio (decidido em 14/11/2026 seguir sem
  ele). Nenhum workflow roda: build, testes, `terraform apply` e deploy são manuais, da máquina do Júlio.
- **AWS é a conta pessoal do Júlio**, não o Academy. Login com `aws login --profile personal`; rodar scripts e Terraform com `AWS_PROFILE=personal-tf`
  (profile com `credential_process = aws configure export-credentials --profile personal --format process`,
  porque o Terraform não lê a sessão do `aws login`) e conferir
  `aws sts get-caller-identity` antes de qualquer `apply`: há profiles de trabalho na mesma máquina.
  O ambiente sobe para testar ou gravar e é destruído logo depois; nada fica ligado sem uso.
- Ordem de `apply`: `infra-k8s` → `infra-db` → deploy dos serviços → `lambda-auth`. Destruir na ordem
  inversa, apagando antes os `Service`s LoadBalancer (os NLBs são do Kubernetes e travam a VPC).
- Desde 15/11/2026 as roles são criadas pelo Terraform (sem `LabRole`), e os serviços acessam SQS e DynamoDB
  pelo EKS Pod Identity. A conta tem cota de 1 vCPU on-demand: 1 node `r6g.medium` via `terraform.tfvars` e
  subida com `SEM_NRI_BUNDLE=1`.

## Regra de negócio

Não inventar. As decisões D1 a D23 estão na Seção 0 do checklist; caso omisso vira pergunta, não código.

## Testes

- Serviço: `mvn verify` no repositório (gate de 80% do JaCoCo sobre o serviço inteiro).
- Sistema inteiro: `sh scripts/rodar-bdd.sh` (sobe tudo em Docker, roda os 12 cenários e derruba).
- Mercado Pago real (credenciais de teste) no lugar do WireMock:
  `docker compose -f docker-compose.yml -f docker-compose.mercadopago.yml up -d --build --wait`.
  O BDD e o Newman rodam sempre sem esse arquivo, contra o WireMock.
- **Toda mudança de endpoint ou de fluxo atualiza `postman/` e roda no Newman**, com o sistema no ar
  (`docker compose up -d --build --wait`):

  ```bash
  npx -y newman@6 run postman/oficina-fase4.collection.json -e postman/oficina-fase4.environment.local.json
  ```

  Resultado esperado hoje: 237 asserções, com 4 falhas só na pasta "Só na AWS: Login" (a Lambda não sobe
  no Docker), ou seja, 233/233 no local. Na AWS, a pasta de Login roda antes e grava `token` (ADMIN) e
  `token_operador` (OPERADOR, exigido para criar, alterar e inativar produto).
- Qualidade: `sh scripts/sonar-local.sh` (SonarQube em Docker, quality gate "Sonar way" nos 3 serviços). O
  passo do CI usa SonarCloud, mas o Actions está bloqueado.
- Teste reproduz produção: não limpar fila, cache ou estado para passar; não alterar nem pular teste
  existente sem combinar.
