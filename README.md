# LeaveFlow — Workforce Availability Intelligence

LeaveFlow is a Spring Boot + React leave-workflow demo. It makes the approval state machine, team-coverage impact, pro-rated balance, audit trail, and SLA escalation visible rather than treating leave as CRUD.

## Database: Supabase PostgreSQL

The backend connects directly to Supabase PostgreSQL through JDBC; the browser never receives a database password or a Supabase service key. In Supabase Dashboard, open **Connect** and choose **Session pooler** for a typical Windows/IPv4 local backend, then copy the host, user, and password into a local `.env` based on `.env.example`. Keep `sslmode=require`.

For a persistent backend, Supabase recommends direct connection when its network supports it; Session pooler is the appropriate IPv4-friendly alternative. Transaction pooler is not the default here because prepared statements require special handling. See [Supabase connection guidance](https://supabase.com/docs/guides/database/connecting-to-postgres).

1. Create a Supabase project and wait until it is ready.
2. Copy `.env.example` to `.env` and replace all placeholder values. Do not commit `.env`.
3. In PowerShell, set the variables for the current terminal:

```powershell
$env:SUPABASE_DB_URL='jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=require'
$env:SUPABASE_DB_USER='postgres.YOUR_PROJECT_REF'
$env:SUPABASE_DB_PASSWORD='your database password'
```

Flyway automatically applies `backend/src/main/resources/db/migration/V1__baseline.sql` on first backend startup. The migration creates the normalized tables and safe demo seed data.

## Run

```powershell
# Backend, after installing Maven or adding Maven Wrapper
cd backend
mvn spring-boot:run

# Separate terminal: frontend
cd frontend
npm.cmd install
npm.cmd run dev
```

Demo user passwords in the seeded database: `demo123` for `employee@example.com`, `manager@example.com`, and `hr@example.com`.

## State machine

`DRAFT → MANAGER_PENDING → HR_PENDING → APPROVED`, with explicit rejection, cancellation and escalation states. Escalation never approves a request; it only changes it to an escalation state and records a system audit event. See [the exact diagram](docs/state-machine.md).

## Policies in this demo

* 20 annual days, pro-rated by calendar months from the employee join month.
* Weekends are excluded in the UI policy description.
* 20% projected team absence is an organization-configurable warning threshold—not an auto-rejection rule.
