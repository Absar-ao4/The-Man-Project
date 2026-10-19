#!/usr/bin/env bash
# Proves a backup can be restored: loads the newest dump (or the one you name) into a throwaway database, prints a few
# counts, and drops the database again. Nothing of the real database is touched.
#
#   ./restore_drill.sh                          # the newest dump in BACKUP_DIR
#   ./restore_drill.sh path/to/manproject_20260101T023000Z.dump
#
# Needs the PostgreSQL client tools (createdb, dropdb, pg_restore, psql) and a user who may create databases.
set -euo pipefail

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

BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/backups}"
DUMP="${1:-}"
if [[ -z "$DUMP" ]]; then
  DUMP="$(ls -1t "$BACKUP_DIR"/manproject_*.dump 2> /dev/null | head -n1 || true)"
fi
if [[ -z "$DUMP" || ! -f "$DUMP" ]]; then
  echo "no dump to restore (looked in $BACKUP_DIR)" >&2
  exit 2
fi

url="${DB_URL#jdbc:postgresql://}"
hostport="${url%%/*}"
host="${hostport%%:*}"
port="5432"
if [[ "$hostport" == *:* ]]; then
  port="${hostport##*:}"
fi

scratch="manproject_drill_$$"
export PGPASSWORD="$DB_PASSWORD"
common=(--host "$host" --port "$port" --username "$DB_USER")

cleanup() {
  dropdb "${common[@]}" --if-exists "$scratch" > /dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Restoring $DUMP into scratch database $scratch"
createdb "${common[@]}" "$scratch"
pg_restore "${common[@]}" --dbname "$scratch" --no-owner --no-privileges --exit-on-error "$DUMP"

query() { psql "${common[@]}" --dbname "$scratch" --tuples-only --no-align --command "$1"; }
echo "users:              $(query 'select count(*) from users')"
echo "workout sessions:   $(query 'select count(*) from workout_sessions')"
echo "meal items:         $(query 'select count(*) from meal_items')"
echo "migrations applied: $(query 'select count(*) from flyway_schema_history where success')"
echo "restore drill passed, dropping $scratch"