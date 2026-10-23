# Staging PostgreSQL read-only verification

Status (2026-10-24): the operator restored a PostgreSQL 18 production snapshot to a separate Railway staging PostgreSQL instance. Exact staging counts observed using psql:

| Table | Production observed | Staging restored |
| --- | ---: | ---: |
| admission_results | 54,591 | 54,591 |
| admission_metrics | 149,332 | 149,332 |
| admission_aggregates | 4,502 | 4,502 |
| universities | 484 | 484 |

Equal counts confirm snapshot-level row totals, **not** equality of each row or Django/Spring behavior. Staging also holds real member data/password hashes: do not send dumps, passwords, secret URLs, or raw account rows to ChatGPT or GitHub.

## Run on Windows PowerShell

1. Keep only the **staging** Railway tunnel running in a separate terminal. Close any production tunnel.
2. Open a new PowerShell. Download/update this repository:

    git clone https://github.com/dh1180/K-unirank_BackEnd.git

   For an existing clone, cd into it and run git pull --ff-only.

3. In the repository root run:

    .\scripts\verify-staging.ps1 -StagingPort YOUR_STAGING_TUNNEL_PORT

   (for example: -StagingPort 15432; use the actual tunnel port). Only input the staging DB password when prompted. It will not be displayed. Never paste the password here.

4. If you do not have JDK 21 and Maven 3.9+ installed, run the SQL-only portion first:

    .\scripts\verify-staging.ps1 -StagingPort YOUR_STAGING_TUNNEL_PORT -SqlOnly

   That is just a reconfirmation of row totals; it does **not** prove Spring Boot works. To run Hibernate schema validation and the API smoke test, install Java 21 + Maven and rerun without -SqlOnly.

5. If Windows PowerShell execution policy blocks the reviewed script, use a process-scoped bypass only:

    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

## Safety guarantees and limitations

- PostgreSQL statements in the script are SELECT and BEGIN READ ONLY. No data writes are performed.
- The Java test is opt-in (KUNIRANK_STAGING_SMOKE=YES), requires an explicit localhost JDBC URL, and initializes each database session with default_transaction_read_only=on. Hibernate uses ddl-auto=validate.
- The script does **not** automatically distinguish production and staging tunnels at the same localhost port. The operator must verify the Railway CLI environment is **staging** before typing STAGING at the prompt.
- The Java tests use only university, admissions, comparison, and VS counts/read-only API requests. They do not query auth_user, django_session, or favorites and do not export student, password, or identity rows.
- The tests may deliberately fail if SQL mappings, cut metric values, or controller assumptions differ from the real staging data. That failure is actionable; share only redacted error text, never the dump or secrets.
- CI's ordinary unit and synthetic Testcontainers tests remain unchanged: the opt-in staging test is skipped there.
- This does not configure Railway services or change the production Django application.

## Next

Review the local results, fix discovered Java JPA mappings/API behavior in PRs, then compare Django and Spring outputs for matching filters against the same staging snapshot. Test Next.js / Raspberry Pi integration only after this read-only staging verification succeeds.
