#!/bin/sh
# Destroi o ambiente na ordem inversa da subida. Mantem so o que scripts/aws-bootstrap.sh cria
# (bucket do state, tabela de lock e budget).
#
# Uso: AWS_PROFILE=personal-tf sh scripts/destruir-aws.sh
set -eu

OS_DIR=$(cd "$(dirname "$0")/.." && pwd)
RAIZ=$(dirname "$OS_DIR")
CHAVES="$OS_DIR/.local/chaves"

CONTA=$(aws sts get-caller-identity --query Account --output text)
# O Terraform nao le sessoes do `aws login`: usar um profile com credential_process (ver CLAUDE.md).
if [ -n "$(aws configure get login_session 2>/dev/null)" ]; then
  echo "ERRO: profile ${AWS_PROFILE:-default} usa aws login, que o Terraform nao le. Use AWS_PROFILE=personal-tf." >&2; exit 1
fi
printf 'DESTRUIR o ambiente na conta %s (profile %s)? Digite destruir: ' "$CONTA" "${AWS_PROFILE:-default}"
read -r resposta
[ "$resposta" = destruir ] || exit 1

destruir() { # pasta, depois argumentos extras
  dir=$1; shift
  echo "=== terraform destroy: $dir"
  terraform -chdir="$dir" init -input=false >/dev/null
  terraform -chdir="$dir" destroy -input=false -auto-approve "$@"
}

# 1. Gateway e Lambda: dependem dos NLBs dos servicos.
LAMBDA="$RAIZ/fiap-15soat-oficina-lambda-auth"
[ -f "$LAMBDA/dist/lambda.zip" ] || (cd "$LAMBDA" && npm ci --silent && npm run --silent empacotar)
TF_VAR_jwt_private_key=$(cat "$CHAVES/sessao.private.key" 2>/dev/null || echo destruir) destruir "$LAMBDA/terraform"

# 2. Services LoadBalancer: os NLBs sao do Kubernetes. Se o cluster sair antes, ficam orfaos,
#    continuam cobrando e travam o destroy da VPC.
CLUSTER=$(terraform -chdir="$RAIZ/fiap-15soat-oficina-infra-k8s" output -raw eks_cluster_name 2>/dev/null || true)
VPC=$(terraform -chdir="$RAIZ/fiap-15soat-oficina-infra-k8s" output -raw vpc_id 2>/dev/null || true)
if [ -n "$CLUSTER" ] && aws eks describe-cluster --name "$CLUSTER" >/dev/null 2>&1; then
  aws eks update-kubeconfig --region us-east-1 --name "$CLUSTER" >/dev/null
  helm uninstall newrelic-bundle -n newrelic >/dev/null 2>&1 || true
  kubectl delete namespace oficina --wait=true --timeout=10m || true
  echo "Esperando os NLBs sairem..."
  for _ in $(seq 1 30); do
    restantes=$(aws elbv2 describe-load-balancers --query "length(LoadBalancers[?VpcId=='$VPC'])" --output text 2>/dev/null || echo 0)
    [ "$restantes" = 0 ] && break
    sleep 10
  done
fi

# 3. Bancos (a RDS ocupa a VPC) e 4. rede, cluster, ECR e filas.
destruir "$RAIZ/fiap-15soat-oficina-infra-db"
destruir "$RAIZ/fiap-15soat-oficina-infra-k8s"

echo
echo "Conferindo sobras que cobram por hora:"
aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerName' --output text
aws ec2 describe-nat-gateways --filter Name=state,Values=available --query 'NatGateways[].NatGatewayId' --output text
aws eks list-clusters --query 'clusters' --output text
aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier' --output text
echo "(linhas vazias acima = nada sobrando)"
