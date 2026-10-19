#!/usr/bin/env bash
# Nightly PostgreSQL backup with 14-day retention.
#
#   DB_URL, DB_USER and DB_PASSWORD are read from the environment (or ../.env if present).
#   BACKUP_DIR defaults to ./backups next to this script; RETENTION_DAYS defaults to 14.
#
# Example cron entry (every night at 02:30):
#   30 2 * * * /path/to/ops/backup.sh >> /var/log/manproject-backup.log 2>&1
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/../.env}"
if [[ -f "$ENV_FILE" ]]; then
  # Only import the three database variables; never source the whole file (it holds other secrets).
  for key in DB_URL DB_USER DB_PASSWORD; do
    if [[ -z "${!key:-}" ]]; then
      value="$(grep -E "^${key}=" "$ENV_FILE" | head -n1 | cut -d= -f2- || true)"
      [[ -n "$value" ]] && export "$key=$value"
    fi
  done
fi

: "${DB_URL:?DB_URL is not set (jdbc:postgresql://host:port/database)}"
: "${DB_USER:?DB_USER is not set}"
: "${DB_PASSWORD:?DB_PASSWORD is not set}"

BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

# jdbc:postgresql://host:5432/name?params  ->  host, port, name
url="${DB_URL#jdbc:postgresql://}"
hostport="${url%%/*}"
dbname="${url#*/}"
dbname="${dbname%%\?*}"
host="${hostport%%:*}"
port="5432"
if [[ "$hostport" == *:* ]]; then
  port="${hostport##*:}"
fi

mkdir -p "$BACKUP_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_DIR/manproject_${stamp}.dump"
partial="$target.partial"

# Custom format is compressed and lets pg_restore pick what to restore. The password goes through
# the environment so it never appears in the process list.
PGPASSWORD="$DB_PASSWORD" pg_dump \
  --host "$host" --port "$port" --username "$DB_USER" --dbname "$dbname" \
  --format custom --no-owner --no-privileges \
  --file "$partial"

# A dump only counts once it has been fully written and can be listed.
pg_restore --list "$partial" > /dev/null
mv "$partial" "$target"
echo "backup written: $target ($(du -h "$target" | cut -f1))"

# Retention: remove dumps older than RETENTION_DAYS days (leftover .partial files too).
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'manproject_*.dump' -o -name 'manproject_*.dump.partial' \) \
  -mtime "+${RETENTION_DAYS}" -print -delete
