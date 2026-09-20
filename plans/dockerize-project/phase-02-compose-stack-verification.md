# Phase 2: Compose Stack & Verification

## Requirements
Running `docker compose up --build` from repo root brings up the application and a PostgreSQL database together, with the app successfully connecting to the database on startup.

## Confirmed Config (from user validation)
- App port: `8080:8080` (host:container).
- DB port: `5432:5432` — exposed to host for local psql/pgAdmin access.
- DB persistence: named volume (e.g. `pgdata`), survives `docker compose down` (not `down -v`).
- Credentials (`.env.example` placeholders): `POSTGRES_DB=ticketing_system`, `POSTGRES_USER=ticketing_user`, `POSTGRES_PASSWORD=changeme`.

## Steps
1. Add a `docker-compose.yml` at repo root defining an `app` service (built from the Phase 1 Dockerfile) and a `db` service (official `postgres:16-alpine` image).
2. Configure the `db` service with `POSTGRES_DB=ticketing_system`, `POSTGRES_USER=ticketing_user`, `POSTGRES_PASSWORD` (from `.env`), and a named volume (`pgdata:/var/lib/postgresql/data`) so data persists across restarts.
3. Add a Postgres healthcheck (`pg_isready -U ticketing_user -d ticketing_system`) to the `db` service, and make `app` depend on `db` being healthy before starting.
4. Wire the app's `DB_URL` (`jdbc:postgresql://db:5432/ticketing_system`), `DB_USERNAME`, `DB_PASSWORD` environment variables to point at the `db` service using Docker's internal networking. Map `8080:8080` for the app and `5432:5432` for the db.
5. Move credentials out of `docker-compose.yml` into a `.env` file consumed by Compose; commit a checked-in `.env.example` with the placeholder values above and git-ignore the real `.env`.
6. Run `docker compose up --build` and verify both containers reach a healthy/running state and the app connects to the database without errors.

## Success Criteria
- `docker compose up --build` starts both `db` and `app` containers with no manual intervention.
- `docker compose logs app` shows Hibernate/JPA successfully connecting to PostgreSQL and the embedded Tomcat server starting, with no connection-refused or authentication errors.
- `docker compose ps` shows the `db` service as healthy and `app` as running.

## Risks
- App container starts before Postgres finishes initializing: mitigated by the `db` healthcheck + `depends_on: condition: service_healthy` from Phase 2 step 3.
- Real credentials accidentally committed: mitigated by git-ignoring `.env` and only committing `.env.example` with placeholders; verify with `git status` before any commit.
