# Oracle database setup

The application requires an Oracle schema user and does not create database users automatically.

1. Create an empty Oracle user/schema with permission to create tables, views, procedures, packages, triggers, and indexes.
2. Run `oracle-bootstrap.sql` while connected as that schema user.
3. Set `ORACLE_DB_USER` and `ORACLE_DB_PASSWORD` before starting the application.
4. Override `ORACLE_DB_HOST`, `ORACLE_DB_PORT`, `ORACLE_DB_SERVICE`, or `ORACLE_DB_SCHEMA` when they differ from the defaults. The schema defaults to `ORACLE_DB_USER`.

Example with SQL*Plus:

```shell
sqlplus "$ORACLE_DB_USER/$ORACLE_DB_PASSWORD@//localhost:${ORACLE_DB_PORT:-1522}/FREEPDB1" @database/oracle-bootstrap.sql
```

`oracle-bootstrap.sql` is intended for an empty schema. Existing installations use the guarded application migration for the `PRODUCT.IMAGE_URL` column and the re-runnable hardened sales, purchase, inventory, and payment procedures in `src/main/resources/db/oracle-hardening.sql`.

For a clean production installation, provision the first administrator once:

```shell
export BOOTSTRAP_ADMIN_ENABLED=true
export BOOTSTRAP_ADMIN_USERNAME=admin_owner
export BOOTSTRAP_ADMIN_PASSWORD='replace-with-a-unique-strong-password'
export BOOTSTRAP_ADMIN_NAME='System Administrator'
./gradlew bootRun
```

After the account is created, stop the application, unset `BOOTSTRAP_ADMIN_PASSWORD`, and set `BOOTSTRAP_ADMIN_ENABLED=false`. A later bootstrap run never overwrites an existing account password.

Demo data is disabled by default. When it is deliberately enabled, set strong, unique values for `DEMO_ADMIN_PASSWORD` and `DEMO_CASHIER_PASSWORD`; both must contain at least 12 characters with upper-case, lower-case, number, and symbol.
