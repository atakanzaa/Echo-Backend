#!/usr/bin/env bash
# Canonical prod compose wrapper. Always uses the base + prod override files
# and the prod env file, so the app lands on the right network with the right
# config. Use this instead of bare `docker compose` on the server.
#
# Examples:
#   ./deploy.sh up -d --build      # build + (re)deploy the whole stack
#   ./deploy.sh ps                 # status
#   ./deploy.sh logs -f app        # tail app logs
#   ./deploy.sh restart caddy      # restart a single service
set -euo pipefail

cd "$(dirname "$0")"

exec docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  --env-file .env.prod \
  "$@"
