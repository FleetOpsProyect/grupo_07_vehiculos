#!/bin/bash
# Se ejecuta cuando LocalStack está listo (desarrollo local). Crea lo mismo que se pide en AWS:
#   - topic fleetops_vehicle_events (donde publica Vehículos)
#   - cola vehicles_queue con su DLQ vehicles_queue_dlq (3 intentos y pasa a la DLQ)
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"

awslocal sns create-topic --name fleetops_vehicle_events --region "$REGION" > /dev/null

awslocal sqs create-queue --queue-name vehicles_queue_dlq --region "$REGION" > /dev/null
DLQ_ARN="$(awslocal sqs get-queue-attributes --region "$REGION" \
  --queue-url "http://sqs.${REGION}.localhost.localstack.cloud:4566/000000000000/vehicles_queue_dlq" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"

awslocal sqs create-queue --queue-name vehicles_queue --region "$REGION" \
  --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" > /dev/null

echo "LocalStack listo: topic fleetops_vehicle_events, colas vehicles_queue y vehicles_queue_dlq"
