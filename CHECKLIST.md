# Checklist de Entrega — Tech Challenge Fase 4

> Estado conferido em 08/11/2026 contra o código, as branches, os PRs e as regras de proteção de cada repositório.
> Legenda: `[ ]` pendente · `[x]` concluído · `[?]` não verificável localmente (falta ferramenta/credencial, não falta de trabalho). Sem status "parcial": um item composto por várias partes vira uma linha por parte, cada uma binária. Por isso, o que está pronto no local e falta validar no ambiente real aparece em duas linhas.
> `(Dn)` indica que o item depende da decisão `n` da Seção 0.

**Entrega: 17/11/2026.** Seções 0 a 4 e higiene ficam com o Kauã; Seções 5 a 9 serão tratadas depois, junto com o Júlio.

**Resumo:** a Fase 4 é majoritariamente reconstrução. Reaproveitamos a base de domínio, EKS, API Gateway, Lambda e New Relic. Não existem hoje: divisão em serviços, banco por serviço, NoSQL, mensageria, Saga, pagamento, BDD e Sonar no CI.

## Retomada: onde paramos

**Regra vigente:** avanço sozinho só no desenvolvimento local. Nada de testar em cloud, e push, PR ou merge só com pedido do Kauã.

**Em uma frase:** o código das Seções 0 a 4 está pronto e testado no computador local, mas nada foi mergeado em `main` e nada rodou em ambiente real.

### O que existe hoje

