#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ENV_FILE:-${PROJECT_ROOT}/.env.prod}"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

if [[ -n "${R2_ACCESS_KEY:-}" && -z "${AWS_ACCESS_KEY_ID:-}" ]]; then
  export AWS_ACCESS_KEY_ID="${R2_ACCESS_KEY}"
fi

if [[ -n "${R2_SECRET_KEY:-}" && -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
  export AWS_SECRET_ACCESS_KEY="${R2_SECRET_KEY}"
fi

required_vars=(
  DB_USER
  R2_ENDPOINT
  R2_BACKUP_BUCKET
  AWS_ACCESS_KEY_ID
  AWS_SECRET_ACCESS_KEY
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required env var: ${var}" >&2
    exit 1
  fi
done

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-echo-db}"
DB_NAME="${DB_NAME:-echo_prod}"
BACKUP_PREFIX="${BACKUP_PREFIX:-daily}"
TMP_DIR="${TMP_DIR:-/tmp}"
TIMESTAMP="$(date -u +%Y%m%d_%H%M%S)"
BACKUP_FILE="${TMP_DIR}/echo_${TIMESTAMP}.sql.gz"
OBJECT_KEY="${BACKUP_PREFIX}/$(basename "${BACKUP_FILE}")"

echo "Creating PostgreSQL backup from ${DB_CONTAINER_NAME}/${DB_NAME}..."
docker exec "${DB_CONTAINER_NAME}" pg_dump -U "${DB_USER}" "${DB_NAME}" | gzip > "${BACKUP_FILE}"

echo "Uploading backup to s3://${R2_BACKUP_BUCKET}/${OBJECT_KEY}..."
aws s3 cp "${BACKUP_FILE}" "s3://${R2_BACKUP_BUCKET}/${OBJECT_KEY}" \
  --endpoint-url "${R2_ENDPOINT}"

rm -f "${BACKUP_FILE}"
echo "Backup completed successfully at $(date -u +%Y-%m-%dT%H:%M:%SZ)"
