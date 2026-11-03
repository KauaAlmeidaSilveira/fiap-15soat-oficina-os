# Checklist de Entrega — Tech Challenge Fase 4

> Estado levantado em 02/11/2026, comparando `SOAT - Fase 4 - Tech challenge.pdf` com o que foi entregue na Fase 3.
> Legenda: `[ ]` pendente · `[x]` concluído · `[?]` não verificável localmente (falta ferramenta/credencial, não falta de trabalho). Sem status "parcial": um item composto por várias partes vira uma linha por parte, cada uma binária.
> `(Dn)` indica que o item depende da decisão `n` da Seção 0.

**Entrega: 17/11/2026.** Seções 0 a 4 e higiene ficam com o Kauã; Seções 5 a 9 serão tratadas depois, junto com o Júlio.

**Resumo:** a Fase 4 é majoritariamente reconstrução. Reaproveitamos a base de domínio, EKS, API Gateway, Lambda e New Relic. Não existem hoje: divisão em serviços, banco por serviço, NoSQL, mensageria, Saga, pagamento, BDD e Sonar no CI.

## Dimensão

Contagem de 02/11/2026: **234 pendentes, 48 concluídos, 3 não verificáveis**. O peso é uma estimativa relativa, não um prazo: muitas linhas de tabela são repetições por serviço e saem rápido, enquanto poucas linhas de Saga concentram o trabalho mais difícil.

