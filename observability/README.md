# Observabilidade (New Relic)

Dashboard e alertas da Fase 4 em Terraform. Seguem a Fase 3 (mesma ferramenta, mesma abordagem como
código), agora para três serviços e a Saga entre eles. Ficam no repositório do OS porque o NRQL depende
dos nomes de eventos e métricas emitidos pelos serviços.

## O que cada serviço envia

| Dado | Origem | Para que serve |
|---|---|---|
| `Transaction`, `Span` | agente APM (`NEW_RELIC_APP_NAME`: `oficina-os`, `oficina-execucao`, `oficina-billing`) | latência, erro, vazão e trace distribuído |
| Trace pelo SQS | `RastreioDistribuido`: os cabeçalhos do trace vão no campo `rastreio` do envelope e o listener os aceita | um trace só do Gateway até o último serviço, atravessando as filas |
| `Log` | agente, com `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED` e o MDC em `context.correlationId` e `context.sagaId` | seguir uma OS pelos 3 serviços |
| `OficinaSagaTransicao`, `OficinaOsCriada`, `OficinaOsTransicao`, `OficinaSagaEventoForaDeEstado` | OS (`MicrometerMetricasGateway`) | painel da Saga |
| `OficinaMensagemPublicada` | os 3 (publicador do outbox) | mensagens da Saga e funil de pagamento |
| `OficinaIntegracaoFalha` | OS e Billing | falhas de Mercado Pago, e-mail e Execução |

Os eventos `Oficina*` são eventos customizados do agente (`EventosNewRelic`): não dependem do
`nri-bundle`, que não cabe no node de 1 vCPU da conta. As métricas Micrometer continuam expostas em
`/actuator/prometheus` para quando o `nri-bundle` voltar.

## Dashboard "Oficina Fase 4 - Microsserviços e Saga"

- **Serviços:** vazão, latência e erro por serviço, mensagens consumidas do SQS, endpoints mais lentos.
- **Saga:** OS criadas, Sagas concluídas, reprovadas, compensadas e com compensação falha, transições,
  mensagens publicadas por tipo, tempo médio por status da OS, caminho das compensações.
- **Pagamentos:** orçamentos (gerados, aprovados, recusados, expirados), cobranças (QR gerado, pagas, não
  pagas, falha ao gerar, estornadas), falhas de integração e o webhook do Mercado Pago.
- **Rastreamento:** Sagas recentes por `correlationId` nos logs dos 3 serviços, logs correlacionados e os
  traces que atravessam mais de um serviço.

## Alertas (política "Oficina Fase 4 - Operação", por e-mail)

| Condição | Dispara quando |
|---|---|
| Saga com compensação que falhou | alguma Saga chega a `COMPENSACAO_FALHOU` |
| Falha ao gerar cobrança ou estornar pagamento | o Billing publica `CobrancaFalhou` ou `EstornoFalhou` |
| Falha no processamento de ordens de serviço | qualquer `OficinaIntegracaoFalha` |
| Taxa de erro acima de 5% | por serviço, por 5 minutos |
| Latência média acima de 2 segundos | por serviço, por 5 minutos |
| Serviço sem reportar dados | desligado por padrão (`alertas_sem_dados`): com o ambiente destruído dispararia sempre |

Mensagem na DLQ não tem alerta: o tamanho da fila é métrica do CloudWatch e só chega ao New Relic com a
integração AWS da conta. O efeito visível na aplicação (Saga parada ou com compensação falha) está coberto.

## Evidências

Prints de 17/11/2026, com o ambiente no ar: dashboard (4 abas), trace distribuído atravessando o SQS, APM dos 3
serviços e condições de alerta, em [`docs/evidencias/new-relic/`](../docs/evidencias/new-relic/README.md).

## Aplicar

```bash
cd observability
cp terraform.tfvars.example terraform.tfvars   # account id, User API key (NRAK-), região e e-mail
terraform init
terraform apply                                # state local, gitignorado
terraform apply -var alertas_sem_dados=true    # só com o ambiente no ar
```

Dashboards e alertas independem da AWS: podem ficar criados entre as subidas do ambiente.
