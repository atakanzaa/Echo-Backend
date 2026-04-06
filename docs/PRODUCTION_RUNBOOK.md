# Echo Production Runbook

This runbook covers deployment, smoke checks, backup, restore, and first-response incident flow for the single-VPS production setup.

## 1. Prerequisites

- Ubuntu 22.04 VPS with Docker and Docker Compose plugin installed.
- Firewall open only on TCP 22, 80, 443.
- DNS record `api.echojournal.net` pointing to the VPS.
- Cloudflare R2 private bucket for backups (example: `echo-backups`).
- R2 lifecycle policy configured to delete backups older than 30 days.

## 2. Production Environment File

```bash
cp .env.prod.example .env.prod
```

Populate `.env.prod` with real values:

- `DB_USER`, `DB_PASS`, `JWT_SECRET`
- `AI_PROVIDER` and matching API key(s)
- `DOMAIN`, `ACME_EMAIL`
- `R2_ENDPOINT`, `R2_BACKUP_BUCKET`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`

## 3. Deploy Command

```bash
docker compose --env-file .env.prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d --build
```

Check service status:

```bash
docker compose --env-file .env.prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  ps
```

## 4. Post-Deploy Smoke Checks

Health endpoints:

```bash
curl -fsS https://api.echojournal.net/api/v1/health
curl -fsS https://api.echojournal.net/actuator/health/liveness
curl -fsS https://api.echojournal.net/actuator/health/readiness
```

Security headers:

```bash
curl -I https://api.echojournal.net | grep -E "Strict-Transport-Security|X-Content-Type-Options|X-Frame-Options|Referrer-Policy"
```

Rate limit check (`429` expected after threshold):

```bash
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST \
    https://api.echojournal.net/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"wrong@test.com","password":"wrong"}'
done
```

## 5. Backup Operations

Manual run:

```bash
ENV_FILE=/opt/echo/.env.prod /opt/echo/scripts/backup.sh
```

Cron (daily at 03:00 UTC):

```cron
0 3 * * * ENV_FILE=/opt/echo/.env.prod /opt/echo/scripts/backup.sh >> /var/log/echo-backup.log 2>&1
```

Validate backup objects:

```bash
aws s3 ls s3://echo-backups/daily/ --endpoint-url "$R2_ENDPOINT"
```

## 6. Restore Drill (Monthly)

1. Download a backup file from R2.
2. Restore into a temporary database first:

```bash
gunzip < echo_YYYYMMDD_HHMMSS.sql.gz | docker exec -i echo-db psql -U "$DB_USER" echo_restore_test
```

3. Verify critical tables and row counts.
4. Record drill date and result in ops notes.

## 7. Incident First Response

1. Check uptime alert target: `https://api.echojournal.net/api/v1/health`.
2. SSH to VPS and inspect containers:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker logs echo-caddy --tail 100
docker logs echo-backend --tail 100
docker logs echo-db --tail 100
```

3. If app is unhealthy but DB is healthy, restart app only:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart app
```

4. If DB failure is persistent, restore from latest valid backup.
5. After mitigation, capture timeline, root cause, and preventive action.

## 8. Known Deferred Work

- Unit/web test suite stabilization (Mockito inline + local JDK attach constraints).
- Test profile alignment for PostgreSQL-specific schema types.
