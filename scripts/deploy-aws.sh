#!/bin/sh
# Deploy dos 3 servicos no EKS, sem GitHub Actions: testes (mvn verify), imagem, push no ECR,
# Secrets e manifestos de k8s/ de cada repositorio. Roda da pasta com os repositorios lado a lado.
#
# Uso:
#   AWS_PROFILE=personal-tf sh scripts/deploy-aws.sh                 # os 3 servicos, com testes
#   AWS_PROFILE=personal-tf sh scripts/deploy-aws.sh billing         # so um servico
#   AWS_PROFILE=personal-tf SEM_TESTES=1 sh scripts/deploy-aws.sh    # pula o mvn verify
#
# Pre-requisitos: terraform apply de infra-k8s e infra-db, scripts/criar-databases.sh do infra-db,
# chaves em .local/chaves (sh scripts/gerar-chaves-locais.sh) e .env deste repositorio (ver
# .env.example). Depois do apply do lambda-auth, rodar de novo: o emissor dos tokens e a base do
# link de aprovacao passam a ser a URL do API Gateway.
set -eu

OS_DIR=$(cd "$(dirname "$0")/.." && pwd)
RAIZ=$(dirname "$OS_DIR")
CHAVES="$OS_DIR/.local/chaves"
REGIAO=us-east-1
SERVICOS=${*:-"execucao billing os"}   # Execucao antes do OS: o OS consulta produtos nela

falha() { echo "ERRO: $*" >&2; exit 1; }

# --- Conferencias ---------------------------------------------------------------------------
CONTA=$(aws sts get-caller-identity --query Account --output text) || falha "sem sessao AWS (aws login --profile personal)"
# O Terraform nao le sessoes do `aws login`: usar um profile com credential_process (ver CLAUDE.md).
if [ -n "$(aws configure get login_session 2>/dev/null)" ]; then
  echo "ERRO: profile ${AWS_PROFILE:-default} usa aws login, que o Terraform nao le. Use AWS_PROFILE=personal-tf." >&2; exit 1
fi
echo "Conta AWS: $CONTA (profile ${AWS_PROFILE:-default})"
for par in sessao servico aprovacao; do
  [ -f "$CHAVES/$par.public.key" ] || falha "chaves ausentes em $CHAVES: rode sh scripts/gerar-chaves-locais.sh"
