#!/bin/sh
# Cria, uma unica vez, o que fica ligado entre as subidas: bucket do state do Terraform, tabela de
# lock e um AWS Budget com alerta por e-mail. Idempotente.
#
# Uso: AWS_PROFILE=personal-tf EMAIL_ALERTA=voce@exemplo.com sh scripts/aws-bootstrap.sh
set -eu

REGIAO=us-east-1
BUCKET=fiap-15soat-oficina-fase4-tfstate
TABELA=fiap-15soat-oficina-tflock
LIMITE_USD=${LIMITE_USD:-20}

CONTA=$(aws sts get-caller-identity --query Account --output text)
echo "Conta AWS: $CONTA (profile ${AWS_PROFILE:-default})"

if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Bucket $BUCKET ja existe"
else
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGIAO" >/dev/null \
    || { echo "Nao foi possivel criar $BUCKET (nome de bucket e global: pode estar em outra conta)" >&2; exit 1; }
  aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption --bucket "$BUCKET" \
    --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-public-access-block --bucket "$BUCKET" \
    --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  echo "Bucket $BUCKET criado"
fi

if aws dynamodb describe-table --table-name "$TABELA" --region "$REGIAO" >/dev/null 2>&1; then
  echo "Tabela $TABELA ja existe"
else
  aws dynamodb create-table --table-name "$TABELA" --region "$REGIAO" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST >/dev/null
  aws dynamodb wait table-exists --table-name "$TABELA" --region "$REGIAO"
  echo "Tabela $TABELA criada"
fi

if [ -z "${EMAIL_ALERTA:-}" ]; then
  echo "EMAIL_ALERTA vazio: budget nao criado"
elif aws budgets describe-budget --account-id "$CONTA" --budget-name oficina-fase4 >/dev/null 2>&1; then
  echo "Budget oficina-fase4 ja existe"
else
  notif() {
    printf '{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":%s,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"%s"}]}' "$1" "$EMAIL_ALERTA"
  }
  aws budgets create-budget --account-id "$CONTA" \
    --budget "{\"BudgetName\":\"oficina-fase4\",\"BudgetLimit\":{\"Amount\":\"$LIMITE_USD\",\"Unit\":\"USD\"},\"TimeUnit\":\"MONTHLY\",\"BudgetType\":\"COST\"}" \
    --notifications-with-subscribers "[$(notif 50),$(notif 80),$(notif 100)]"
  echo "Budget oficina-fase4 criado: US\$ $LIMITE_USD/mes, alertas em 50%, 80% e 100%"
fi
