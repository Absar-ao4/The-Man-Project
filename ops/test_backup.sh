#!/usr/bin/env bash
# Checks backup.sh without a database: pg_dump and pg_restore are replaced by small stand-ins on the PATH.
# It proves the parts that matter when something goes wrong: a dump only counts once it is complete and readable, the
# password never appears in the arguments, the connection details are read from DB_URL, and old dumps are removed only after
# a new one succeeded. Needs bash and GNU coreutils (Linux, or Git Bash on Windows).
#
#   ./ops/test_backup.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP="$SCRIPT_DIR/backup.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# Stand-in for pg_dump: records how it was called and writes a small file where --file says.
cat > "$WORK/bin/pg_dump" <<'SH'
#!/usr/bin/env bash
echo "pg_dump $*" >> "$FAKE_LOG"
echo "PGPASSWORD=${PGPASSWORD:-unset}" >> "$FAKE_LOG"
if [[ "${FAKE_DUMP_FAIL:-}" == "1" ]]; then
  echo "connection refused" >&2
  exit 1
fi
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--file" ]]; then
    echo "fake dump of the database" > "$2"
    [[ "${FAKE_DUMP_CORRUPT:-}" == "1" ]] && echo "corrupt" > "$2"
  fi
  shift
done
SH
# Stand-in for pg_restore --list: only a file that says it is a dump can be listed.
cat > "$WORK/bin/pg_restore" <<'SH'
#!/usr/bin/env bash
file="${@: -1}"
grep -q "fake dump" "$file"
SH
chmod +x "$WORK/bin/pg_dump" "$WORK/bin/pg_restore"

export PATH="$WORK/bin:$PATH"
export FAKE_LOG="$WORK/calls.log"
export ENV_FILE=/dev/null
export DB_URL="jdbc:postgresql://db.example:6543/manproject?sslmode=require"
export DB_USER="backup_user"
export DB_PASSWORD="s3cret-pass"

failures=0
pass() { echo "  ok   $1"; }
fail() { echo "  FAIL $1"; failures=$((failures + 1)); }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

fresh() {
  rm -rf "$WORK/b" "$FAKE_LOG"
  mkdir -p "$WORK/b"
  export BACKUP_DIR="$WORK/b"
  unset FAKE_DUMP_FAIL FAKE_DUMP_CORRUPT
}
dumps() { find "$WORK/b" -maxdepth 1 -name 'manproject_*.dump' | wc -l | tr -d ' '; }
partials() { find "$WORK/b" -maxdepth 1 -name '*.partial' | wc -l | tr -d ' '; }

echo "a good run"
fresh
"$BACKUP" > "$WORK/out.txt" 2>&1
check "exits with success" "[[ $? -eq 0 ]]"
check "leaves exactly one finished dump" "[[ \$(dumps) -eq 1 ]]"
check "leaves no partial file" "[[ \$(partials) -eq 0 ]]"
check "names it manproject_<UTC time>.dump" "ls \"$WORK/b\" | grep -Eq '^manproject_[0-9]{8}T[0-9]{6}Z\.dump$'"

echo "the connection details"
check "host, port, database and user come from DB_URL" "grep -q -- '--host db.example --port 6543 --username backup_user --dbname manproject' \"$FAKE_LOG\""
check "the ?sslmode part is not treated as part of the name" "! grep -q 'dbname manproject?' \"$FAKE_LOG\""
check "the password goes in the environment" "grep -q 'PGPASSWORD=s3cret-pass' \"$FAKE_LOG\""
check "the password is never an argument" "! grep '^pg_dump' \"$FAKE_LOG\" | grep -q 's3cret-pass'"
check "it uses the compressed custom format" "grep -q -- '--format custom' \"$FAKE_LOG\""

echo "a URL without a port"
fresh
DB_URL="jdbc:postgresql://localhost/manproject" "$BACKUP" > /dev/null 2>&1
check "the standard port 5432 is used" "grep -q -- '--host localhost --port 5432' \"$FAKE_LOG\""
export DB_URL="jdbc:postgresql://db.example:6543/manproject?sslmode=require"

echo "retention"
fresh
touch -d "20 days ago" "$WORK/b/manproject_20200101T000000Z.dump"
touch -d "15 days ago" "$WORK/b/manproject_20200102T000000Z.dump.partial"
touch -d "13 days ago" "$WORK/b/manproject_20200103T000000Z.dump"
touch -d "20 days ago" "$WORK/b/somebody_elses_file.txt"
"$BACKUP" > /dev/null 2>&1
check "a dump older than 14 days is removed" "[[ ! -e \"$WORK/b/manproject_20200101T000000Z.dump\" ]]"
check "an old unfinished file is removed too" "[[ ! -e \"$WORK/b/manproject_20200102T000000Z.dump.partial\" ]]"
check "a dump from 13 days ago is kept" "[[ -e \"$WORK/b/manproject_20200103T000000Z.dump\" ]]"
check "files that are not ours are never touched" "[[ -e \"$WORK/b/somebody_elses_file.txt\" ]]"
check "the new dump is there" "[[ \$(dumps) -eq 2 ]]"

echo "the retention period can be changed"
fresh
touch -d "5 days ago" "$WORK/b/manproject_20200104T000000Z.dump"
RETENTION_DAYS=3 "$BACKUP" > /dev/null 2>&1
check "RETENTION_DAYS=3 removes a 5 day old dump" "[[ ! -e \"$WORK/b/manproject_20200104T000000Z.dump\" ]]"

echo "pg_dump fails"
fresh
touch -d "20 days ago" "$WORK/b/manproject_20200105T000000Z.dump"
FAKE_DUMP_FAIL=1 "$BACKUP" > /dev/null 2>&1
check "the script reports failure" "[[ $? -ne 0 ]]"
check "no new dump appears" "[[ \$(dumps) -eq 1 ]]"
check "old dumps are kept, so a broken night never costs the last good backup" "[[ -e \"$WORK/b/manproject_20200105T000000Z.dump\" ]]"

echo "pg_dump writes something unreadable"
fresh
FAKE_DUMP_CORRUPT=1 "$BACKUP" > /dev/null 2>&1
check "the script reports failure" "[[ $? -ne 0 ]]"
check "it never becomes a finished dump" "[[ \$(dumps) -eq 0 ]]"

echo "missing settings"
fresh
env -u DB_PASSWORD "$BACKUP" > /dev/null 2>&1
check "a missing DB_PASSWORD stops it before anything is written" "[[ $? -ne 0 && \$(dumps) -eq 0 ]]"

echo
if [[ $failures -eq 0 ]]; then
  echo "all backup checks passed"
else
  echo "$failures check(s) failed"
  exit 1
fi