done
if [ -f "$OS_DIR/.env" ]; then set -a; . "$OS_DIR/.env"; set +a; fi
# Os servicos compilam com --release 21; o mvn usa o JDK do JAVA_HOME, que pode ser outro.
if [ -z "${SEM_TESTES:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || falha "JDK 21 nao encontrado (mvn verify precisa dele)"
  export JAVA_HOME
fi

tf_saida() { repo=$1; shift; terraform -chdir="$RAIZ/fiap-15soat-oficina-$repo" output "$@"; }

CLUSTER=$(tf_saida infra-k8s -raw eks_cluster_name)
aws eks update-kubeconfig --region "$REGIAO" --name "$CLUSTER" >/dev/null
ECR_JSON=$(tf_saida infra-k8s -json ecr_repositorios)
DB_HOST=$(tf_saida infra-db -raw rds_endpoint)
SENHAS_DB=$(tf_saida infra-db -json senhas_servicos)
API_URL=$(tf_saida lambda-auth/terraform -raw api_gateway_url 2>/dev/null || true)
case "$API_URL" in https://*) ;; *) API_URL="" ;; esac
if [ -z "$API_URL" ]; then
  echo "AVISO: lambda-auth ainda sem apply; emissor provisorio ate o proximo deploy"
  API_URL="http://api-gateway-pendente"
fi
JWT_ISSUER=$API_URL
APP_PUBLIC_BASE_URL=$API_URL
export DB_HOST JWT_ISSUER APP_PUBLIC_BASE_URL

json() { printf '%s' "$1" | python3 -c "import json,sys; print(json.load(sys.stdin)['$2'])"; }
REGISTRO="$CONTA.dkr.ecr.$REGIAO.amazonaws.com"
aws ecr get-login-password --region "$REGIAO" | docker login --username AWS --password-stdin "$REGISTRO" >/dev/null

aplicar_secret() { # nome, depois pares --from-literal/--from-file
  nome=$1; shift
  kubectl create secret generic "$nome" --namespace oficina "$@" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
}

kubectl apply -f "$OS_DIR/k8s/namespace.yaml" >/dev/null

for s in $SERVICOS; do
  DIR="$RAIZ/fiap-15soat-oficina-$s"
  [ -d "$DIR/k8s" ] || falha "servico desconhecido ou sem k8s/: $s"
  echo "=== $s"

  if [ -z "${SEM_TESTES:-}" ]; then
    # Os testes leem chaves de src/main/resources (gitignorado); reaproveita as de .local/chaves.
    RES="$DIR/src/main/resources"
    copiar() { [ -f "$RES/$2" ] || cp "$CHAVES/$1" "$RES/$2"; }
    copiar sessao.public.key jwt.public.key
    case $s in
      os) copiar sessao.private.key jwt.private.key
          copiar servico.private.key servico.private.key
          copiar servico.public.key servico.public.key ;;
      billing) copiar aprovacao.private.key aprovacao.private.key
               copiar aprovacao.public.key aprovacao.public.key ;;
    esac
    echo "--- testes (mvn verify, gate de 80%)"
    (cd "$DIR" && mvn -q verify) || falha "testes de $s falharam; nada foi publicado"
  fi

  TAG=$(git -C "$DIR" rev-parse --short HEAD)
  [ -z "$(git -C "$DIR" status --porcelain)" ] || TAG="$TAG-local$(date +%s)"
  IMAGEM="$(json "$ECR_JSON" "$s"):$TAG"
  echo "--- imagem $IMAGEM"
  docker build --platform linux/arm64 -q -t "$IMAGEM" "$DIR" >/dev/null
  docker push -q "$IMAGEM" >/dev/null

  echo "--- secrets"
  case $s in
    os)
      aplicar_secret oficina-os-secrets \
        --from-literal=DB_PASSWORD="$(json "$SENHAS_DB" os)" \
        --from-literal=NEW_RELIC_LICENSE_KEY="${NEW_RELIC_LICENSE_KEY:-}"
      aplicar_secret oficina-os-chaves \
        --from-file=sessao.public.key="$CHAVES/sessao.public.key" \
        --from-file=servico.private.key="$CHAVES/servico.private.key" \
        --from-file=servico.public.key="$CHAVES/servico.public.key" ;;
    execucao)
      aplicar_secret oficina-execucao-secrets \
        --from-literal=DB_PASSWORD="$(json "$SENHAS_DB" execucao)" \
        --from-literal=NEW_RELIC_LICENSE_KEY="${NEW_RELIC_LICENSE_KEY:-}"
      aplicar_secret oficina-execucao-chaves \
        --from-file=sessao.public.key="$CHAVES/sessao.public.key" \
        --from-file=servico.public.key="$CHAVES/servico.public.key" ;;
    billing)
      [ -n "${MERCADOPAGO_ACCESS_TOKEN:-}" ] || falha "MERCADOPAGO_ACCESS_TOKEN vazio no .env"
      [ -n "${MERCADOPAGO_EXTERNAL_POS_ID:-}" ] || falha "MERCADOPAGO_EXTERNAL_POS_ID vazio no .env"
      aplicar_secret oficina-billing-secrets \
        --from-literal=MERCADOPAGO_ACCESS_TOKEN="$MERCADOPAGO_ACCESS_TOKEN" \
        --from-literal=MERCADOPAGO_EXTERNAL_POS_ID="$MERCADOPAGO_EXTERNAL_POS_ID" \
        --from-literal=MERCADOPAGO_WEBHOOK_SECRET="${MERCADOPAGO_WEBHOOK_SECRET:-}" \
        --from-literal=GMAIL_SMTP_USERNAME="${GMAIL_SMTP_USERNAME:-}" \
        --from-literal=GMAIL_SMTP_PASSWORD="${GMAIL_SMTP_PASSWORD:-}" \
        --from-literal=NEW_RELIC_LICENSE_KEY="${NEW_RELIC_LICENSE_KEY:-}"
      aplicar_secret oficina-billing-chaves \
        --from-file=sessao.public.key="$CHAVES/sessao.public.key" \
        --from-file=aprovacao.private.key="$CHAVES/aprovacao.private.key" \
        --from-file=aprovacao.public.key="$CHAVES/aprovacao.public.key" ;;
  esac

  # Hash da configuracao: muda o template do pod e forca o rollout quando so ConfigMap ou Secret mudam.
  CONFIG_HASH=$( { envsubst '${DB_HOST} ${JWT_ISSUER} ${APP_PUBLIC_BASE_URL}' < "$DIR/k8s/configmap.yaml"
                   kubectl get secret -n oficina "oficina-$s-secrets" "oficina-$s-chaves" -o jsonpath='{.items[*].data}'; } \
                 | shasum | cut -c1-16)
  export IMAGEM CONFIG_HASH

  echo "--- manifestos"
  for f in serviceaccount configmap deployment service hpa; do
    envsubst '${IMAGEM} ${DB_HOST} ${JWT_ISSUER} ${APP_PUBLIC_BASE_URL} ${CONFIG_HASH}' < "$DIR/k8s/$f.yaml" | kubectl apply -f - >/dev/null
  done
  kubectl rollout status -n oficina "deployment/oficina-$s" --timeout=10m
done

echo
kubectl get pods,svc -n oficina
echo "Emissor e base publica: $API_URL"
