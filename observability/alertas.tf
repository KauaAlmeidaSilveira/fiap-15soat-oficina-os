# Alertas da Fase 4, notificados por e-mail. Os quatro da Fase 3 (falha de integração, erro, latência e
# aplicação sem dados) agora valem para os três serviços, um incidente por serviço (FACET appName), e
# entram os da Saga.
#
# Mensagem parada na DLQ não está aqui: o número de mensagens da fila é métrica do CloudWatch, que só
# chega ao New Relic com a integração AWS da conta (fora do escopo; a conta AWS é destruída a cada uso).
# O sintoma que chega pela aplicação é a Saga em COMPENSACAO_FALHOU ou parada, coberto abaixo.

resource "newrelic_alert_policy" "oficina" {
  name                = "Oficina Fase 4 - Operação"
  incident_preference = "PER_CONDITION_AND_TARGET"
}

locals {
  # Mesmos parâmetros para todas as condições: janela de 1 min, sinal avaliado por 5 min.
  condicoes = {
    saga_compensacao_falhou = {
      nome      = "Saga com compensação que falhou"
      descricao = "Uma Saga chegou a COMPENSACAO_FALHOU: exige reprocessamento manual (POST /api/ordens-servico/{id}/saga/reprocessar)."
      query     = "SELECT count(*) FROM OficinaSagaTransicao WHERE para = 'COMPENSACAO_FALHOU'"
      limite    = 0
      ocorre    = "at_least_once"
    }
    falha_pagamento = {
      nome      = "Falha ao gerar cobrança ou estornar pagamento"
      descricao = "O Billing publicou CobrancaFalhou ou EstornoFalhou: Mercado Pago indisponível ou recusando."
      query     = "SELECT count(*) FROM OficinaMensagemPublicada WHERE servico = 'billing' AND tipo IN ('CobrancaFalhou', 'EstornoFalhou')"
      limite    = 0
      ocorre    = "at_least_once"
    }
    falhas_integracao = {
      nome      = "Falha no processamento de ordens de serviço"
      descricao = "Falha registrada nas integrações (Mercado Pago, e-mail, Execução): OficinaIntegracaoFalha."
      query     = "SELECT count(*) FROM OficinaIntegracaoFalha FACET servico"
      limite    = 0
      ocorre    = "at_least_once"
    }
    taxa_de_erro = {
      nome      = "Taxa de erro acima de 5%"
      descricao = "Percentual de transações com erro, por serviço."
      query     = "SELECT percentage(count(*), WHERE error IS true) FROM Transaction WHERE appName IN (${local.apps_nrql}) FACET appName"
      limite    = 5
      ocorre    = "all"
    }
    latencia = {
      nome      = "Latência média acima de 2 segundos"
      descricao = "Latência média das transações web, por serviço."
      query     = "SELECT average(duration) FROM Transaction WHERE appName IN (${local.apps_nrql}) AND transactionType = 'Web' FACET appName"
      limite    = 2
      ocorre    = "all"
    }
  }
}

resource "newrelic_nrql_alert_condition" "oficina" {
  for_each = local.condicoes

  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = each.value.nome
  description = each.value.descricao
  type        = "static"
  enabled     = true

  nrql {
    query = each.value.query
  }

  critical {
    operator              = "above"
    threshold             = each.value.limite
    threshold_duration    = 300
    threshold_occurrences = each.value.ocorre
  }

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

# Serviço que parou de reportar: perda de sinal por appName. Fica ligado só enquanto o ambiente está no
# ar; depois do destroy todos ficariam sem dados, então desabilitar com alertas_sem_dados = false.
resource "newrelic_nrql_alert_condition" "sem_dados" {
  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = "Serviço sem reportar dados"
  description = "Nenhuma transação de um dos serviços por 10 minutos."
  type        = "static"
  enabled     = var.alertas_sem_dados

  nrql {
    query = "SELECT count(*) FROM Transaction WHERE appName IN (${local.apps_nrql}) FACET appName"
  }

  critical {
    operator              = "below"
    threshold             = 1
    threshold_duration    = 600
    threshold_occurrences = "all"
  }

  expiration_duration            = 600
  open_violation_on_expiration   = true
  close_violations_on_expiration = false

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

resource "newrelic_notification_destination" "email" {
  account_id = var.newrelic_account_id
  name       = "Oficina Fase 4 - e-mail do time"
  type       = "EMAIL"

  property {
    key   = "email"
    value = var.alertas_email
  }
}

resource "newrelic_notification_channel" "email" {
  account_id     = var.newrelic_account_id
  name           = "Oficina Fase 4 - canal de e-mail"
  type           = "EMAIL"
  destination_id = newrelic_notification_destination.email.id
  product        = "IINT"

  property {
    key   = "subject"
    value = "[Oficina] {{ issueTitle }}"
  }
}

resource "newrelic_workflow" "oficina" {
  account_id            = var.newrelic_account_id
  name                  = "Oficina Fase 4 - notificar falhas"
  muting_rules_handling = "NOTIFY_ALL_ISSUES"

  issues_filter {
    name = "politica-oficina"
    type = "FILTER"

    predicate {
      attribute = "labels.policyIds"
      operator  = "EXACTLY_MATCHES"
      values    = [newrelic_alert_policy.oficina.id]
    }
  }

  destination {
    channel_id = newrelic_notification_channel.email.id
  }
}
