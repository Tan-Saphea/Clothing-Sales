# Production runbook

## Required configuration

Run with `SPRING_PROFILES_ACTIVE=production`. Supply `ORACLE_DB_PASSWORD`, TLS key-store variables, and bootstrap credentials only for the first administrator startup. Never store these values in source control or shell history.

The production profile requires HTTPS, secure session cookies, and ECS JSON logs. Send stdout to the platform log collector and alert on authentication lockouts, HTTP 5xx responses, database pool failures, reporting-health failures, and rejected or failed refunds.

## Database permissions

Use `database/oracle-least-privilege.sql` as the starting privilege set. Never grant `DBA` or `ANY` privileges. After schema migrations are complete, set `SCHEMA_MIGRATION_ENABLED=false` and revoke schema-creation privileges when operationally practical.

## Backup and restore

1. Run `scripts/backup-oracle.sh` from a protected operations host.
2. Copy the Data Pump output to encrypted, access-controlled, off-host storage.
3. Retain daily, weekly, and monthly recovery points according to the business retention policy.
4. At least quarterly, restore the newest backup into an isolated empty recovery schema with `scripts/restore-oracle.sh`.
5. Run `./gradlew integrationTest`, reconcile table row counts and financial totals, and record recovery time and recovery point results.

A backup is not considered valid until a restore drill succeeds.

## Deployment checks

- `./gradlew clean test bootJar`
- `./gradlew dependencyCheckAnalyze`
- `./gradlew playwrightInstall integrationTest` on a Docker-enabled runner
- Confirm `/actuator/health/liveness` and `/actuator/health/readiness` return `UP` without exposing internal details.
- Confirm direct HTTP is redirected or blocked by the load balancer.
- Confirm `BOOTSTRAP_ADMIN_ENABLED=false` and demo data is disabled.
- Complete a sale, payment, refund approval/completion, purchase receipt, stock adjustment, and backup/restore smoke test.
