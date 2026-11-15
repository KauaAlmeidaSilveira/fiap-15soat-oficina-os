terraform {
  required_version = ">= 1.5"

  required_providers {
    newrelic = {
      source  = "newrelic/newrelic"
      version = "~> 3.0"
    }
  }

  # State local (gitignorado): dashboards e alertas não dependem da AWS, que é destruída a cada uso.
  # Apagar o terraform.tfstate faz o próximo apply tentar recriar o que já existe, e o New Relic
  # recusa nomes duplicados.
}

provider "newrelic" {
  account_id = var.newrelic_account_id
  api_key    = var.newrelic_api_key
  region     = var.newrelic_region
}
