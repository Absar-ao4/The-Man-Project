#!/usr/bin/env bash
# Restore a backup made by backup.sh into the database named by DB_URL.
#
#   ./restore.sh path/to/manproject_20260101T023000Z.dump
#
# This REPLACES the contents of the target database (pg_restore --clean --if-exists). Stop the backend
# first, and point DB_URL at a scratch database if you only want to inspect a backup.
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "usage: $0 <dump file>" >&2
  exit 2
fi
DUMP="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/../.env}"
if [[ -f "$ENV_FILE" ]]; then
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

url="${DB_URL#jdbc:postgresql://}"
hostport="${url%%/*}"
dbname="${url#*/}"
dbname="${dbname%%\?*}"
host="${hostport%%:*}"
port="5432"
if [[ "$hostport" == *:* ]]; then
  port="${hostport##*:}"
fi

echo "Restoring $DUMP into database '$dbname' on $host:$port as $DB_USER"
if [[ "${ASSUME_YES:-}" != "1" ]]; then
  read -r -p "This overwrites the database. Type the database name to continue: " answer
  if [[ "$answer" != "$dbname" ]]; then
    echo "aborted"
    exit 1
  fi
fi

PGPASSWORD="$DB_PASSWORD" pg_restore \
  --host "$host" --port "$port" --username "$DB_USER" --dbname "$dbname" \
  --clean --if-exists --no-owner --no-privileges --exit-on-error \
  "$DUMP"
echo "restore finished"
