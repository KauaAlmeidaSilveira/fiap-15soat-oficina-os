#!/bin/sh
# Sobe o ambiente inteiro na conta AWS, na ordem dos contratos entre repositorios:
# infra-k8s -> infra-db -> databases -> servicos -> lambda-auth -> servicos de novo (emissor = Gateway).
# Leva cerca de 30 minutos (o EKS sozinho passa de 10). Destruir com scripts/destruir-aws.sh.
#
# Uso: AWS_PROFILE=personal-tf sh scripts/subir-aws.sh
# Variaveis opcionais: SEM_TESTES=1 (repassada ao deploy), NEW_RELIC_LICENSE_KEY no .env (instala o nri-bundle),
# SEM_NRI_BUNDLE=1 (nao instala o nri-bundle: com um node de 1 vCPU ele nao cabe; o APM dos servicos continua).
set -eu

OS_DIR=$(cd "$(dirname "$0")/.." && pwd)
RAIZ=$(dirname "$OS_DIR")
CHAVES="$OS_DIR/.local/chaves"

CONTA=$(aws sts get-caller-identity --query Account --output text)
# O Terraform nao le sessoes do `aws login`: usar um profile com credential_process (ver CLAUDE.md).
if [ -n "$(aws configure get login_session 2>/dev/null)" ]; then
  echo "ERRO: profile ${AWS_PROFILE:-default} usa aws login, que o Terraform nao le. Use AWS_PROFILE=personal-tf." >&2; exit 1
fi
printf 'Conta AWS: %s (profile %s). Continuar? [s/N] ' "$CONTA" "${AWS_PROFILE:-default}"
read -r resposta
[ "$resposta" = s ] || exit 1

[ -f "$CHAVES/sessao.private.key" ] || sh "$OS_DIR/scripts/gerar-chaves-locais.sh"
if [ -f "$OS_DIR/.env" ]; then set -a; . "$OS_DIR/.env"; set +a; fi

aplicar() { # pasta, depois argumentos extras
  dir=$1; shift
  echo "=== terraform apply: $(basename "$(dirname "$dir")")/$(basename "$dir")"
  terraform -chdir="$dir" init -input=false >/dev/null
  terraform -chdir="$dir" apply -input=false -auto-approve "$@"
}

aplicar "$RAIZ/fiap-15soat-oficina-infra-k8s"

TFVARS_DB="$RAIZ/fiap-15soat-oficina-infra-db/terraform.tfvars"
if ! grep -q '^db_password' "$TFVARS_DB" 2>/dev/null; then
  echo "db_password = \"$(openssl rand -hex 16)\"" >> "$TFVARS_DB"
  echo "Senha do usuario master da RDS gerada em $TFVARS_DB (gitignorado)"
fi
aplicar "$RAIZ/fiap-15soat-oficina-infra-db"

aws eks update-kubeconfig --region us-east-1 \
  --name "$(terraform -chdir="$RAIZ/fiap-15soat-oficina-infra-k8s" output -raw eks_cluster_name)" >/dev/null
sh "$RAIZ/fiap-15soat-oficina-infra-db/scripts/criar-databases.sh"

if [ -n "${NEW_RELIC_LICENSE_KEY:-}" ] && [ -z "${SEM_NRI_BUNDLE:-}" ]; then
  echo "=== New Relic (nri-bundle)"
  helm repo add newrelic https://helm-charts.newrelic.com >/dev/null 2>&1 || true
  helm repo update newrelic >/dev/null
  helm upgrade --install newrelic-bundle newrelic/nri-bundle --namespace newrelic --create-namespace \
    --set global.licenseKey="$NEW_RELIC_LICENSE_KEY" --set global.cluster=oficina-eks \
    -f "$OS_DIR/k8s/newrelic-values.yaml" >/dev/null
fi

sh "$OS_DIR/scripts/deploy-aws.sh"

(cd "$RAIZ/fiap-15soat-oficina-lambda-auth" && npm ci --silent && npm run --silent empacotar)
TF_VAR_jwt_private_key=$(cat "$CHAVES/sessao.private.key") aplicar "$RAIZ/fiap-15soat-oficina-lambda-auth/terraform"

# Segunda passada: agora o emissor dos tokens e a base do link de aprovacao sao a URL do Gateway.
SEM_TESTES=1 sh "$OS_DIR/scripts/deploy-aws.sh"

URL=$(terraform -chdir="$RAIZ/fiap-15soat-oficina-lambda-auth/terraform" output -raw api_gateway_url)
echo
echo "Ambiente no ar: $URL"
echo "Webhook do Mercado Pago (cadastrar no painel): $URL/webhooks/mercado-pago"
echo "Ao terminar: AWS_PROFILE=${AWS_PROFILE:-default} sh scripts/destruir-aws.sh"