| Seção | Pendentes | Peso |
|---|---|---|
| 0. Decisões em aberto | 4 | Pequeno, mas trava o resto |
| 1. Separação em Microsserviços | 47 | **Muito grande** |
| 2. Comunicação entre Microsserviços | 13 | Grande |
| 3. Saga Pattern | 15 | **Muito grande** |
| 4. Testes e Qualidade | 17 | Grande |
| 5. CI/CD Automatizado | 36 | Médio |
| 6. Infraestrutura | 33 | Grande |
| 7. Monitoramento e Observabilidade | 18 | Médio |
| 8. Documentação da Arquitetura | 11 | Médio |
| 9. Entregáveis | 40 | Médio |
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
- [ ] Validar se o AWS Academy permite SQS (D5)
- [ ] Validar se o AWS Academy permite DynamoDB (D6)
- [ ] Ler a [documentação do Mercado Pago (QR dinâmico)](https://www.mercadopago.com.br/developers/pt/docs/qr-code/payment-processing)
- [x] Criar `docs/requisitos-fase4.md`, espelho em texto do PDF, como a Fase 3 fez

## 1. Separação em Microsserviços

### Os repositórios

Mantém o prefixo `fiap-15soat-oficina-`. O monólito `fiap-15soat-oficina-api` deixa de ser o serviço de produção.

| # | Repositório | Papel | Situação |
|---|---|---|---|
| 1 | `fiap-15soat-oficina-os` | Abertura, status, histórico, orquestração da Saga, cliente, veículo e usuário | criado |
| 2 | `fiap-15soat-oficina-billing` | Orçamento, aprovação, pagamento, Mercado Pago | criado |
| 3 | `fiap-15soat-oficina-execucao` | Fila, diagnóstico, reparo, produto e estoque | criado |
| 4 | `fiap-15soat-oficina-lambda-auth` | Autenticação e API Gateway | existe, precisa de ajustes |
| 5 | `fiap-15soat-oficina-infra-k8s` | VPC, EKS, ECR | existe, precisa de ajustes |
| 6 | `fiap-15soat-oficina-infra-db` | Bancos gerenciados | existe, precisa de ajustes |

- [x] Criar repositório `fiap-15soat-oficina-os`
- [x] Criar repositório `fiap-15soat-oficina-billing`
- [x] Criar repositório `fiap-15soat-oficina-execucao`
- [x] Definir a conta do GitHub dos repositórios novos — `KauaAlmeidaSilveira`; precisam ser públicos para usar o SonarCloud gratuito
- [x] Montar a tabela endpoint atual → serviço novo, sem deixar nenhum dos 37 endpoints de fora (D4)
- [x] Definir o destino de cada uma das 10 entidades JPA atuais (D4)
- [x] Decidir o destino de `fiap-15soat-oficina-api` — congelado como registro da Fase 3; o código é copiado para os repositórios novos, sem levar o histórico de commits

### OS Service

- [ ] Abertura da OS
- [ ] Atualização de status
- [ ] Consulta de status
- [ ] Histórico da OS
- [ ] Rotas do cliente `GET /api/minhas-ordens-servico` e `/{id}`, com `clienteId` vindo do `sub` do token
- [ ] Listagem padrão preservada (oculta finalizadas, ordena por prioridade de status)
- [ ] `GET /api/ordens-servico/metricas/tempo-medio` recalculada com os dados que o serviço passa a ter
- [ ] Novos status na máquina de estados de `OrdemServico` (D1, D2)
- [ ] Remover o atalho `AGUARDANDO_APROVACAO → EM_EXECUCAO` em `avancarStatus` (D11)
- [ ] Bloquear adição e remoção de item depois de paga (D3)
- [ ] CRUD de cliente migrado, com ciclo de vida `ATIVO`/`INATIVO` (D4)
- [ ] CRUD de veículo migrado, com vínculo cliente-veículo (D4)
- [ ] Usuários e roles migrados, com `POST /api/auth/register` (D4)

### Billing Service

- [ ] Gerar orçamento a partir dos itens da OS
- [ ] Enviar orçamento por e-mail (migrar `NotificacaoAprovacaoGateway`, SMTP e template)
- [ ] Aprovação e recusa pelo link (migrar `/aprovacao-os`, `TokenAprovacaoGateway` e página de confirmação)
- [ ] Manter a idempotência do link de aprovação, hoje garantida pelo status da OS (segunda tentativa → 409)
- [ ] Manter o token de aprovação e o de sessão mutuamente excludentes (falha de segurança já corrigida na Fase 3)
- [ ] Registrar pagamento
- [ ] Verificar pagamento
- [ ] Informar ao OS Service o resultado, para atualizar o status após o pagamento

### Integração com Mercado Pago

- [ ] Criar conta e credenciais de teste
- [ ] Guardar o token como secret, nunca no repositório
- [ ] Gerar QR dinâmico para o valor do orçamento
- [ ] Receber webhook em rota pública do API Gateway
- [ ] Validar a autenticidade do webhook conforme a documentação
- [ ] Tratar webhook repetido sem duplicar pagamento
- [ ] Consultar o status na API do Mercado Pago, sem confiar só no corpo do webhook
- [ ] Testar pagamento aprovado em ambiente de teste
- [ ] Testar pagamento recusado em ambiente de teste
- [ ] Testar QR expirado (D2)

### Execução e Produção

- [ ] Fila de execução das OS
- [ ] Atualizar status durante o diagnóstico (D12)
- [ ] Atualizar status durante o reparo
- [ ] Comunicar finalização ao OS Service
- [ ] CRUD de produto migrado (D4)
- [ ] Estoque migrado: movimentações, saldo e `sincronizarSaldo` (D4)
- [ ] Baixa e devolução de estoque como operações idempotentes (D10)

### Banco por serviço

- [ ] Banco próprio do OS Service (D6)
- [ ] Banco próprio do Billing (D6)
- [ ] Banco próprio de Execução (D6)
- [ ] Pelo menos um banco relacional (SQL) em uso
- [ ] Pelo menos um banco não relacional (NoSQL) em uso
- [ ] Justificar a escolha de banco de cada serviço
- [ ] Remover FK de `ordem_servico` para `cliente`, `veiculo` e `users` se os donos mudarem (D4)
- [ ] Remover FK de `os_item` para `produto`, guardando cópia de nome, tipo e preço
- [x] Remover FK de `movimentacao_estoque` para `ordem_servico`
- [ ] Dividir o `DataLoader` por serviço, com IDs coerentes entre os serviços
- [x] Decidir se migra dados do RDS atual — não migra; cada serviço recomeça com carga inicial própria

## 2. Comunicação entre Microsserviços

- [ ] Definir o que é REST síncrono e o que é evento
- [ ] Documentar o catálogo de eventos (nome, produtor, consumidor, payload)
- [ ] Provisionar o broker com Terraform (D5)
- [ ] Criar as filas de cada fluxo (D5)
- [ ] Criar dead-letter queue e política de retry
- [ ] Consumidores idempotentes em cada serviço
- [ ] Publicação confiável de evento junto com a gravação no banco (outbox ou equivalente)
- [ ] Clientes REST com timeout e tratamento de falha
- [ ] Autenticar chamadas entre serviços com JWT de serviço de curta duração, definindo quem pode assiná-lo
- [ ] Confirmar que nenhum serviço acessa o banco de outro
- [ ] `lambda-auth` deixa de ler `users` e `cliente` no banco diretamente (`src/repositorio.js`) (D4)
- [ ] `lambda-auth` deixa de gravar `senha_hash` em `cliente` diretamente (D4)
- [ ] Endpoint interno de apoio à autenticação, não exposto pelo Gateway (D4)

## 3. Saga Pattern

- [ ] Documentar a escolha (orquestrada ou coreografada) e justificar no README (D5)
- [ ] Desenhar o fluxo feliz: abrir OS → diagnóstico → orçamento → aprovação → pagamento → execução → finalização (D1, D12)
- [ ] Desenhar a compensação de cada etapa
- [ ] Persistir o estado da Saga, para sobreviver a reinício de pod
- [ ] Implementar o fluxo feliz
- [ ] Compensação: orçamento recusado pelo cliente
- [ ] Compensação: falha ao gerar orçamento
- [ ] Compensação: cliente sem resposta (D9)
- [ ] Compensação: pagamento recusado ou QR expirado, com devolução do estoque (D2, D10)
- [ ] Compensação: falha ao enviar para execução
- [ ] Compensação: estoque insuficiente na baixa (D18)
- [ ] Compensação: falha ao gerar cobrança, com devolução do estoque (D18)
- [ ] Compensação: cancelamento com reparo em andamento (D22)
- [ ] Tratar mensagem duplicada, fora de ordem e serviço fora do ar
- [ ] Forma reprodutível de provocar cada falha, para os testes e o vídeo

## 4. Testes e Qualidade

Cada critério é checado por repositório, como na Fase 3: um agregado esconde qual serviço falta.

| Critério | OS | Billing | Execução |
|---|---|---|---|
| Testes migrados do monólito | [x] | [ ] | [x] |
| Testes unitários | [ ] | [ ] | [ ] |
| Testes de integração com o broker e o banco | [ ] | [ ] | [ ] |
| Gate de 80% sobre o serviço inteiro, não só 3 pacotes | [ ] | [ ] | [ ] |
| Sonar sem falha no quality gate | [ ] | [ ] | [ ] |

- [x] Escolher a ferramenta de BDD — Cucumber
- [x] Escolher a ferramenta de qualidade — SonarCloud
- [ ] Criar os projetos no SonarCloud
- [ ] Pelo menos um fluxo completo em BDD (abrir OS até a finalização)
- [x] Definir onde o BDD do fluxo completo roda — no repositório do OS Service, subindo os outros serviços e a fila por Docker
- [ ] Cenário BDD de falha com compensação (não exigido, mas reforça a demonstração da Saga)
- [ ] `lambda-auth`: atualizar os testes depois de sair do acesso direto ao banco

## 5. CI/CD Automatizado

| Etapa da pipeline | OS | Billing | Execução |
|---|---|---|---|
| Build | [ ] | [ ] | [ ] |
| Testes automatizados | [ ] | [ ] | [ ] |
| Verificação de qualidade (Sonar) | [ ] | [ ] | [ ] |
| Build e push da imagem no ECR | [ ] | [ ] | [ ] |
| Deploy automatizado no Kubernetes | [ ] | [ ] | [ ] |
| Secrets cadastrados no GitHub | [ ] | [ ] | [ ] |
| Pipeline verde de ponta a ponta | [ ] | [ ] | [ ] |

### Regras de proteção

| Regra | OS | Billing | Execução | lambda-auth | infra-k8s | infra-db |
|---|---|---|---|---|---|---|
| Branch principal chamada `main` | [ ] | [ ] | [ ] | [ ] | [x] | [x] |
| PR obrigatório | [ ] | [ ] | [ ] | [x] | [x] | [x] |
| Checagens automáticas obrigatórias | [ ] | [ ] | [ ] | [?] | [?] | [?] |
| Push direto testado e bloqueado | [ ] | [ ] | [ ] | [x] | [ ] | [ ] |

`[?]`: o checklist da Fase 3 não cita status checks obrigatórios e o `gh api` devolveu 404 sem detalhar o motivo. Conferir nas configurações do GitHub.

## 6. Infraestrutura

### `infra-k8s`

- [ ] ECR por serviço (hoje há só `${project_name}-api`)
- [ ] Avaliar a capacidade do cluster para 3 serviços, `nri-bundle` e HPA (hoje 2 nós `t3.medium`, máximo 3)
- [ ] Criar broker e demais recursos no repositório escolhido (D5)
- [ ] Confirmar o modelo de cluster com o professor (D7)

### `infra-db`

- [ ] Provisionar os bancos SQL de cada serviço, sem compartilhar database (D6)
- [ ] Provisionar o banco NoSQL (D6)
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
| `Dockerfile` | [ ] | [ ] | [ ] |
| `Deployment`, `Service`, `ConfigMap` | [ ] | [ ] | [ ] |
| `HPA` | [ ] | [ ] | [ ] |
| Health check, requests e limits | [ ] | [ ] | [ ] |
| Secrets e permissão de IAM para fila e banco | [ ] | [ ] | [ ] |
| `docker-compose` local com dependências | [ ] | [ ] | [ ] |

- [ ] `docker-compose` que suba o sistema inteiro localmente
- [ ] Ajustar o CI que tem `oficina-db` fixo no `aws rds wait`
- [ ] Atualizar o bucket de state e as chaves do Terraform para os recursos novos

## 7. Monitoramento e Observabilidade

O enunciado pede o uso das ferramentas já implementadas na Fase 3: **New Relic**, account `7428180`.

| Item | OS | Billing | Execução |
|---|---|---|---|
| Agente New Relic com `appName` próprio | [ ] | [ ] | [ ] |
| Logs JSON com correlação | [ ] | [ ] | [ ] |
| Métricas de negócio migradas | [ ] | [ ] | [ ] |

- [ ] Propagar `X-Correlation-Id` nas chamadas REST entre serviços
- [ ] Propagar correlação e ID da Saga nas mensagens (hoje `CorrelationIdFilter` só cobre HTTP)
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
- [ ] **Descrição da estratégia de Saga** (exigido no PDF)
- [ ] **Justificativa da divisão dos microsserviços e das tecnologias** (exigido no PDF)
- [ ] ADR da Saga
- [ ] ADR de mensageria, substituindo a ADR-0002 (comunicação síncrona via REST)
- [ ] ADR ou RFC do banco NoSQL
- [ ] Migrar as ADRs e RFCs da Fase 3 que continuam válidas

## 9. Entregáveis

### Repositórios Git

| Exigido | OS | Billing | Execução |
|---|---|---|---|
| Código-fonte | [ ] | [ ] | [ ] |
| `Dockerfile` e manifestos Kubernetes | [ ] | [ ] | [ ] |
| Pipeline de CI/CD | [ ] | [ ] | [ ] |
| Evidência de cobertura no README (print ou link) | [ ] | [ ] | [ ] |
| Documentação da arquitetura do serviço | [ ] | [ ] | [ ] |
| Swagger ou collection Postman atualizada | [ ] | [ ] | [ ] |
| README com propósito, tecnologias, passos e diagrama | [ ] | [ ] | [ ] |

- [ ] Saga documentada e justificada no README do serviço correspondente
- [ ] READMEs de `lambda-auth`, `infra-k8s` e `infra-db` atualizados
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

1. **Credenciais AWS são temporárias (Academy).** Toda retomada começa por renovar as credenciais locais e os secrets do GitHub de todos os repositórios, agora em número maior. Na Fase 3 isso deixou pipelines vermelhas.
2. **Restrições de IAM do Academy.** Na Fase 3, o módulo `terraform-aws-modules/eks` falhou por `iam:GetRole` e foi substituído por recursos nativos. Validar cedo os recursos novos (SQS, DynamoDB).
3. **Infraestrutura precisa estar no ar para gravar o vídeo.** Reservar tempo para subir tudo de novo, incluindo o ambiente com credenciais renovadas.
4. **Webhook do Mercado Pago depende de URL pública estável.** Em desenvolvimento local é preciso um túnel ou simular a chamada.
5. **Paridade funcional.** A divisão não pode perder comportamento da Fase 3: as regras de `OrdemServicoUseCase`, de `Cliente` e de estoque precisam ser conferidas contra a tabela de endpoints da Seção 1.
6. **Falha de notificação é *best-effort* hoje.** Falha de SMTP não derruba a transição de status. Na Saga, definir se o envio do orçamento continua assim ou se vira etapa com compensação.
7. **Cobertura local não é evidência.** Os 86,9% vêm de um relatório local de 15/09/2026 do monólito e não se transferem para os serviços.

---

## Higiene do repositório

- [x] `.gitignore` de cada repositório novo já cobrindo `.env`, chaves JWT em qualquer diretório e `terraform.tfvars`
- [x] Nenhuma credencial do Mercado Pago, AWS ou New Relic em commit
- [x] Versionar este checklist no repositório do OS Service, já que ainda mora só na pasta local
- [x] Decidir o destino da alteração não commitada em `CHECKLIST.md` do repositório `api` da Fase 3 — não há alteração local; item encerrado em 02/11/2026
