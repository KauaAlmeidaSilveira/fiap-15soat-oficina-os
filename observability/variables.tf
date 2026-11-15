variable "newrelic_account_id" {
  description = "Account ID do New Relic (identificador, não é segredo). Precisa ser a conta da license key do .env."
  type        = number
}

variable "newrelic_api_key" {
  description = "User API key do New Relic (começa com NRAK-). Nunca commitar."
  type        = string
  sensitive   = true
}

variable "newrelic_region" {
  description = "Região da conta New Relic: US ou EU."
  type        = string
  default     = "US"

  validation {
    condition     = contains(["US", "EU"], var.newrelic_region)
    error_message = "newrelic_region precisa ser US ou EU."
  }
}

variable "alertas_email" {
  description = "E-mail que recebe as notificações de alerta"
  type        = string
}

# Contrato com NEW_RELIC_APP_NAME do k8s/configmap.yaml de cada serviço. Se divergir, painéis e
# alertas casam com nada, sem erro.
variable "apps" {
  description = "appName de cada serviço no APM"
  type        = map(string)
  default = {
    os       = "oficina-os"
    execucao = "oficina-execucao"
    billing  = "oficina-billing"
  }
}

# Com o ambiente destruído, os três serviços ficam sem dados e o alerta dispararia sempre. Ligar só
# enquanto o ambiente estiver no ar (ex.: na gravação): terraform apply -var alertas_sem_dados=true.
variable "alertas_sem_dados" {
  description = "Liga o alerta de serviço sem reportar dados"
  type        = bool
  default     = false
}
