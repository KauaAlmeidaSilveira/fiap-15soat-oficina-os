# Checklist de Entrega — Tech Challenge Fase 4

> Estado conferido em 16/11/2026 contra o código, as branches, os PRs, as execuções do Actions, os colaboradores e as regras de proteção de cada repositório.
> Legenda: `[ ]` pendente · `[x]` concluído · `[?]` não verificável localmente (falta ferramenta/credencial, não falta de trabalho). Sem status "parcial": um item composto por várias partes vira uma linha por parte, cada uma binária. Por isso, o que está pronto no local e falta validar no ambiente real aparece em duas linhas.
> `(Dn)` indica que o item depende da decisão `n` da Seção 0.

**Entrega: 17/11/2026.** Seções 0 a 4 e higiene foram feitas pelo Kauã até 08/11/2026; Seções 5 a 9 foram tocadas pelo Júlio entre 14 e 16/11/2026.

**Resumo:** a Fase 4 é majoritariamente reconstrução. Reaproveitamos a base de domínio, EKS, API Gateway, Lambda e New Relic. Não existem hoje: divisão em serviços, banco por serviço, NoSQL, mensageria, Saga, pagamento, BDD e Sonar no CI.

## Retomada: onde paramos

**Regra vigente:** avanço sozinho só no desenvolvimento local. Subir ou destruir na AWS, criar recurso no Mercado Pago, push, PR e merge só com pedido explícito de quem opera (Júlio ou Kauã).

**Em uma frase:** o código das Seções 0 a 4 dos 3 serviços está em `main` desde 08/11/2026, e o de `infra-k8s`, `infra-db` e `lambda-auth` desde 14/11/2026. A AWS é a conta pessoal do Júlio e o GitHub Actions está fora: tudo roda manualmente, pelos scripts do OS. **Em 15/11/2026 o sistema subiu inteiro na AWS e uma OS foi do início à entrega pelo API Gateway, com Mercado Pago real (credenciais de teste), webhook assinado e e-mails pelo Gmail; o ambiente foi destruído em seguida.** O código dessa subida foi mergeado em 15/11/2026 na branch principal dos 6 repositórios (ver "Ações na nuvem já feitas"); o roteiro para subir de novo está em `SUBIR-AMBIENTE.md`, na pasta de trabalho.

### O que existe hoje

