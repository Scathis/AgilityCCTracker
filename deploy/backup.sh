#!/usr/bin/env bash
# Nightly MySQL backup for AgilityCCTracker - BUILD_PLAN.md §6/Phase 6 step 7.
# mysqldump via the running mysql container, gzip, keep 14 days.
#
# Usage: run from anywhere - paths below are absolute. Intended for a cron
# entry (see docs/BUILD_LOG.md's Phase 6 section for the exact line used).

set -euo pipefail

REPO_DIR="/mnt/120ssd/agilitycctracker"
SERVER_DIR="${REPO_DIR}/server"
BACKUP_DIR="${SERVER_DIR}/backups"
RETENTION_DAYS=14
TIMESTAMP="$(date +%Y-%m-%d_%H%M%S)"
OUTFILE="${BACKUP_DIR}/agilitycc-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

# .env holds MYSQL_ROOT_PASSWORD/DB_NAME - never echoed, only used inline.
DB_NAME="$(grep -m1 '^DB_NAME=' "${SERVER_DIR}/.env" | cut -d= -f2-)"
MYSQL_ROOT_PASSWORD="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "${SERVER_DIR}/.env" | cut -d= -f2-)"

cd "${SERVER_DIR}"
docker compose -p agilitycctracker exec -T mysql \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" "${DB_NAME}" \
  | gzip > "${OUTFILE}"

# Prune backups older than the retention window.
find "${BACKUP_DIR}" -name 'agilitycc-*.sql.gz' -mtime "+${RETENTION_DAYS}" -delete

echo "Backup written: ${OUTFILE} ($(du -h "${OUTFILE}" | cut -f1))"
