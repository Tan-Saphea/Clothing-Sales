#!/usr/bin/env bash
set -euo pipefail

: "${ORACLE_DB_USER:?ORACLE_DB_USER is required}"
: "${ORACLE_DB_PASSWORD:?ORACLE_DB_PASSWORD is required}"
: "${ORACLE_DB_SERVICE:?ORACLE_DB_SERVICE is required}"
: "${ORACLE_BACKUP_DIR:?ORACLE_BACKUP_DIR is required and must be a protected directory}"

backup_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump_name="clothing_${backup_stamp}.dmp"
log_name="clothing_${backup_stamp}.log"

umask 077
expdp "${ORACLE_DB_USER}/${ORACLE_DB_PASSWORD}@${ORACLE_DB_SERVICE}" \
  schemas="${ORACLE_DB_USER}" \
  directory="${ORACLE_DATAPUMP_DIRECTORY:-DATA_PUMP_DIR}" \
  dumpfile="${dump_name}" logfile="${log_name}" compression=all

printf '%s\n' "Oracle Data Pump backup created: ${dump_name}"
printf '%s\n' "Copy it from the Oracle Data Pump directory into ${ORACLE_BACKUP_DIR}, encrypt it, and verify retention."