| Repositório | Branch de trabalho | Situação | Testes da última execução local |
|---|---|---|---|
| `fiap-15soat-oficina-execucao` | `feat/fila-estoque-saga` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-execucao/pull/1) aberto, com a transação das rotas de operador | 81 testes, 96,2% de cobertura |
| `fiap-15soat-oficina-billing` | `feat/orcamento-pagamento` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-billing/pull/1) aberto | 82 testes, 95,4% de cobertura |
| `fiap-15soat-oficina-os` | `feat/divisao-servicos` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-os/pull/1) aberto, com os cenários F3, F8 e F9 | 215 testes, 96,7% de cobertura, mais 12 cenários BDD |
| `fiap-15soat-oficina-lambda-auth` | `feat/sem-acesso-ao-banco` | [PR #1](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth/pull/1) aberto | 45 testes; `terraform validate` ok |
| `fiap-15soat-oficina-infra-k8s` (do Júlio) | `feat/filas-saga` | [PR #1](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-k8s/pull/1) aberto | `terraform validate` ok |
| `fiap-15soat-oficina-infra-db` (do Júlio) | `feat/tabela-billing` | [PR #1](https://github.com/JulioNCavalcanti/fiap-15soat-oficina-infra-db/pull/1) aberto | `terraform validate` ok |

Os 3 serviços também têm a branch `chore/ci`, publicada no GitHub em 08/11/2026 e sem PR, com o workflow de CI. Ele roda só em PR ou push para `main`, então ainda não rodou. Todo `[x]` de código neste checklist está nessas branches, não em `main`.

Para ver o sistema funcionando, a partir de `fiap-15soat-oficina-os`, com o Docker ligado e os repositórios de Execução e Billing na pasta vizinha:

```bash
sh scripts/gerar-chaves-locais.sh
sh scripts/rodar-bdd.sh   # sobe tudo, roda os 12 cenários e derruba
```

Para testar pelo Postman, siga a descrição da collection em `postman/oficina-fase4.collection.json`. A pasta `postman/` fica fora dos repositórios: o enunciado pede "Swagger ou collection Postman" por repositório, e o Swagger já cobre os 3 serviços.

### O que os testes provam e o que não provam

- **Provado localmente:** fluxo completo da OS até a entrega, compensações F1 a F10 e reinício do orquestrador, com os 3 serviços em Docker. A collection repete o fluxo e as falhas, menos F3 e o reinício.
- **Simulado:** SQS e DynamoDB no LocalStack, Mercado Pago no WireMock e e-mail no Mailpit.
- **Nunca testado em ambiente real:** AWS, API real do Mercado Pago, CI e SonarCloud.
- **Suposições do Mercado Pago a conferir na primeira compra de teste:** caminho do `qr_data`, método de cancelamento, formato da assinatura do webhook e status de pagamento recusado. Seguem a documentação.

### Ações na nuvem já feitas

Antes da regra acima, abri os 6 PRs. Em `lambda-auth`, `infra-k8s` e `infra-db`, o PR disparou o `terraform plan`, que falhou com `The security token included in the request is expired`. O `apply` foi pulado e nenhum recurso foi criado. Os PRs dos repositórios do Júlio foram abertos sem eu saber que eram dele.

### Por onde retomar

1. **Revisar o que foi feito:** ler os PRs na ordem Execução, Billing e OS. As diferenças em relação ao plano aprovado estão na seção 9 de `docs/plano-secoes-0-4.md`.
2. **Decidir os PRs do Júlio:** manter ou fechar `infra-k8s` e `infra-db`. O merge deles e o de `lambda-auth` dispara `terraform apply`, então só com a conta AWS definida.
3. **Fazer o merge dos serviços:** Execução, Billing e OS, nessa ordem.
4. **Liberar o CI:** abrir o PR de `chore/ci` e proteger `main` nos 3 serviços. O escopo `workflow` da conta pessoal já foi liberado e a branch já foi publicada.
5. **Criar os acessos externos:** SonarCloud, conta AWS (outra conta Academy ou a do Júlio) e conta do Mercado Pago.
6. **Validar no ambiente real:** seguir os itens "na conta AWS" e "com conta real" das Seções 0 a 2.

### Antes de testar na AWS

Aprovar e mergear os PRs coloca o código em `main`, mas não põe o sistema no ar. Ainda faltam, nas Seções 5 e 6:

- manifestos Kubernetes dos 3 serviços (`Deployment`, `Service`, `ConfigMap`, `HPA`, probes, requests e limits);
- ECR por serviço em `infra-k8s` (hoje só `${project_name}-api`);
- databases `oficina_os` e `oficina_execucao` na RDS, com usuário próprio por serviço;
- rotas por serviço no API Gateway, com um NLB interno por serviço, em `lambda-auth`;
- roteiro de deploy: secrets (chaves JWT, banco, SMTP, New Relic, Mercado Pago), build e push das imagens e `kubectl apply`.

**Ordem de merge e `apply`:** `infra-k8s` → `infra-db` → deploy dos 3 serviços → `lambda-auth`. O `lambda-auth` encontra o NLB pela tag `kubernetes.io/service-name` (`terraform/rede.tf`); aplicado antes do deploy, o `terraform apply` falha.

**Mercado Pago:** testar a cobrança na AWS exige conta e credenciais de teste do Mercado Pago (Seção 1). Sem elas, a geração da cobrança falha e a OS segue a compensação de falha de cobrança (D18). O webhook real também depende da rota pública no API Gateway.

### Decisões em aberto

- **D7:** o EKS compartilhado é aceito como infraestrutura própria? Decidir com o Júlio e confirmar com o professor.
- **Conta AWS:** outra conta Academy ou a conta do Júlio.
- **Decididas em 08/11/2026:** corrigir a transação das rotas de operador da Execução (feito, Seção 2); gateway nginx local em uma porta só, não por agora; banco SQL na AWS com 1 instância RDS e 2 databases (`oficina_os` e `oficina_execucao`), cada um com usuário próprio.
- **Seções 5 a 9:** ficam para depois, com o Júlio. Não comecei nenhuma delas de propósito; o que aparece marcado ali saiu como parte das Seções 0 a 4.

Dá para avançar sem cloud, se for liberado: manifestos Kubernetes, diagramas em Mermaid, ADRs, README e métricas.

---

## Dimensão

Contagem de 08/11/2026: **146 pendentes, 182 concluídos, 2 não verificáveis**. O peso é uma estimativa relativa, não um prazo: muitas linhas de tabela são repetições por serviço e saem rápido, enquanto poucas linhas de Saga concentram o trabalho mais difícil.

| Seção | Pendentes | Peso |
|---|---|---|
| 0. Decisões em aberto | 3 | Pequeno, mas trava o resto |
| 1. Separação em Microsserviços | 9 | **Muito grande** |
| 2. Comunicação entre Microsserviços | 3 | Grande |
| 3. Saga Pattern | 0 | **Muito grande** |
| 4. Testes e Qualidade | 4 | Grande |
| 5. CI/CD Automatizado | 40 | Médio |
| 6. Infraestrutura | 29 | Grande |
| 7. Monitoramento e Observabilidade | 12 | Médio |
| 8. Documentação da Arquitetura | 10 | Médio |
| 9. Entregáveis | 36 | Médio |
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
| D5 | Arquitetura | Tipo de Saga e broker | Orquestrada, com o orquestrador dentro do OS Service, e SQS (pendente validar no Academy) | [x] |
| D6 | Arquitetura | Banco de cada serviço | DynamoDB no Billing, PostgreSQL nos outros dois (pendente validar no Academy; MongoDB é o plano B) | [x] |
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
- [ ] Validar SQS na conta AWS que for usada (D5) — conta ainda não definida
- [ ] Validar DynamoDB na conta AWS que for usada (D6) — conta ainda não definida
- [x] Ler a [documentação do Mercado Pago (QR dinâmico)](https://www.mercadopago.com.br/developers/pt/docs/qr-code/payment-processing)
- [x] Criar `docs/requisitos-fase4.md`, espelho em texto do PDF, como a Fase 3 fez

## 1. Separação em Microsserviços

### Os repositórios

Mantém o prefixo `fiap-15soat-oficina-`. O monólito `fiap-15soat-oficina-api` deixa de ser o serviço de produção e fica na pasta local `legado/`, só como registro da Fase 3.

| # | Repositório | Papel | Situação |
|---|---|---|---|
| 1 | `fiap-15soat-oficina-os` | Abertura, status, histórico, orquestração da Saga, cliente, veículo e usuário | criado; PR #1 aberto |
| 2 | `fiap-15soat-oficina-billing` | Orçamento, aprovação, pagamento, Mercado Pago | criado; PR #1 aberto |
| 3 | `fiap-15soat-oficina-execucao` | Fila, diagnóstico, reparo, produto e estoque | criado; PR #1 aberto |
| 4 | `fiap-15soat-oficina-lambda-auth` | Autenticação e API Gateway | PR #1 tira o acesso ao banco; rotas do Gateway pendentes (Seção 6) |
| 5 | `fiap-15soat-oficina-infra-k8s` (do Júlio) | VPC, EKS, ECR | PR #1 adiciona as filas; ECR e cluster pendentes (Seção 6) |
| 6 | `fiap-15soat-oficina-infra-db` (do Júlio) | Bancos gerenciados | PR #1 adiciona o DynamoDB; bancos SQL pendentes (Seção 6) |

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

- [ ] Criar conta e credenciais de teste
- [x] Ler o token de variável de ambiente (`MERCADOPAGO_ACCESS_TOKEN`), nunca do repositório
- [ ] Guardar o token como secret no ambiente real
- [x] Gerar QR dinâmico para o valor do orçamento, testado contra o simulador
- [ ] Conferir o QR gerado com conta real, incluindo o caminho do `qr_data`
- [x] Receber o webhook no Billing, testado contra o simulador
- [ ] Receber webhook em rota pública do API Gateway
- [x] Validar a autenticidade do webhook conforme a documentação (`AssinaturaWebhook`)
- [ ] Conferir o formato da assinatura com um webhook real
- [ ] Conferir o cancelamento da cobrança com conta real
- [x] Tratar webhook repetido sem duplicar pagamento
- [x] Consultar o status na API do Mercado Pago, sem confiar só no corpo do webhook
- [x] Testar pagamento aprovado e QR expirado no BDD, contra o simulador (D2)
- [x] Testar pagamento recusado em teste de unidade do Billing, sem cenário BDD próprio
- [ ] Testar pagamento aprovado com conta real de teste
- [ ] Testar pagamento recusado com conta real de teste
- [ ] Testar QR expirado com conta real de teste (D2)

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
- [x] Escrever o broker em Terraform (D5) — `sqs.tf`, no PR #1 de `infra-k8s`, com `terraform validate` ok
- [ ] Provisionar o broker na conta AWS (D5)
- [x] Criar as filas de cada fluxo no LocalStack (D5)
- [ ] Criar as filas de cada fluxo na conta AWS (D5)
- [x] Configurar dead-letter queue e política de retry no Terraform e no LocalStack
- [ ] Criar dead-letter queue e política de retry na conta AWS
- [x] Consumidores idempotentes em cada serviço
- [x] Publicação confiável de evento junto com a gravação no banco (outbox ou equivalente), no OS, no Billing e no caminho por fila da Execução
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
| Sonar sem falha no quality gate | [ ] | [ ] | [ ] |

- [x] Escolher a ferramenta de BDD — Cucumber
- [x] Escolher a ferramenta de qualidade — SonarCloud
- [x] Configurar o Sonar no `pom.xml` dos 3 serviços
- [ ] Criar os projetos no SonarCloud
- [x] Pelo menos um fluxo completo em BDD (abrir OS até a finalização)
- [x] Definir onde o BDD do fluxo completo roda — no repositório do OS Service, subindo os outros serviços e a fila por Docker
- [x] Cenário BDD de falha com compensação (não exigido, mas reforça a demonstração da Saga)
- [x] `lambda-auth`: atualizar os testes depois de sair do acesso direto ao banco

## 5. CI/CD Automatizado

| Etapa da pipeline | OS | Billing | Execução |
|---|---|---|---|
| Workflow de build, testes, cobertura e Sonar escrito (branch `chore/ci`, publicada, sem PR) | [x] | [x] | [x] |
| PR da `chore/ci` aberto contra `main`, depois do merge do PR #1 do serviço | [ ] | [ ] | [ ] |
| Workflow publicado no GitHub (na `main`) | [ ] | [ ] | [ ] |
| Build | [ ] | [ ] | [ ] |
| Testes automatizados | [ ] | [ ] | [ ] |
| Verificação de qualidade (Sonar) | [ ] | [ ] | [ ] |
| Build e push da imagem no ECR | [ ] | [ ] | [ ] |
| Deploy automatizado no Kubernetes | [ ] | [ ] | [ ] |
| Secrets cadastrados no GitHub | [ ] | [ ] | [ ] |
| Pipeline verde de ponta a ponta | [ ] | [ ] | [ ] |

A branch `chore/ci` foi publicada em 08/11/2026, mas ainda não tem PR. Ela saiu da `feat/*`, então o PR dela fica para depois do merge do PR #1 de cada serviço; aberto antes contra `main`, ele mostraria também os commits da `feat/*`. O workflow só roda em PR ou push para `main`. As linhas de Build, Testes e Sonar só fecham quando ele rodar no GitHub. Imagem no ECR e deploy ainda não estão no workflow.

### Regras de proteção

| Regra | OS | Billing | Execução | lambda-auth | infra-k8s | infra-db |
|---|---|---|---|---|---|---|
| Branch principal chamada `main` | [x] | [x] | [x] | [ ] | [x] | [x] |
| PR obrigatório | [ ] | [ ] | [ ] | [x] | [x] | [x] |
| Checagens automáticas obrigatórias | [ ] | [ ] | [ ] | [ ] | [?] | [?] |
| Push direto testado e bloqueado | [ ] | [ ] | [ ] | [x] | [ ] | [ ] |

Em 08/11/2026, o `gh api` confirmou de novo que a `main` dos 3 serviços está sem proteção (`Branch not protected`). Na `master` de `lambda-auth`, o PR é obrigatório, mas não há checagem obrigatória (`required_status_checks` vazio).

`[?]`: em `infra-k8s` e `infra-db`, o `gh api` devolve 404 para a conta do Kauã, sem detalhar o motivo, e não há ruleset na `main`. Conferir com o Júlio, dono dos repositórios.

## 6. Infraestrutura

### `infra-k8s`

- [ ] ECR por serviço (hoje há só `${project_name}-api`)
- [ ] Avaliar a capacidade do cluster para 3 serviços, `nri-bundle` e HPA (hoje 2 nós `t3.medium`, máximo 3)
- [x] Escrever broker e demais recursos no repositório escolhido (D5) — `sqs.tf`, no PR #1
- [ ] Aplicar broker e demais recursos na conta AWS (D5)
- [ ] Confirmar o modelo de cluster com o professor (D7)

### `infra-db`

- [ ] Escrever os bancos SQL de cada serviço, sem compartilhar database (D6) — hoje há uma só instância `aws_db_instance`; decidido em 08/11/2026: a mesma instância, com os databases `oficina_os` e `oficina_execucao` e um usuário por serviço
- [ ] Provisionar os bancos SQL na conta AWS (D6)
- [x] Escrever o banco NoSQL (D6) — `dynamodb.tf`, no PR #1
- [ ] Provisionar o banco NoSQL na conta AWS (D6)
- [ ] Restringir as credenciais: cada serviço só alcança o próprio banco

### `lambda-auth` (API Gateway)

- [ ] Trocar a rota única `ANY /api/{proxy+}` por rotas para cada serviço
- [ ] Criar um NLB interno e integração por serviço
- [ ] Mover a rota pública `/aprovacao-os` para o Billing
- [ ] Criar a rota pública do webhook do Mercado Pago
- [ ] Revisar `iss`, `aud` e distribuição da chave pública nos 3 serviços

### Por serviço

| Item | OS | Billing | Execução |
|---|---|---|---|
| `Dockerfile` | [x] | [x] | [x] |
| `Deployment`, `Service`, `ConfigMap` | [ ] | [ ] | [ ] |
| `HPA` | [ ] | [ ] | [ ] |
| Health check (probes do Actuator habilitadas) | [x] | [x] | [x] |
| Health check, requests e limits nos manifestos | [ ] | [ ] | [ ] |
| Secrets e permissão de IAM para fila e banco | [ ] | [ ] | [ ] |
| `docker-compose` local com dependências | [x] | [ ] | [ ] |

Billing e Execução não têm `docker-compose` próprio: sobem pelo do OS, e seus testes usam Testcontainers. Se isso bastar, as duas linhas podem sair.

- [x] `docker-compose` que suba o sistema inteiro localmente — em `fiap-15soat-oficina-os`
- [ ] Ajustar o CI que tem `oficina-db` fixo no `aws rds wait` — está na pipeline do monólito; vale para o deploy dos serviços novos
- [ ] Atualizar o bucket de state e as chaves do Terraform para os recursos novos

## 7. Monitoramento e Observabilidade

O enunciado pede o uso das ferramentas já implementadas na Fase 3: **New Relic**, account `7428180`.

| Item | OS | Billing | Execução |
|---|---|---|---|
| Agente New Relic na imagem, com `appName` próprio no `docker-compose` | [x] | [x] | [x] |
| Agente reportando no New Relic, com `appName` nos manifestos | [ ] | [ ] | [ ] |
| Logs JSON com correlação | [x] | [x] | [x] |
| Métricas de negócio migradas | [x] | [x] | [ ] |

Execução não tem métrica própria, e as métricas do monólito ficaram no OS e no Billing. Falta decidir se ela precisa de alguma. O Billing só tem `oficina.integracao.falhas`, então o painel de pagamentos vai exigir métricas novas.

- [x] Propagar `X-Correlation-Id` na chamada REST OS → Execução
- [ ] Propagar `X-Correlation-Id` na chamada REST Lambda → OS — o handler lê o cabeçalho, mas `src/repositorio.js` não o repassa
- [x] Propagar correlação e ID da Saga nas mensagens — `Envelope` e `MDC` nos listeners
- [ ] Trace distribuído visível de ponta a ponta, atravessando o broker
- [ ] Adaptar os dashboards de `observability/`, hoje amarrados ao `oficina-api`
- [ ] Criar painel de Saga (em andamento, concluídas, compensadas)
- [ ] Criar painel de pagamentos
- [ ] Criar alerta para falha de Saga e fila parada ou com DLQ
- [ ] Definir o novo repositório de `observability/`, que hoje mora na `api`
- [ ] Validar tudo com a infraestrutura no ar, como na Fase 3

## 8. Documentação da Arquitetura

- [ ] **Diagrama geral da arquitetura final** com microsserviços, bancos e comunicação (exigido no PDF)
- [ ] Diagrama de sequência da Saga: fluxo feliz
- [ ] Diagrama de sequência da Saga: fluxo com compensação
- [ ] Diagrama de sequência do pagamento com Mercado Pago
- [ ] Diagrama ER ou modelo de dados de cada serviço, gerado por Mermaid
- [x] **Descrição da estratégia de Saga** (exigido no PDF) — no README do OS; levar ao PDF na Seção 9
- [ ] **Justificativa da divisão dos microsserviços e das tecnologias** (exigido no PDF) — o README do OS justifica comunicação e bancos; falta a divisão dos serviços
- [ ] ADR da Saga
- [ ] ADR de mensageria, substituindo a ADR-0002 (comunicação síncrona via REST)
- [ ] ADR ou RFC do banco NoSQL
- [ ] Migrar as ADRs e RFCs da Fase 3 que continuam válidas

## 9. Entregáveis

### Repositórios Git

| Exigido | OS | Billing | Execução |
|---|---|---|---|
| Código-fonte em `main` (hoje em PR) | [ ] | [ ] | [ ] |
| `Dockerfile` | [x] | [x] | [x] |
| Manifestos Kubernetes | [ ] | [ ] | [ ] |
| Pipeline de CI/CD | [ ] | [ ] | [ ] |
| Evidência de cobertura no README (print ou link) | [ ] | [ ] | [ ] |
| Documentação da arquitetura do serviço | [ ] | [ ] | [ ] |
| Swagger configurado por `springdoc` | [x] | [x] | [x] |
| Swagger abrindo com o serviço no ar | [x] | [x] | [x] |
| Collection Postman atualizada, em `postman/` na pasta da Fase 4 | [x] | [x] | [x] |
| README com propósito, tecnologias e passos | [x] | [x] | [x] |
| Diagrama no README | [ ] | [ ] | [ ] |

- [x] Saga documentada e justificada no README do serviço correspondente
- [x] README de `lambda-auth` atualizado
- [ ] READMEs de `infra-k8s` e `infra-db` atualizados
- [ ] Criar `README-DOCS.md`, índice para o avaliador, como na Fase 3
- [ ] Usuário `soat-architecture` em todos os repositórios (não está no PDF da Fase 4, mas foi exigido nas fases anteriores; confirmar)

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

1. **Credenciais AWS são temporárias (Academy).** Toda retomada começa por renovar as credenciais locais e os secrets do GitHub de todos os repositórios, agora em número maior. Na Fase 3 isso deixou pipelines vermelhas, e os `terraform plan` dos PRs atuais falharam por token expirado.
2. **Restrições de IAM do Academy.** Na Fase 3, o módulo `terraform-aws-modules/eks` falhou por `iam:GetRole` e foi substituído por recursos nativos. Validar cedo os recursos novos (SQS, DynamoDB).
3. **Infraestrutura precisa estar no ar para gravar o vídeo.** Reservar tempo para subir tudo de novo, incluindo o ambiente com credenciais renovadas.
4. **Webhook do Mercado Pago depende de URL pública estável.** Em desenvolvimento local é preciso um túnel ou simular a chamada.
5. **Paridade funcional.** A divisão não pode perder comportamento da Fase 3: as regras de `OrdemServicoUseCase`, de `Cliente` e de estoque precisam ser conferidas contra a tabela de endpoints da Seção 1.
6. **Falha de notificação.** Resolvido pela D20: orçamento *best-effort*, QR com 3 tentativas e depois falha de cobrança.
7. **Cobertura local não é evidência.** Os números da Retomada vêm de execuções locais do JaCoCo. A evidência no README depende do CI rodando.
8. **Merge que cria infraestrutura.** Em `lambda-auth`, `infra-k8s` e `infra-db`, o merge dispara `terraform apply`. Não fazer sem a conta AWS definida.

---

## Higiene do repositório

- [x] `.gitignore` de cada repositório novo já cobrindo `.env`, chaves JWT em qualquer diretório e `terraform.tfvars`
- [x] Nenhuma credencial do Mercado Pago, AWS ou New Relic em commit
- [x] Versionar este checklist no repositório do OS Service, já que ainda mora só na pasta local
- [x] Decidir o destino da alteração não commitada em `CHECKLIST.md` do repositório `api` da Fase 3 — não há alteração local; item encerrado em 02/11/2026