| Repositório | Branch de trabalho | Situação | Testes da última execução local |
|---|---|---|---|
| `fiap-15soat-oficina-execucao` | `feat/fila-estoque-saga` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao/pull/1) mergeado em 08/11/2026, com a transação das rotas de operador | 81 testes, 96,2% de cobertura |
| `fiap-15soat-oficina-billing` | `feat/orcamento-pagamento` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing/pull/1) mergeado em 08/11/2026 | 82 testes, 95,4% de cobertura |
| `fiap-15soat-oficina-os` | `feat/divisao-servicos` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-os/pull/1) mergeado em 08/11/2026, com os cenários F3, F8 e F9 | 215 testes, 96,7% de cobertura, mais 12 cenários BDD |
| `fiap-15soat-oficina-lambda-auth` | `feat/sem-acesso-ao-banco` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth/pull/1) mergeado em 14/11/2026; #2 e #3 em 15/11/2026; aplicado e destruído em 15/11/2026 | 45 testes; `terraform validate` ok |
| `fiap-15soat-oficina-infra-k8s` (do Júlio) | `feat/filas-saga` | [PR #1](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s/pull/1) mergeado em 14/11/2026; #2 em 15/11/2026; aplicado e destruído em 15/11/2026 | `terraform validate` ok |
| `fiap-15soat-oficina-infra-db` (do Júlio) | `feat/tabela-billing` | [PR #1](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-db/pull/1) mergeado em 14/11/2026; #2 em 15/11/2026; aplicado e destruído em 15/11/2026 | `terraform validate` ok |

As branches de trabalho acima já foram mergeadas; em 16/11/2026 os 6 repositórios estão com `main`/`master` em dia e nenhum PR aberto.

O workflow de CI (`ci.yml`) está em `main` nos 3 serviços desde 14/11/2026 ([OS](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-os/pull/2) e [Execução](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao/pull/2) mergeados pelo Kauã, [Billing](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing/pull/2) pelo Júlio), sem nunca ter rodado: o GitHub Actions das duas contas está bloqueado por billing (ponto de atenção 8). **Decidido em 14/11/2026: o Actions não volta, e build, testes, `apply` e deploy passam a ser manuais, da máquina do Júlio.** O código dos 3 serviços, de `infra-k8s`, de `infra-db` e de `lambda-auth` está em `main`/`master`; não há PR aberto.

Para ver o sistema funcionando, a partir de `fiap-15soat-oficina-os`, com o Docker ligado e os repositórios de Execução e Billing na pasta vizinha:

```bash
sh scripts/gerar-chaves-locais.sh
sh scripts/rodar-bdd.sh   # sobe tudo, roda os 12 cenários e derruba
```

Para testar pelo Postman, siga a descrição da collection em `fiap-15soat-oficina-os/postman/`. A pasta foi versionada no OS em 08/11/2026, para o Júlio receber pelo git: o environment versionado fica sem tokens, e `postman/gerar-tokens.py` gera o `oficina-fase4.environment.local.json`, ignorado pelo git.

Para montar a pasta de trabalho (repositórios lado a lado e `legado/`) e conhecer as regras de operação, ver o `CLAUDE.md` do repositório do OS.

### O que os testes provam e o que não provam

- **Provado localmente:** fluxo completo da OS até a entrega, compensações F1 a F10 e reinício do orquestrador, com os 3 serviços em Docker. A collection repete o fluxo e as falhas, menos F3 e o reinício.
- **Simulado:** SQS e DynamoDB no LocalStack, Mercado Pago no WireMock e e-mail no Mailpit.
- **Testado na AWS em 15/11/2026:** fluxo feliz completo, com Lambda, Gateway, RDS, SQS, DynamoDB, Pod Identity, HPA, Gmail e a API real do Mercado Pago (QR, pagamento aprovado e webhook).
- **Nunca testado em ambiente real:** compensações na AWS e CI. Qualidade validada com SonarQube local em 15/11/2026.
- **Suposições do Mercado Pago:** `qr_data` e assinatura conferidos em 15/11/2026 (a assinatura usa o id da order na grafia original; corrigido). Método de cancelamento e status de pagamento recusado ficam opcionais (não exigidos; cobertos contra o WireMock).

### Ações na nuvem já feitas

Antes da regra acima, abri os 6 PRs. Em `lambda-auth`, `infra-k8s` e `infra-db`, o PR disparou o `terraform plan`, que falhou com `The security token included in the request is expired`. O `apply` foi pulado e nenhum recurso foi criado. Os PRs dos repositórios do Júlio foram abertos sem eu saber que eram dele.

Em 14/11/2026, o Júlio mergeou os PRs #1 de `infra-k8s` e `infra-db` (merge commits `798bb49` e `4a8e4af`). Os workflows de `main` não começaram: a conta `JulioNCavalcanti` também está com o GitHub Actions bloqueado por billing (ponto de atenção 8). Nenhum recurso foi criado. Mesmo com o Actions liberado, o `apply` desses dois repositórios espera aprovação: o environment `producao` exige revisor.

No mesmo dia, o Júlio recebeu `write` nos 4 repositórios do Kauã e mergeou o PR #1 de `lambda-auth` em `master` (`6bd76db`). O workflow de deploy não começou, pelo mesmo bloqueio, e nada foi criado.

Em 15/11/2026, na conta `390403887596` do Júlio:

- `aws-bootstrap.sh` criou o bucket `fiap-15soat-oficina-fase4-tfstate`, a tabela de lock e o Budget `oficina-fase4` (US$ 20/mês). Ficam ligados entre as subidas.
- Primeira subida: o node group falhou com `AsgInstanceLaunchFailures` (*MaxFleetCountExceeded*). A conta nova tem cota de 1 vCPU on-demand; o pedido de 8 vCPU virou o caso 179476836800116 e foi negado.
- Segunda subida com 1 node `r6g.medium` (1 vCPU): tudo no ar depois de três ajustes (SMTP fora do health check do Billing, requests de CPU de 50m, JDK 21 no `mvn`). Newman: Login 3/3; Catálogo 26/49, com as falhas em cascata do token único (Seção 4). Fluxo completo feito à mão pelo Gateway até `ENTREGUE`.
- Mercado Pago: webhook cadastrado no Modo de teste do painel; a primeira notificação assinada deu 401 e levou à correção da assinatura.
- Ambiente destruído no fim do dia por `destruir-aws.sh` (109 recursos; conferido que não sobrou VPC, instância, NLB, NAT, EKS, RDS, ECR, fila, Lambda nem Gateway).
- PRs mergeados pelo Júlio no mesmo dia, sem CI (Actions bloqueado): `infra-k8s` #2, `infra-db` #2, `lambda-auth` #2 e #3, Execução #3 e #4, Billing #3 e #4 e OS (`feat/deploy-aws`). Depois, também sem CI: Billing #5 a #7 e Execução #5 e #6 (trace pelo SQS, webhook de order inexistente, probes) em 15/11/2026, e OS #10 a #13 (probes, proteção de branch da infra, diagrama e justificativa da arquitetura) até 16/11/2026, e o modelo de dados nos READMEs (OS #14, Billing #8, Execução #7) em 16/11/2026. As execuções disparadas por esses merges seguem recusadas com "The job was not started because your account is locked due to a billing issue" (última conferida: OS, 17/11/2026 01:28 UTC).

### Por onde retomar

1. **Revisão feita:** o Júlio aprovou os 6 PRs em 08/11/2026. As diferenças em relação ao plano aprovado estão na seção 9 de `docs/plano-secoes-0-4.md`.
2. **PRs de infra:** `infra-k8s`, `infra-db` e `lambda-auth` mergeados em 14/11/2026, sem `apply` (ver "Ações na nuvem já feitas"). Antes do primeiro `apply`, adaptar à conta pessoal (ver "Antes de testar na AWS"). A ordem de `apply` continua valendo, com `lambda-auth` por último.
3. **Merge dos serviços:** feito em 08/11/2026 (Execução, Billing e OS).
4. **CI manual:** o Actions não volta (decidido em 14/11/2026). Build, testes e cobertura rodam com `mvn verify` local, e o deploy, da máquina do Júlio. Os requisitos de pipeline e de "deploy automatizado" no vídeo ficam sem atender (ponto de atenção 8).
5. **Acessos externos:** AWS e Mercado Pago (vendedora, loja, caixa, compradora de teste e webhook) prontos em 15/11/2026. O SonarCloud foi trocado pelo SonarQube local (Seção 4).
6. **Validar no ambiente real:** feito o fluxo feliz em 15/11/2026. Na próxima subida, mostrar as compensações pela AWS e conferir trace e painéis do New Relic. O Mercado Pago está fechado: o que o enunciado pede foi validado.
7. **Próximos passos:** documentação (Seção 8), proteção de branch nos repositórios do Kauã (Seção 5) e a gravação do vídeo numa nova subida.

### Ambiente na AWS

Escrito e aplicado em 15/11/2026, e mergeado no mesmo dia na branch principal dos 6 repositórios. Roteiro completo, com os problemas já vistos, em `SUBIR-AMBIENTE.md` na pasta de trabalho:

- `k8s/` nos 3 serviços: `Namespace`, `ServiceAccount`, `ConfigMap`, `Deployment` (probes, requests e limits), `Service` LoadBalancer com NLB interno e `HPA`; `.dockerignore` para chaves e `.env` não entrarem na imagem;
- `infra-k8s`: roles do cluster e dos nodes, uma role por serviço pelo EKS Pod Identity (cada uma só com as próprias filas e, no Billing, a tabela), ECR por serviço com `force_delete`, nodes ARM `t4g.medium` (imagem construída sem emulação nos Macs);
- `infra-db`: senhas por serviço e `scripts/criar-databases.sh`, que cria `oficina_os` e `oficina_execucao` com usuário dono só do próprio database, por um pod de `psql` (a RDS é privada);
- `lambda-auth`: role própria da Lambda, um NLB e uma integração por serviço, rotas por prefixo, `/aprovacao-os` e `/webhooks/mercado-pago` públicas no Billing;
- OS: `scripts/aws-bootstrap.sh` (bucket do state, lock e Budget, uma vez), `scripts/subir-aws.sh` (tudo, na ordem), `scripts/deploy-aws.sh` (testes, imagem, ECR, Secrets e manifestos) e `scripts/destruir-aws.sh` (ordem inversa, NLBs antes).

**Conta AWS pessoal (decidido em 14/11/2026):** os testes e o vídeo usam a conta pessoal do Júlio, não o Academy. O ambiente sobe para testar e é destruído logo depois; sobe de novo para gravar o vídeo e é destruído de novo. Preparação feita em 15/11/2026:

- configurar o profile `personal` do AWS CLI na máquina do Júlio e rodar tudo com `AWS_PROFILE=personal-tf` (profile com `credential_process` em cima da sessão do `aws login --profile personal`, que o Terraform não lê), conferindo `aws sts get-caller-identity` antes de cada `apply` (há profiles de trabalho na mesma máquina);
- usar Terraform 1.9.8, a versão dos workflows, e Node 22 (`.nvmrc` do `lambda-auth`);
- rodar `scripts/aws-bootstrap.sh` uma vez (bucket de state, tabela de lock e AWS Budget com alerta);
- preencher no `.env` do OS o `NEW_RELIC_LICENSE_KEY` e o Gmail do envio de e-mail (`GMAIL_SMTP_USERNAME` e `GMAIL_SMTP_PASSWORD`, uma senha de app); sem o Gmail, orçamento e QR não chegam ao cliente;
- a sessão do profile `personal` é do usuário root da conta: preferir um usuário ou role IAM com permissão de administrador (ainda root);
- por causa da cota de 1 vCPU, `infra-k8s/terraform.tfvars` (gitignorado) fixa 1 node `r6g.medium`, e a subida roda com `SEM_NRI_BUNDLE=1`.

**Secrets do GitHub:** os atuais são do Academy e estão expirados. Com o Actions fora, não são usados; ficam registrados para o caso de ele voltar:

| Repositório | Dono | Secrets | Quem pode trocar |
|---|---|---|---|
| `infra-k8s` | Júlio | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Júlio |
| `infra-db` | Júlio | os 3 da AWS e `DB_PASSWORD` | Júlio |
| `lambda-auth` | Kauã | os 3 da AWS e `JWT_PRIVATE_KEY` | Kauã: o Júlio tem permissão `write` (push), e secrets exigem `admin` |
| OS, Billing e Execução (`chore/ci`) | Kauã | `SONAR_TOKEN` (o deploy, quando entrar no workflow, vai pedir também os da AWS) | Kauã: o Júlio tem `write` desde 14/11/2026, e secrets exigem `admin` |

**Roteiro do Júlio (o que dá para fazer sem depender do Kauã):**

1. Fazer as adaptações da conta pessoal listadas acima.
2. Conferir na conta dele o backend do Terraform: bucket S3 `fiap-15soat-oficina-fase4-tfstate` e tabela de lock `fiap-15soat-oficina-tflock`, em `us-east-1`, criados uma vez fora do Terraform por `scripts/aws-bootstrap.sh` (`versions.tf` de `infra-k8s`, `infra-db` e `lambda-auth`). O nome antigo `fiap-15soat-oficina-tfstate` está ocupado por outra conta (Academy).
3. Rodar `terraform plan` e `terraform apply` de `infra-k8s` na máquina dele (o código já está em `main`). Numa conta sem nada, ele cria VPC, EKS, ECR e as filas SQS com as DLQs; o EKS demora.
4. Rodar `terraform plan` e `terraform apply` de `infra-db`. Ele cria a RDS (que encontra a VPC pelo nome `oficina-vpc`, por isso vem depois) e a tabela DynamoDB `oficina-billing`.
5. Conferir no console que as 3 filas, as 3 DLQs e a tabela existem, e marcar na Seção 0 a validação de SQS (D5) e DynamoDB (D6) e, nas Seções 2 e 6, os itens "na conta AWS".
6. Se algum recurso falhar, registrar o erro exato neste checklist, em "Pontos de atenção".
7. Destruir tudo ao terminar, na ordem inversa, e conferir no console que não sobrou NLB, NAT Gateway nem EKS.

Com as branches acima, `AWS_PROFILE=personal-tf SEM_NRI_BUNDLE=1 sh scripts/subir-aws.sh` faz os passos 3 e 4, cria os databases, faz o deploy, aplica o `lambda-auth` e faz o segundo deploy; `scripts/destruir-aws.sh` faz o passo 7. As imagens ARM foram conferidas (sem chaves nem `.env` dentro).

**Ordem de `apply`:** `infra-k8s` → `infra-db` → deploy dos 3 serviços → `lambda-auth`. O `lambda-auth` encontra o NLB pela tag `kubernetes.io/service-name` (`terraform/rede.tf`); aplicado antes do deploy, o `terraform apply` falha.

**Mercado Pago:** as credenciais de teste existem desde 15/11/2026 (Seção 1). Localmente, `docker compose -f docker-compose.yml -f docker-compose.mercadopago.yml up -d --build --wait` usa a API real no lugar do WireMock, lendo o `.env` (modelo em `.env.example`); o BDD continua no WireMock. A API é a mesma para teste e produção (`api.mercadopago.com`): o ambiente vem da credencial. Na AWS, o token e o `external_pos_id` entram como `Secret` do Billing. Sem eles, a geração da cobrança falha e a OS segue a compensação de falha de cobrança (D18). O webhook real também depende da rota pública no API Gateway.

### Decisões em aberto

- **D7:** o EKS compartilhado é aceito como infraestrutura própria? Decidir com o Júlio e confirmar com o professor.
- **Decididas em 08/11/2026:** corrigir a transação das rotas de operador da Execução (feito, Seção 2); gateway nginx local em uma porta só, não por agora; banco SQL na AWS com 1 instância RDS e 2 databases (`oficina_os` e `oficina_execucao`), cada um com usuário próprio; testes na AWS na conta do Júlio (em 14/11/2026: a conta pessoal, não o Academy, subindo e destruindo o ambiente a cada uso); entrada pelo caminho planejado (rota por serviço no API Gateway, com NLB interno por serviço); `postman/` versionado no OS, sem tokens; `CLAUDE.md` no OS com as regras de operação da Fase 4.
- **Seções 5 a 9:** tocadas pelo Júlio entre 14 e 16/11/2026 (manifestos, infra na AWS, observabilidade, diagrama geral).

Dá para avançar sem cloud: diagramas de sequência em Mermaid, ADRs, `README-DOCS.md`, cobertura nos READMEs de Billing e Execução, proteção de branch nos repositórios do Kauã, roteiro do vídeo e PDF.

---

## Dimensão

Contagem de 16/11/2026: **68 pendentes, 259 concluídos, 0 não verificáveis**. O peso é uma estimativa relativa, não um prazo: muitas linhas de tabela são repetições por serviço e saem rápido, enquanto poucas linhas de Saga concentram o trabalho mais difícil.

| Seção | Pendentes | Peso |
|---|---|---|
| 0. Decisões em aberto | 1 | Pequeno, mas trava o resto |
| 1. Separação em Microsserviços | 0 | **Muito grande** |
| 2. Comunicação entre Microsserviços | 0 | Grande |
| 3. Saga Pattern | 0 | **Muito grande** |
| 4. Testes e Qualidade | 0 | Grande |
| 5. CI/CD Automatizado | 34 | Médio |
| 6. Infraestrutura | 1 | Grande |
| 7. Monitoramento e Observabilidade | 4 | Médio |
| 8. Documentação da Arquitetura | 7 | Médio |
| 9. Entregáveis | 21 | Médio |
| Higiene do repositório | 0 | Pequeno |

---

## 0. Decisões em aberto

Decisões tomadas pelo Kauã em 02/11/2026. Só a D7 segue em aberto.

| # | Tipo | Decisão | Resposta | Decidido |
|---|---|---|---|---|
| D1 | Negócio | Quando o cliente paga? | Após a aprovação e antes da execução | [x] |
| D2 | Negócio | O que acontece com pagamento recusado ou QR expirado? | Devolver estoque e levar a OS a um novo status `CANCELADA`, sem nova tentativa automática | [x] |
| D3 | Negócio | A OS pode ter itens alterados depois de paga? | Não: adição e remoção bloqueadas; serviço extra vira outra OS | [x] |
| D4 | Arquitetura | Onde ficam cliente, veículo, usuário, produto e estoque? | Cliente, veículo e usuário no OS Service; produto e estoque em Execução; 3 serviços no total | [x] |
| D5 | Arquitetura | Tipo de Saga e broker | Orquestrada, com o orquestrador dentro do OS Service, e SQS (pendente validar na conta AWS) | [x] |
| D6 | Arquitetura | Banco de cada serviço | DynamoDB no Billing, PostgreSQL nos outros dois (pendente validar na conta AWS; MongoDB é o plano B) | [x] |
| D7 | Arquitetura | "Infraestrutura própria" aceita EKS compartilhado? | **Em aberto:** decidir com o Júlio e confirmar com o professor. Recomendação: um cluster só, com deploy e banco por serviço | [ ] |
| D8 | Logística | Prazo de entrega | 17/11/2026 | [x] |
| D9 | Negócio | O que acontece se o cliente não responder ao orçamento? | Quando o link vence, em 7 dias, a OS vai para `CANCELADA` | [x] |
| D10 | Negócio | Em que momento o estoque é baixado? | Na aprovação, como hoje, com devolução justificada se o pagamento falhar | [x] |
| D11 | Negócio | `PATCH /avancar-status` e `PATCH /aprovar` continuam podendo aprovar pela oficina? | Remover o atalho de `avancar-status`; manter `PATCH /aprovar` para `ADMIN` e `OPERADOR`, passando pelos mesmos passos da Saga e com pagamento obrigatório | [x] |
| D12 | Negócio | Diagnóstico vem antes do orçamento? | Sim, como hoje; a OS passa por Execução duas vezes (diagnóstico e reparo) | [x] |
| D13 | Negócio | Como o cliente recebe o QR? | Por e-mail, com imagem do QR e código copia-e-cola | [x] |
| D14 | Negócio | Qual a validade do QR? | 24 horas, configurável | [x] |
| D15 | Negócio | Itens podem mudar entre o fim do diagnóstico e o pagamento? | Não: congelam ao concluir o diagnóstico, antes do bloqueio da D3 | [x] |
| D16 | Negócio | OS sem itens ou com total zero pode concluir o diagnóstico? | Sim: encerra sem orçamento, cobrança ou reparo, em `FINALIZADA`, podendo ir a `ENTREGUE` | [x] |
| D17 | Negócio | Falha ao gerar orçamento? | A OS volta a `EM_DIAGNOSTICO`, com alerta | [x] |
| D18 | Negócio | Estoque insuficiente ou falha ao gerar cobrança depois da aprovação? | Devolver o que foi baixado e levar a OS a `CANCELADA`, sem nova tentativa automática | [x] |
| D19 | Negócio | Falha ao enviar para execução, ou pagamento depois do cancelamento? | Reembolso automático pelo Mercado Pago, devolução de estoque e `CANCELADA` | [x] |
| D20 | Negócio | Falha de e-mail e aviso de cancelamento | Orçamento *best-effort*; QR com 3 tentativas, depois falha de cobrança; cliente avisado se a OS for cancelada depois de aprovada | [x] |
| D21 | Negócio | O que sobra de `PATCH /avancar-status`? | Só `FINALIZADA → ENTREGUE`; diagnóstico e reparo passam a rotas do serviço de Execução, e os outros casos respondem 422 | [x] |
| D22 | Negócio | Reparo em andamento pode ser cancelado? | Sim, por `ADMIN` e `OPERADOR`, com motivo obrigatório e antes de `FINALIZADA`: reembolso integral, todas as peças voltam ao estoque (a oficina retira o que já instalou) e a OS vai a `CANCELADA` | [x] |
| D23 | Negócio | Efeitos aceitos do desenho | `PATCH /aprovar` responde 202; `dataInicio` passa a ser o início real do reparo, e `tempo-medio` não conta a fila; histórico da OS só para funcionários | [x] |

Levantamentos que destravam as decisões:

- [x] Ler o enunciado e comparar com a Fase 3
- [x] Validar SQS na conta AWS que for usada (D5) — conta do Júlio — validado na AWS em 15/11/2026: 3 filas e 3 DLQs criadas pelo `infra-k8s`, comandos e eventos da Saga trafegando nos dois sentidos
- [x] Validar DynamoDB na conta AWS que for usada (D6) — conta do Júlio — validado na AWS em 15/11/2026: tabela `oficina-billing` com orçamento, cobrança e outbox, acesso pela role do Pod Identity
- [x] Ler a [documentação do Mercado Pago (QR dinâmico)](https://www.mercadopago.com.br/developers/pt/docs/qr-code/payment-processing)
- [x] Criar `docs/requisitos-fase4.md`, espelho em texto do PDF, como a Fase 3 fez

## 1. Separação em Microsserviços

### Os repositórios

Mantém o prefixo `fiap-15soat-oficina-`. O monólito `fiap-15soat-oficina-api` deixa de ser o serviço de produção e fica na pasta local `legado/`, só como registro da Fase 3.

| # | Repositório | Papel | Situação |
|---|---|---|---|
| 1 | `fiap-15soat-oficina-os` | Abertura, status, histórico, orquestração da Saga, cliente, veículo e usuário | criado; PR #1 mergeado |
| 2 | `fiap-15soat-oficina-billing` | Orçamento, aprovação, pagamento, Mercado Pago | criado; PR #1 mergeado |
| 3 | `fiap-15soat-oficina-execucao` | Fila, diagnóstico, reparo, produto e estoque | criado; PR #1 mergeado |
| 4 | `fiap-15soat-oficina-lambda-auth` | Autenticação e API Gateway | Sem acesso ao banco (PR #1, 14/11/2026); rotas por serviço e role própria (PR #2, 15/11/2026) |
| 5 | `fiap-15soat-oficina-infra-k8s` (do Júlio) | VPC, EKS, ECR | Filas (PR #1); roles, Pod Identity e ECR por serviço (PR #2, 15/11/2026) |
| 6 | `fiap-15soat-oficina-infra-db` (do Júlio) | Bancos gerenciados | DynamoDB (PR #1); database e usuário por serviço na RDS (PR #2, 15/11/2026) |

- [x] Criar repositório `fiap-15soat-oficina-os`
- [x] Criar repositório `fiap-15soat-oficina-billing`
- [x] Criar repositório `fiap-15soat-oficina-execucao`
- [x] Definir a conta do GitHub dos repositórios novos — `KauaAlmeidaSilveira`; precisam ser públicos para usar o SonarCloud gratuito
- [x] Montar a tabela endpoint atual → serviço novo, sem deixar nenhum dos 37 endpoints de fora (D4)
- [x] Definir o destino de cada uma das 10 entidades JPA atuais (D4)
- [x] Decidir o destino de `fiap-15soat-oficina-api` — congelado como registro da Fase 3; o código é copiado para os repositórios novos, sem levar o histórico de commits

### OS Service

- [x] Abertura da OS
- [x] Atualização de status
- [x] Consulta de status
- [x] Histórico da OS
- [x] Rotas do cliente `GET /api/minhas-ordens-servico` e `/{id}`, com `clienteId` vindo do `sub` do token
- [x] Listagem padrão preservada (oculta finalizadas, ordena por prioridade de status)
- [x] `GET /api/ordens-servico/metricas/tempo-medio` recalculada com os dados que o serviço passa a ter
- [x] Novos status na máquina de estados de `OrdemServico` (D1, D2)
- [x] Remover o atalho `AGUARDANDO_APROVACAO → EM_EXECUCAO` em `avancarStatus` (D11)
- [x] Bloquear adição e remoção de item depois de paga (D3)
- [x] CRUD de cliente migrado, com ciclo de vida `ATIVO`/`INATIVO` (D4)
- [x] CRUD de veículo migrado, com vínculo cliente-veículo (D4)
- [x] Usuários e roles migrados, com `POST /api/auth/register` (D4)

### Billing Service

- [x] Gerar orçamento a partir dos itens da OS
- [x] Enviar orçamento por e-mail (migrar `NotificacaoAprovacaoGateway`, SMTP e template)
- [x] Aprovação e recusa pelo link (migrar `/aprovacao-os`, `TokenAprovacaoGateway` e página de confirmação)
- [x] Manter a idempotência do link de aprovação, hoje garantida pelo status da OS (segunda tentativa → 409)
- [x] Manter o token de aprovação e o de sessão mutuamente excludentes (falha de segurança já corrigida na Fase 3)
- [x] Registrar pagamento
- [x] Verificar pagamento
- [x] Informar ao OS Service o resultado, para atualizar o status após o pagamento

### Integração com Mercado Pago

- [x] Criar conta e credenciais de teste — em 15/11/2026: usuário vendedor de teste `3846952336`, loja `OFICINAMATRIZ` (`90050202`) e caixa `OFICINACAIXA1` (`140857597`), criados pela API; token no `.env` do OS, fora do git; conta compradora de teste criada no mesmo dia, usada para pagar o QR
- [x] Ler o token de variável de ambiente (`MERCADOPAGO_ACCESS_TOKEN`), nunca do repositório
- [x] Guardar o token como secret no ambiente real — validado na AWS em 15/11/2026: `Secret` `oficina-billing-secrets`, criado pelo `deploy-aws.sh` a partir do `.env`
- [x] Gerar QR dinâmico para o valor do orçamento, testado contra o simulador
- [x] Conferir o QR gerado com conta real, incluindo o caminho do `qr_data` — validado na AWS em 15/11/2026: order `type: qr`, modo `dynamic`, `qr_data` em `type_response`, enviado por e-mail
- [x] Receber o webhook no Billing, testado contra o simulador
- [x] Receber webhook em rota pública do API Gateway — validado na AWS em 15/11/2026: notificação do painel (Modo de teste) chegou ao Billing pelo Gateway
- [x] Validar a autenticidade do webhook conforme a documentação (`AssinaturaWebhook`)
- [x] Conferir o formato da assinatura com um webhook real — validado na AWS em 15/11/2026: o Mercado Pago assina o `data.id` da order na grafia original, não em minúsculas; corrigido no Billing (aceita as duas) e sem `request-id` o manifesto omite a parte
- [x] Tratar webhook repetido sem duplicar pagamento
- [x] Consultar o status na API do Mercado Pago, sem confiar só no corpo do webhook
- [x] Testar pagamento aprovado e QR expirado no BDD, contra o simulador (D2)
- [x] Testar pagamento recusado em teste de unidade do Billing, sem cenário BDD próprio
**Opcionais, não exigidos pelo enunciado** (decidido em 15/11/2026: o PDF pede a integração com o Mercado Pago, já validada na AWS; estes caminhos estão cobertos contra o WireMock no BDD e na collection, e cancelamento e QR expirado aparecem nas compensações F6, F7, F9 e F10): cancelamento da cobrança, pagamento recusado e QR expirado com conta real de teste.

- [x] Testar pagamento aprovado com conta real de teste — validado na AWS em 15/11/2026: conta compradora de teste pagou pelo app, order `processed/accredited`, cobrança `PAGA`, Saga até `CONCLUIDA` e OS `ENTREGUE`
- [x] Webhook de order inexistente responde 500 (o Billing propaga o 404 do Mercado Pago), e o Mercado Pago reenvia; responder 200 com aviso no log — achado e corrigido em 15/11/2026: o 404 vira `OrderNaoEncontradaException` e a notificação é ignorada; falha temporária continua 500, para o Mercado Pago reenviar

### Execução e Produção

- [x] Fila de execução das OS
- [x] Atualizar status durante o diagnóstico (D12)
- [x] Atualizar status durante o reparo
- [x] Comunicar finalização ao OS Service
- [x] CRUD de produto migrado (D4)
- [x] Estoque migrado: movimentações, saldo e `sincronizarSaldo` (D4)
- [x] Baixa e devolução de estoque como operações idempotentes (D10)

### Banco por serviço

- [x] Banco próprio do OS Service (D6)
- [x] Banco próprio do Billing (D6)
- [x] Banco próprio de Execução (D6)
- [x] Pelo menos um banco relacional (SQL) em uso
- [x] Pelo menos um banco não relacional (NoSQL) em uso
- [x] Justificar a escolha de banco de cada serviço
- [x] Remover FK de `ordem_servico` para `cliente`, `veiculo` e `users` se os donos mudarem (D4) — os donos não mudaram; as FKs ficam
- [x] Remover FK de `os_item` para `produto`, guardando cópia de nome, tipo e preço
- [x] Remover FK de `movimentacao_estoque` para `ordem_servico`
- [x] Dividir o `DataLoader` por serviço, com IDs coerentes entre os serviços
- [x] Decidir se migra dados do RDS atual — não migra; cada serviço recomeça com carga inicial própria

## 2. Comunicação entre Microsserviços

- [x] Definir o que é REST síncrono e o que é evento
- [x] Documentar o catálogo de eventos (nome, produtor, consumidor, payload)
- [x] Escrever o broker em Terraform (D5) — `sqs.tf`, em `main` de `infra-k8s` desde 14/11/2026 (PR #1), com `terraform validate` ok
- [x] Provisionar o broker na conta AWS (D5) — validado na AWS em 15/11/2026
- [x] Criar as filas de cada fluxo no LocalStack (D5)
- [x] Criar as filas de cada fluxo na conta AWS (D5) — validado na AWS em 15/11/2026
- [x] Configurar dead-letter queue e política de retry no Terraform e no LocalStack
- [x] Criar dead-letter queue e política de retry na conta AWS — validado na AWS em 15/11/2026
- [x] Consumidores idempotentes em cada serviço
- [x] Publicação confiável de evento junto com a gravação no banco (outbox ou equivalente), no OS, no Billing e no caminho por fila da Execução
- [x] Investigar mensagem do outbox do Billing publicada duas vezes com 2 réplicas (mesmo `OUTBOX#id` nos logs dos dois pods) — achado em 15/11/2026: o publicador admitia a duplicidade de propósito. Agora cada mensagem é reservada por 30 s com escrita condicional e só uma réplica publica; se ela cair, a reserva vence e outra publica (`PublicadorOutboxIT`)
- [x] Rotas de operador da Execução (`/diagnostico` e `/reparo`) gravando item e `outbox` na mesma transação — `@Transactional` nas 4 rotas de `ExecucaoController`; `ExecucaoControllerTransacaoIT` força falha na outbox e confirma que o item não muda
- [x] Clientes REST com timeout e tratamento de falha
- [x] Autenticar chamadas entre serviços com JWT de serviço de curta duração, definindo quem pode assiná-lo
- [x] Confirmar que nenhum serviço acessa o banco de outro
- [x] `lambda-auth` deixa de ler `users` e `cliente` no banco diretamente (`src/repositorio.js`) (D4)
- [x] `lambda-auth` deixa de gravar `senha_hash` em `cliente` diretamente (D4)
- [x] Endpoint interno de apoio à autenticação, não exposto pelo Gateway (D4)

## 3. Saga Pattern

- [x] Documentar a escolha (orquestrada ou coreografada) e justificar no README (D5)
- [x] Desenhar o fluxo feliz: abrir OS → diagnóstico → orçamento → aprovação → pagamento → execução → finalização (D1, D12)
- [x] Desenhar a compensação de cada etapa
- [x] Persistir o estado da Saga, para sobreviver a reinício de pod
- [x] Implementar o fluxo feliz
- [x] Compensação: orçamento recusado pelo cliente
- [x] Compensação: falha ao gerar orçamento
- [x] Compensação: cliente sem resposta (D9)
- [x] Compensação: pagamento recusado ou QR expirado, com devolução do estoque (D2, D10)
- [x] Compensação: falha ao enviar para execução
- [x] Compensação: estoque insuficiente na baixa (D18)
- [x] Compensação: falha ao gerar cobrança, com devolução do estoque (D18)
- [x] Compensação: cancelamento com reparo em andamento (D22)
- [x] Tratar mensagem duplicada, fora de ordem e serviço fora do ar
- [x] Forma reprodutível de provocar cada falha, para os testes e o vídeo

## 4. Testes e Qualidade

Cada critério é checado por repositório, como na Fase 3: um agregado esconde qual serviço falta.

| Critério | OS | Billing | Execução |
|---|---|---|---|
| Testes migrados do monólito | [x] | [x] | [x] |
| Testes unitários | [x] | [x] | [x] |
| Testes de integração com o broker e o banco | [x] | [x] | [x] |
| Gate de 80% sobre o serviço inteiro, não só 3 pacotes | [x] | [x] | [x] |
| Sonar sem falha no quality gate | [x] | [x] | [x] |

- [x] Escolher a ferramenta de BDD — Cucumber
- [x] Escolher a ferramenta de qualidade — SonarCloud
- [x] Configurar o Sonar no `pom.xml` dos 3 serviços
- [x] Criar os projetos no SonarCloud — trocado por SonarQube local em 15/11/2026 (`scripts/sonar-local.sh`): sem Actions, o SonarCloud não teria quem rodasse a análise. Os 3 passam no gate "Sonar way", com nota A em confiabilidade, segurança, revisão de segurança e manutenibilidade e cobertura de 95,4% (OS), 94,4% (Execução) e 92,5% (Billing); resultado e revisões no README do OS. O `ci.yml` continua com o passo do SonarCloud, que exige criar a organização se o Actions voltar
- [x] Collection do Postman com um token por role na AWS: lá cada usuário tem uma role só (`kaua@gmail.com` ADMIN, `recepcao@oficina.com` RECEPCAO, `operador@oficina.com` OPERADOR), e o token único com as 3 roles só existe no local; no Catálogo, 23 de 49 asserções falharam em cascata a partir do 403 em `POST /api/produtos` — achado em 15/11/2026 Corrigido em 15/11/2026: a pasta de Login grava também `token_operador`, usado para criar, alterar e inativar produto (única regra só de OPERADOR); o resto usa o ADMIN. Newman local 233/233 fora da pasta de Login; na AWS, conferir na próxima subida
- [x] Pelo menos um fluxo completo em BDD (abrir OS até a finalização)
- [x] Definir onde o BDD do fluxo completo roda — no repositório do OS Service, subindo os outros serviços e a fila por Docker
- [x] Cenário BDD de falha com compensação (não exigido, mas reforça a demonstração da Saga)
- [x] `lambda-auth`: atualizar os testes depois de sair do acesso direto ao banco

## 5. CI/CD Automatizado

| Etapa da pipeline | OS | Billing | Execução |
|---|---|---|---|
| Workflow de build, testes, cobertura e Sonar escrito (branch `chore/ci`) | [x] | [x] | [x] |
| PR da `chore/ci` aberto contra `main` (PR #2 de cada serviço) | [x] | [x] | [x] |
| Workflow publicado no GitHub (na `main`) | [x] | [x] | [x] |
| Build | [ ] | [ ] | [ ] |
| Testes automatizados | [ ] | [ ] | [ ] |
| Verificação de qualidade (Sonar) | [ ] | [ ] | [ ] |
| Build e push da imagem no ECR | [ ] | [ ] | [ ] |
| Deploy automatizado no Kubernetes | [ ] | [ ] | [ ] |
| Secrets cadastrados no GitHub | [ ] | [ ] | [ ] |
| Pipeline verde de ponta a ponta | [ ] | [ ] | [ ] |

O PR da `chore/ci` foi aberto em 08/11/2026, depois do merge dos PRs #1, e mostra só o `ci.yml`; foi mergeado em 14/11/2026 nos 3 serviços. Nenhuma execução começou: o GitHub Actions está bloqueado, e foi decidido seguir sem ele (ponto de atenção 8). O workflow só roda em PR ou push para `main`. As linhas de Build, Testes e Sonar só fecham quando ele rodar no GitHub. Imagem no ECR e deploy ainda não estão no workflow.

### Regras de proteção

| Regra | OS | Billing | Execução | lambda-auth | infra-k8s | infra-db |
|---|---|---|---|---|---|---|
| Branch principal chamada `main` | [x] | [x] | [x] | [ ] | [x] | [x] |
| PR obrigatório | [ ] | [ ] | [ ] | [x] | [x] | [x] |
| Checagens automáticas obrigatórias | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Push direto testado e bloqueado | [ ] | [ ] | [ ] | [x] | [x] | [x] |

Em 16/11/2026, pela conta do Kauã, o `gh api` confirmou que a `main` dos 3 serviços segue sem proteção (`Branch not protected`) e sem ruleset. Na `master` de `lambda-auth`, o PR é obrigatório, vale para administradores (`enforce_admins`) e não aceita force push, mas não há checagem obrigatória (`required_status_checks` vazio).

Em `infra-k8s` e `infra-db`, conferido em 14/11/2026 pela conta do Júlio (para a do Kauã, o `gh api` devolvia 404): a `main` exige PR, com 0 aprovações, e não exige checagem (`required_status_checks` vazio). O environment `producao` exige revisor, e é ele que segura o `apply`. Em 16/11/2026 a regra passou a valer também para administradores (`enforce_admins`), e o push direto do Júlio na `main` dos dois foi recusado (`GH006: Protected branch update failed`).

Nos 4 repositórios do Kauã, o Júlio tem só `write`, e proteção exige `admin`: falta o Kauã aplicar a mesma regra (PR obrigatório, valendo para administradores, sem force push nem exclusão) e renomear a `master` do `lambda-auth` para `main` (o `ci-cd.yml` já aceita as duas). Checagem obrigatória fica de fora enquanto o Actions estiver bloqueado: um check que nunca roda travaria todo merge.

## 6. Infraestrutura

### `infra-k8s`

- [x] ECR por serviço — `ecr.tf`, com `force_delete` (mergeado e aplicado na AWS em 15/11/2026)
- [x] Avaliar a capacidade do cluster para 3 serviços, `nri-bundle` e HPA (2 nós `t4g.medium`, máximo 3; requests de 250m e 384Mi por serviço) — validado na AWS em 15/11/2026: a conta tem cota de 1 vCPU (aumento negado), então roda com 1 node `r6g.medium` (1 vCPU, 8 GB), prefix delegation e requests de 50m; sem `nri-bundle`, que não cabe. Uso real em repouso ~6% de CPU e ~28% de memória; o HPA escalou Billing e Execução para 2 réplicas na subida
- [x] Escrever broker e demais recursos no repositório escolhido (D5) — `sqs.tf`, em `main` desde 14/11/2026
- [x] Aplicar broker e demais recursos na conta AWS (D5) — validado na AWS em 15/11/2026
- [ ] Confirmar o modelo de cluster com o professor (D7)

### `infra-db`

- [x] Escrever os bancos SQL de cada serviço, sem compartilhar database (D6) — a mesma instância, com os databases `oficina_os` e `oficina_execucao` e um usuário por serviço, criados por `scripts/criar-databases.sh` (mergeado e aplicado na AWS em 15/11/2026)
- [x] Provisionar os bancos SQL na conta AWS (D6) — validado na AWS em 15/11/2026: databases `oficina_os` e `oficina_execucao` criados por `criar-databases.sh`
- [x] Escrever o banco NoSQL (D6) — `dynamodb.tf`, em `main` desde 14/11/2026
- [x] Provisionar o banco NoSQL na conta AWS (D6) — validado na AWS em 15/11/2026
- [x] Restringir as credenciais: cada serviço só alcança o próprio banco — usuário dono só do próprio database e, no Billing, role do Pod Identity só com a tabela (mergeado e aplicado na AWS em 15/11/2026)

### `lambda-auth` (API Gateway)

- [x] Trocar a rota única `ANY /api/{proxy+}` por rotas para cada serviço (mergeado e aplicado na AWS em 15/11/2026)
- [x] Criar um NLB interno e integração por serviço — o NLB vem do `Service` de cada `k8s/`
- [x] Mover a rota pública `/aprovacao-os` para o Billing
- [x] Criar a rota pública do webhook do Mercado Pago — `POST /webhooks/mercado-pago`
- [x] Revisar `iss`, `aud` e distribuição da chave pública nos 3 serviços — revisado em 15/11/2026, sem mudança: o `iss` da sessão é o `api_endpoint` do Gateway nos dois lados (Lambda e `JWT_ISSUER`), `aud` `oficina-api`; o token interno da Lambda usa `oficina-lambda-auth`/`oficina-os`, e o de serviço, `oficina-os`/`oficina-execucao`

### Por serviço

| Item | OS | Billing | Execução |
|---|---|---|---|
| `Dockerfile` | [x] | [x] | [x] |
| `Deployment`, `Service`, `ConfigMap` | [x] | [x] | [x] |
| `HPA` | [x] | [x] | [x] |
| Health check (probes do Actuator habilitadas) | [x] | [x] | [x] |
| Health check, requests e limits nos manifestos | [x] | [x] | [x] |
| Secrets e permissão de IAM para fila e banco | [x] | [x] | [x] |

`docker-compose` local com dependências: um só, no OS, sobe o sistema inteiro (os 3 serviços, PostgreSQL, LocalStack, WireMock e Mailpit). Billing e Execução não têm compose próprio de propósito: rodam por esse e, nos testes, usam Testcontainers (decidido em 15/11/2026; a linha da tabela saiu).

- [x] `docker-compose` que suba o sistema inteiro localmente — em `fiap-15soat-oficina-os`

Não se aplica mais (decidido em 15/11/2026): o `aws rds wait` com `oficina-db` fixo era da pipeline do monólito (`fiap-15soat-oficina-api`). Os serviços da Fase 4 não esperam a RDS no CI; o deploy (`scripts/deploy-aws.sh`) lê o endpoint do output do `infra-db`.
- [x] Probes em `/actuator/health/liveness` e `/readiness` — achado em 15/11/2026 (as probes usavam o health completo, com dependências externas, e o SMTP sem credencial derrubava o Billing). Corrigido no mesmo dia: startup e liveness em `/actuator/health/liveness`, readiness em `/actuator/health/readiness`, rotas liberadas no `SecurityConfig` dos 3 serviços e cobertas por `ProbesKubernetesIT`
- [x] Atualizar o bucket de state e as chaves do Terraform para os recursos novos — validado na AWS em 15/11/2026: bucket `fiap-15soat-oficina-fase4-tfstate` (o nome antigo é de outra conta), criado por `aws-bootstrap.sh`

## 7. Monitoramento e Observabilidade

O enunciado pede o uso das ferramentas já implementadas na Fase 3: **New Relic**, account `7428180`.

| Item | OS | Billing | Execução |
|---|---|---|---|
| Agente New Relic na imagem, com `appName` próprio no `docker-compose` | [x] | [x] | [x] |
| Agente reportando no New Relic, com `appName` nos manifestos | [x] | [x] | [x] |
| Logs JSON com correlação | [x] | [x] | [x] |
| Métricas de negócio migradas | [x] | [x] | [ ] |

Os 3 agentes reportaram na conta 7428180 durante o teste de 15/11/2026 (transações e spans dos três). Execução não tem métrica própria, e as métricas do monólito ficaram no OS e no Billing; ela aparece nos painéis pelas mensagens que publica e pelo APM. Como o `nri-bundle` (que lia o `/actuator/prometheus`) não cabe no node de 1 vCPU, os painéis usam eventos customizados do agente (`EventosNewRelic`) e os logs vão pelo próprio agente, com o MDC em `context.*` (ver `observability/README.md`).

- [x] Propagar `X-Correlation-Id` na chamada REST OS → Execução
- [x] Propagar `X-Correlation-Id` na chamada REST Lambda → OS — em 15/11/2026, por `AsyncLocalStorage` (`src/correlacao.js`); a Lambda gera o id quando não vem e o devolve na resposta (branch `fix/repassar-correlation-id`)
- [x] Propagar correlação e ID da Saga nas mensagens — `Envelope` e `MDC` nos listeners
- [ ] Trace distribuído visível de ponta a ponta, atravessando o broker — escrito em 15/11/2026 (`RastreioDistribuido`: cabeçalhos do trace no campo `rastreio` do envelope, aceitos no listener com `@Trace(dispatcher = true)`), falta ver no New Relic com o ambiente no ar
- [x] Adaptar os dashboards de `observability/`, hoje amarrados ao `oficina-api` — `observability/` no OS, dashboard "Oficina Fase 4 - Microsserviços e Saga" (Serviços, Saga, Pagamentos, Rastreamento), aplicado em 15/11/2026 na conta 7428180 (`terraform apply` local; as 26 consultas NRQL conferidas pela API)
- [x] Criar painel de Saga (em andamento, concluídas, compensadas) — eventos customizados `OficinaSagaTransicao` e `OficinaMensagemPublicada`, que não dependem do `nri-bundle`
- [x] Criar painel de pagamentos — a partir dos eventos publicados pelo Billing (`OficinaMensagemPublicada`) e de `OficinaIntegracaoFalha`
- [ ] Criar alerta para falha de Saga e fila parada ou com DLQ — Saga (`COMPENSACAO_FALHOU`), falha de cobrança ou estorno, integração, erro, latência e serviço sem dados aplicados em 15/11/2026 (`observability/alertas.tf`, e-mail); a DLQ fica de fora (métrica do CloudWatch, exige integrar a conta AWS ao New Relic)
- [x] Definir o novo repositório de `observability/`, que hoje mora na `api` — no OS, junto do compose e do Postman
- [ ] Validar tudo com a infraestrutura no ar, como na Fase 3

## 8. Documentação da Arquitetura

- [x] **Diagrama geral da arquitetura final** com microsserviços, bancos e comunicação (exigido no PDF) — `docs/arquitetura.md`, em Mermaid, em 16/11/2026; levar ao PDF na Seção 9
- [ ] Diagrama de sequência da Saga: fluxo feliz
- [ ] Diagrama de sequência da Saga: fluxo com compensação
- [ ] Diagrama de sequência do pagamento com Mercado Pago
- [x] Diagrama ER ou modelo de dados de cada serviço, gerado por Mermaid — em 16/11/2026, seção "Modelo de dados" no README de cada serviço: ER do PostgreSQL no OS e na Execução, e tabela única do DynamoDB (itens, chaves e GSI) no Billing
- [x] **Descrição da estratégia de Saga** (exigido no PDF) — no README do OS; levar ao PDF na Seção 9
- [x] **Justificativa da divisão dos microsserviços e das tecnologias** (exigido no PDF) — `docs/arquitetura.md`, em 16/11/2026: dono de cada dado, alternativas descartadas e uma tabela de tecnologias com o porquê; levar ao PDF na Seção 9
- [ ] ADR da Saga
- [ ] ADR de mensageria, substituindo a ADR-0002 (comunicação síncrona via REST)
- [ ] ADR ou RFC do banco NoSQL
- [ ] Migrar as ADRs e RFCs da Fase 3 que continuam válidas

## 9. Entregáveis

### Repositórios Git

| Exigido | OS | Billing | Execução |
|---|---|---|---|
| Código-fonte em `main` (PR #1 mergeado em 08/11/2026) | [x] | [x] | [x] |
| `Dockerfile` | [x] | [x] | [x] |
| Manifestos Kubernetes | [x] | [x] | [x] |
| Pipeline de CI/CD | [ ] | [ ] | [ ] |
| Evidência de cobertura no README (print ou link) | [x] | [ ] | [ ] |
| Documentação da arquitetura do serviço | [x] | [x] | [x] |
| Swagger configurado por `springdoc` | [x] | [x] | [x] |
| Swagger abrindo com o serviço no ar | [x] | [x] | [x] |
| Collection Postman atualizada, em `postman/` do repositório do OS | [x] | [x] | [x] |
| README com propósito, tecnologias e passos | [x] | [x] | [x] |
| Diagrama no README | [x] | [x] | [x] |

Desde 16/11/2026, os 3 READMEs têm a seção "Modelo de dados" com diagrama Mermaid (ER do PostgreSQL no OS e na Execução, tabela do DynamoDB no Billing); no OS, a arquitetura geral fica em `docs/arquitetura.md`, linkada em "Documentos". Cobertura: só o README do OS tem, na tabela do SonarQube local de 15/11/2026 da seção "Qualidade" (com os números dos 3 serviços); os de Billing e Execução não citam cobertura.

- [x] Saga documentada e justificada no README do serviço correspondente
- [x] README de `lambda-auth` atualizado
- [x] READMEs de `infra-k8s` e `infra-db` atualizados — e o de `lambda-auth`, para a Fase 4
- [ ] Criar `README-DOCS.md`, índice para o avaliador, como na Fase 3
- [x] Corrigir a descrição do Swagger nos 3 serviços (`SwaggerConfig`) — em 15/11/2026: título e papel de cada serviço, autenticação por Bearer JWT com botão Authorize, versão 4.0.0 (branches `fix/descricao-swagger`)
- [x] Usuário `soat-architecture` em todos os repositórios (não está no PDF da Fase 4, mas foi exigido nas fases anteriores; confirmar) — conferido em 16/11/2026: colaborador com `write` nos 6 repositórios, sem convite pendente

### Vídeo de demonstração (YouTube/Vimeo, público ou não listado, até 15 minutos)

- [ ] Roteiro cronometrado
- [ ] Fluxo completo de uma OS passando pelos microsserviços
- [ ] Execução do Saga Pattern
- [ ] Tratamento de falhas, com compensação visível
- [ ] Deploy automatizado de pelo menos um microsserviço com validação de testes
- [ ] Monitoramento dos fluxos distribuídos
- [ ] Rastreamento dos fluxos distribuídos
- [ ] Upload e conferência do link em janela anônima

### Entrega no Portal do Aluno (PDF)

- [ ] Nome e identificação dos participantes
- [ ] Links dos repositórios
- [ ] Link do vídeo de até 15 minutos
- [ ] Diagrama geral da arquitetura final
- [ ] Descrição da estratégia escolhida para a Saga
- [ ] Justificativa da divisão dos microsserviços e das tecnologias
- [ ] Conferir que todos os links abrem sem login, ou com o acesso do avaliador

---

## Pontos de atenção que atravessam as frentes

1. **Custo da conta AWS pessoal.** Desde 14/11/2026 a AWS é a conta pessoal do Júlio, e cada hora ligada custa (EKS, NAT Gateway, nós, RDS, NLBs). Destruir tudo ao fim de cada uso, conferir no console que não sobrou nada e manter um AWS Budget com alerta. Os secrets do Academy, expirados, deixaram de valer: os `terraform plan` dos PRs falharam por token expirado.
2. **Código feito para o Academy.** Na Fase 3, o módulo `terraform-aws-modules/eks` falhou por `iam:GetRole` e foi substituído por recursos nativos, e o EKS e a Lambda reutilizam a `LabRole`. Na conta pessoal a `LabRole` não existe: as roles precisam ser criadas no Terraform antes do primeiro `apply`.
3. **Infraestrutura precisa estar no ar para gravar o vídeo.** Reservar tempo para subir tudo de novo, incluindo o ambiente com credenciais renovadas.
4. **Webhook do Mercado Pago depende de URL pública estável.** A URL do Gateway muda a cada subida: recadastrar no painel (aba Modo de teste) depois de subir. Em desenvolvimento local é preciso um túnel ou simular a chamada.
5. **Paridade funcional.** A divisão não pode perder comportamento da Fase 3: as regras de `OrdemServicoUseCase`, de `Cliente` e de estoque precisam ser conferidas contra a tabela de endpoints da Seção 1.
6. **Falha de notificação.** Resolvido pela D20: orçamento *best-effort*, QR com 3 tentativas e depois falha de cobrança.
7. **Cobertura local não é evidência.** Os números da Retomada vêm de execuções locais do JaCoCo. Sem o Actions, a evidência no README passa a ser o relatório do JaCoCo gerado localmente (print ou arquivo).
8. **GitHub Actions bloqueado nas contas `KauaAlmeidaSilveira` e `JulioNCavalcanti`.** Em 08/11/2026, os jobs dos PRs #2 não começaram: "The job was not started because your account is locked due to a billing issue". O bloqueio continuou depois de rodar de novo. A página de cobrança não mostra nada devido, e a conta nunca pagou pelo GitHub. O Actions funcionou no `lambda-auth` em 03/11/2026, então o bloqueio começou depois. Próximo passo: abrir chamado no suporte do GitHub (support.github.com) com a mensagem exata. Enquanto isso, nenhum workflow roda nos repositórios dessa conta, inclusive o `terraform` do `lambda-auth`. Em 14/11/2026, a conta `JulioNCavalcanti` apresentou o mesmo bloqueio: os workflows de `main` de `infra-k8s` e `infra-db`, disparados pelo merge, não começaram, com a mesma mensagem; o de `master` do `lambda-auth` também não. O bloqueio é do GitHub, não da AWS. **Decidido em 14/11/2026: o Actions não volta, e tudo passa a ser manual.** Ficam sem atender, a menos que o Actions volte: pipeline de CI/CD com deploy automatizado por serviço, checagens automáticas obrigatórias na `main` e, no vídeo, o "deploy automatizado de pelo menos um microsserviço com validação de testes" (`docs/requisitos-fase4.md`).
9. **Merge que cria infraestrutura.** Em `lambda-auth`, `infra-k8s` e `infra-db`, o push na branch principal dispara `terraform apply`, mas com o Actions bloqueado nada roda. Se o Actions voltar, `infra-k8s` e `infra-db` esperam aprovação no environment `producao`; em `lambda-auth` não há revisor, e o deploy roda na hora com os secrets que estiverem lá.

---

## Higiene do repositório

- [x] `.gitignore` de cada repositório novo já cobrindo `.env`, chaves JWT em qualquer diretório e `terraform.tfvars`
- [x] Nenhuma credencial do Mercado Pago, AWS ou New Relic em commit
- [x] Versionar este checklist no repositório do OS Service, já que ainda mora só na pasta local
- [x] Decidir o destino da alteração não commitada em `CHECKLIST.md` do repositório `api` da Fase 3 — não há alteração local; item encerrado em 02/11/2026
