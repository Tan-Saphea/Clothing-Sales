#!/usr/bin/env bash
set -euo pipefail

: "${ORACLE_DB_USER:?ORACLE_DB_USER is required}"
: "${ORACLE_DB_PASSWORD:?ORACLE_DB_PASSWORD is required}"
: "${ORACLE_DB_SERVICE:?ORACLE_DB_SERVICE is required}"
: "${ORACLE_RESTORE_DUMP:?ORACLE_RESTORE_DUMP is required}"

printf '%s\n' "Restoring ${ORACLE_RESTORE_DUMP} into ${ORACLE_DB_USER}. This must be run only in an approved empty recovery schema."
if [[ "${CONFIRM_EMPTY_RECOVERY_SCHEMA:-}" != "YES" ]]; then
  printf '%s\n' "Set CONFIRM_EMPTY_RECOVERY_SCHEMA=YES after verifying the target schema is empty." >&2
  exit 2
fi

impdp "${ORACLE_DB_USER}/${ORACLE_DB_PASSWORD}@${ORACLE_DB_SERVICE}" \
  directory="${ORACLE_DATAPUMP_DIRECTORY:-DATA_PUMP_DIR}" \
  dumpfile="${ORACLE_RESTORE_DUMP}" \
  logfile="restore_$(date -u +%Y%m%dT%H%M%SZ).log" \
  table_exists_action=skip

printf '%s\n' "Restore finished. Run the application smoke tests and reconcile row counts before promotion."
