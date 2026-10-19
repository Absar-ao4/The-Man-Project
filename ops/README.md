# Operations

| File | Purpose |
|---|---|
| `backup.sh` | `pg_dump` (custom format) of the database in `DB_URL`, verified, with 14-day retention |
| `restore.sh` | `pg_restore --clean` of a dump into the database in `DB_URL`, with a confirmation prompt |
| `restore_drill.sh` | Restores the newest dump into a throwaway database, prints a few counts and drops it: proof that a backup works |
| `test_backup.sh` | Checks `backup.sh` without a database (stand-ins for `pg_dump` and `pg_restore`): complete dumps only, retention, password handling |
| `docker-compose.backup.yml` | A nightly backup service to run next to the main stack |
| `seed/generate_workout_seed.py` | Regenerates the exercise and plan seed migration |

## Backups

Requires the PostgreSQL client tools (`pg_dump`, `pg_restore`) on the machine that runs it, or use the compose service below.

```bash
./ops/backup.sh                       # reads DB_URL, DB_USER, DB_PASSWORD from the environment or ../.env
BACKUP_DIR=/var/backups/man RETENTION_DAYS=14 ./ops/backup.sh
```

### Nightly, with Docker

```bash
docker compose -f docker-compose.yml -f ops/docker-compose.backup.yml up -d
```

This adds a small service that runs `backup.sh` at 02:30 UTC and writes to `ops/backups` (14 days kept, set
`BACKUP_RETENTION_DAYS` in `.env` to change it). Its log is `docker compose logs backup`.

### Nightly, with cron

```
30 2 * * * /opt/man-project/ops/backup.sh >> /var/log/manproject-backup.log 2>&1
```

Dumps are named `manproject_<UTC timestamp>.dump`. A dump is written to a `.partial` file, checked with
`pg_restore --list`, and only then renamed, so a failed run never leaves something that looks valid. Old
dumps are removed only after a new one succeeded, so a broken night never costs the last good backup.
**Copy the backup directory off the machine** (object storage, another host): a backup next to the database
does not survive losing the disk.

## Restore

```bash
./ops/restore.sh ops/backups/manproject_20260101T023000Z.dump
```

Stop the backend first. The script asks you to type the database name before it overwrites anything
(set `ASSUME_YES=1` for unattended drills).

### Practise it

```bash
./ops/restore_drill.sh                # newest dump into a scratch database, then drops it
bash ./ops/test_backup.sh             # the script's own checks, no database needed
```

Run the drill every month and after changing anything about the database: a backup you have never restored is
a guess. Flyway runs the migrations on start, so a restored database from an older version is brought up to date
the first time the backend starts against it.

## What is not covered

- The database is the only state. The backend keeps nothing else on disk, and photos of meals are never stored.
- Users can export their own data from the app (`GET /api/me/export`) and rebuild a device from the server with
  "Restore from cloud". Neither replaces a database backup.
- Secrets (`.env`) are not in the backup. Keep them in a password manager or a secrets store; losing `JWT_SECRET`
  only signs everyone out, but the API keys have to be created again.