# Dashboard da Fase 4: um sistema, três serviços e a Saga entre eles.
#
# Fontes de dados:
# - Transaction e Span: agente APM de cada serviço (appName em var.apps);
# - Oficina*: eventos customizados gravados pelos serviços (EventosNewRelic), que não dependem do
#   nri-bundle. OficinaSagaTransicao, OficinaOsCriada, OficinaOsTransicao e OficinaSagaEventoForaDeEstado
#   vêm do OS; OficinaIntegracaoFalha, do OS e do Billing; OficinaMensagemPublicada, dos três (outbox);
# - Log: logs enviados pelo agente, com o MDC em context.correlationId e context.sagaId.

locals {
  apps_nrql = join(", ", [for app in values(var.apps) : "'${app}'"])
}

resource "newrelic_one_dashboard" "oficina" {
  name        = "Oficina Fase 4 - Microsserviços e Saga"
  permissions = "public_read_only"

  page {
    name = "Serviços"

    widget_line {
      title  = "Requisições e mensagens por minuto"
      row    = 1
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT rate(count(*), 1 minute) FROM Transaction WHERE appName IN (${local.apps_nrql}) FACET appName TIMESERIES AUTO"
      }
    }

    widget_line {
      title  = "Latência média (ms)"
      row    = 1
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT average(duration) * 1000 FROM Transaction WHERE appName IN (${local.apps_nrql}) FACET appName TIMESERIES AUTO"
      }
    }

    widget_line {
      title  = "Taxa de erro (%)"
      row    = 4
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT percentage(count(*), WHERE error IS true) FROM Transaction WHERE appName IN (${local.apps_nrql}) FACET appName TIMESERIES AUTO"
      }
    }

    widget_table {
      title  = "Mensagens consumidas do SQS"
      row    = 4
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*), average(duration) * 1000 AS 'ms' FROM Transaction WHERE appName IN (${local.apps_nrql}) AND name LIKE 'OtherTransaction/Message/SQS/%' FACET appName, name SINCE 1 day ago"
      }
    }

    widget_table {
      title  = "Endpoints mais lentos"
      row    = 7
      column = 1
      width  = 12
      height = 3
      nrql_query {
        query = "SELECT average(duration) * 1000 AS 'ms', count(*) AS 'chamadas' FROM Transaction WHERE appName IN (${local.apps_nrql}) AND transactionType = 'Web' FACET appName, name SINCE 1 hour ago LIMIT 20"
      }
    }
  }

  page {
    name = "Saga"

    widget_billboard {
      title  = "OS criadas"
      row    = 1
      column = 1
      width  = 3
      height = 3
      nrql_query {
        query = "SELECT count(*) AS 'OS' FROM OficinaOsCriada SINCE 1 day ago"
      }
    }

    widget_billboard {
      title  = "Sagas encerradas"
      row    = 1
      column = 4
      width  = 5
      height = 3
      nrql_query {
        query = "SELECT filter(count(*), WHERE para = 'CONCLUIDA') AS 'Concluídas', filter(count(*), WHERE para = 'REPROVADA') AS 'Reprovadas', filter(count(*), WHERE para = 'COMPENSADA') AS 'Compensadas', filter(count(*), WHERE para = 'COMPENSACAO_FALHOU') AS 'Compensação falhou' FROM OficinaSagaTransicao SINCE 1 day ago"
      }
    }

    widget_billboard {
      title  = "Eventos fora de estado (descartados)"
      row    = 1
      column = 9
      width  = 4
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaSagaEventoForaDeEstado SINCE 1 day ago"
      }
    }

    widget_line {
      title  = "Transições da Saga por estado de destino"
      row    = 4
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaSagaTransicao FACET para TIMESERIES AUTO"
      }
    }

    widget_table {
      title  = "Mensagens da Saga publicadas (outbox → SQS)"
      row    = 4
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaMensagemPublicada FACET servico, tipo SINCE 1 day ago LIMIT 50"
      }
    }

    widget_bar {
      title  = "Tempo médio no status da OS (minutos)"
      row    = 7
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT average(segundosNoStatusAnterior) / 60 FROM OficinaOsTransicao FACET de SINCE 1 day ago"
      }
    }

    widget_table {
      title  = "Compensações: de onde a Saga saiu"
      row    = 7
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaSagaTransicao WHERE para IN ('CANCELANDO_REPARO', 'ENCERRANDO_COBRANCA', 'ESTORNANDO_PAGAMENTO', 'DEVOLVENDO_ESTOQUE', 'CANCELANDO_ORCAMENTO', 'COMPENSADA', 'COMPENSACAO_FALHOU') FACET de, para SINCE 1 day ago"
      }
    }
  }

  page {
    name = "Pagamentos"

    widget_billboard {
      title  = "Orçamentos"
      row    = 1
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT filter(count(*), WHERE tipo = 'OrcamentoGerado') AS 'Gerados', filter(count(*), WHERE tipo = 'OrcamentoAprovado') AS 'Aprovados', filter(count(*), WHERE tipo = 'OrcamentoRecusado') AS 'Recusados', filter(count(*), WHERE tipo = 'OrcamentoExpirado') AS 'Expirados' FROM OficinaMensagemPublicada WHERE servico = 'billing' SINCE 1 day ago"
      }
    }

    widget_billboard {
      title  = "Cobranças (Mercado Pago)"
      row    = 1
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT filter(count(*), WHERE tipo = 'CobrancaGerada') AS 'QR gerado', filter(count(*), WHERE tipo = 'PagamentoConfirmado') AS 'Pagas', filter(count(*), WHERE tipo = 'PagamentoFalhou') AS 'Não pagas', filter(count(*), WHERE tipo = 'CobrancaFalhou') AS 'Falha ao gerar', filter(count(*), WHERE tipo = 'PagamentoEstornado') AS 'Estornadas', filter(count(*), WHERE tipo = 'EstornoFalhou') AS 'Estorno falhou' FROM OficinaMensagemPublicada WHERE servico = 'billing' SINCE 1 day ago"
      }
    }

    widget_line {
      title  = "Eventos de pagamento ao longo do tempo"
      row    = 4
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaMensagemPublicada WHERE servico = 'billing' AND tipo IN ('CobrancaGerada', 'PagamentoConfirmado', 'PagamentoFalhou', 'CobrancaFalhou', 'PagamentoEstornado', 'EstornoFalhou') FACET tipo TIMESERIES AUTO"
      }
    }

    widget_table {
      title  = "Falhas de integração (Mercado Pago, e-mail, Execução)"
      row    = 4
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM OficinaIntegracaoFalha FACET servico, integracao, motivo SINCE 1 day ago"
      }
    }

    widget_line {
      title  = "Webhook do Mercado Pago: chamadas e latência (ms)"
      row    = 7
      column = 1
      width  = 12
      height = 3
      nrql_query {
        query = "SELECT count(*) AS 'chamadas', average(duration) * 1000 AS 'ms' FROM Transaction WHERE appName = '${var.apps.billing}' AND name LIKE '%webhooks%' TIMESERIES AUTO"
      }
    }
  }

  page {
    name = "Rastreamento"

    widget_table {
      title  = "Últimas Sagas: logs por correlationId (os 3 serviços)"
      row    = 1
      column = 1
      width  = 12
      height = 4
      nrql_query {
        query = "SELECT latest(timestamp) AS 'última', uniqueCount(entity.name) AS 'serviços', count(*) AS 'linhas' FROM Log WHERE context.correlationId IS NOT NULL FACET context.correlationId, context.sagaId SINCE 1 day ago LIMIT 20"
      }
    }

    widget_table {
      title  = "Logs recentes com correlação"
      row    = 5
      column = 1
      width  = 12
      height = 4
      nrql_query {
        query = "SELECT entity.name, context.correlationId, level, message FROM Log WHERE context.correlationId IS NOT NULL SINCE 30 minutes ago LIMIT 100"
      }
    }

    widget_table {
      title  = "Traces distribuídos que atravessam mais de um serviço"
      row    = 9
      column = 1
      width  = 12
      height = 3
      nrql_query {
        query = "SELECT uniqueCount(entity.name) AS 'serviços', count(*) AS 'spans', max(timestamp) AS 'fim' FROM Span WHERE entity.name IN (${local.apps_nrql}) FACET trace.id SINCE 1 hour ago LIMIT 20"
      }
    }
  }
}

output "dashboard_url" {
  description = "Link do dashboard no New Relic"
  value       = newrelic_one_dashboard.oficina.permalink
}
