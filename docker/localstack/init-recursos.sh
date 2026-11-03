#!/bin/sh
# Mesmos recursos do Terraform de infra-k8s (filas) e infra-db (tabela).
set -e

for fila in oficina-os-saga-eventos oficina-billing-comandos oficina-execucao-comandos; do
    awslocal sqs create-queue --queue-name "$fila-dlq" >/dev/null
    dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "http://localhost:4566/000000000000/$fila-dlq" \
        --attribute-names QueueArn --query Attributes.QueueArn --output text)
    awslocal sqs create-queue --queue-name "$fila" --attributes \
        "{\"VisibilityTimeout\":\"60\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" >/dev/null
done

awslocal dynamodb create-table --table-name oficina-billing \
    --billing-mode PAY_PER_REQUEST \
    --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
        AttributeName=outboxPendente,AttributeType=S AttributeName=criadoEm,AttributeType=S \
    --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
    --global-secondary-indexes 'IndexName=outbox-pendente,KeySchema=[{AttributeName=outboxPendente,KeyType=HASH},{AttributeName=criadoEm,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    >/dev/null

touch /tmp/recursos-prontos
