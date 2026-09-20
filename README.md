# TicketingSystem

A Spring Boot ticketing system built with a clean (onion) architecture, split across Maven modules so business rules stay independent of frameworks and infrastructure.

> **Status:** domain layer complete (208 unit tests); application layer P1 complete (Event & TicketType CRUD use-cases, 32 tests). Infrastructure and presentation layers not yet implemented.

## Tech Stack

| Concern            | Choice                                      |
|--------------------|----------------------------------------------|
| Language           | Java 21                                      |
| Framework          | Spring Boot 4.1.1                            |
| Build              | Maven (multi-module)                         |
| Persistence        | Spring Data JPA + PostgreSQL                 |
| Object mapping     | MapStruct                                    |
| Boilerplate        | Lombok                                       |
| Cross-cutting      | Spring AOP / AspectJ                         |
| API docs           | springdoc-openapi (Swagger UI)               |
| Validation         | Spring Validation                            |

## Architecture

The codebase follows clean architecture: dependencies point inward, toward `domain`. Each module is its own Maven artifact with explicit `pom.xml` dependencies enforcing the rule.

```
bootstrap  ──depends on──▶ infrastructure, presentation
infrastructure ─────────▶ application
presentation ────────────▶ application
application ─────────────▶ domain
domain                    (no dependencies)
```

| Module           | Responsibility                                                                 |
|------------------|----------------------------------------------------------------------------------|
| `domain`         | Core business entities and rules. No framework dependencies.                    |
| `application`    | Use cases / business logic orchestration, depends only on `domain`.             |
| `infrastructure` | JPA persistence, MapStruct mappers, and other technical implementations.        |
| `presentation`   | REST controllers, DTOs, validation, OpenAPI/Swagger config.                     |
| `bootstrap`      | Spring Boot entry point; wires `infrastructure` + `presentation` into a runnable app. |

## Project Structure

```
TicketingSystem/
├── domain/            # business entities & rules
├── application/       # use cases
├── infrastructure/     # JPA, mappers, external integrations
├── presentation/       # REST controllers, DTOs
├── bootstrap/          # Spring Boot application entry point
│   └── src/main/resources/application.yml
└── pom.xml             # parent/aggregator POM
```

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL instance reachable from your machine

### Configuration

The app reads its datasource from environment variables (see `bootstrap/src/main/resources/application.yml`):

| Variable      | Description              |
|---------------|---------------------------|
| `DB_URL`      | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/ticketing` |
| `DB_USERNAME` | Database username          |
| `DB_PASSWORD` | Database password          |

### Build

```bash
mvn clean install
```

### Run

```bash
DB_URL=jdbc:postgresql://localhost:5432/ticketing \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
mvn -pl bootstrap spring-boot:run
```

The app starts on `http://localhost:8080` by default.

### API Documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec:

```
http://localhost:8080/v3/api-docs
```

## Git Workflow

### Branching model

This repo uses a **simplified Git Flow**: `main` (production) ← `dev` (integration) ← `feature/*`, `fix/*`.

- `main` and `dev` are protected branches — all changes land via PR/MR, never pushed to directly.
- Feature/fix branches are short-lived: delete after merge to avoid long-lived branches accumulating large conflicts.
- Never name a branch after a person (e.g. `phat-branch`) — the name should describe the change, not the author.

### Branch naming

| Type          | When to use                              |
|---------------|--------------------------------------------|
| `feature/`    | New feature                                |
| `fix/`        | Bug fix                                    |
| `hotfix/`     | Urgent fix on production                   |
| `refactor/`   | Code restructuring, no behavior change     |
| `chore/`      | Chores (dependency bumps, config, ...)     |
| `docs/`       | Documentation only                         |
| `test/`       | Add/fix tests                              |
| `release/`    | Release prep (e.g. `release/1.2.0`)        |

**Description rules:**
- Lowercase, words joined with hyphens (kebab-case): `feature/user-authentication`
- Short but descriptive — no unclear abbreviations
- English, to avoid encoding issues and stay tool/CI-friendly
- If there's an issue tracker (Jira, GitHub Issues), include the ID: `feature/TICK-123-add-payment-gateway`

**Examples for this project:**

```
feature/ticket-domain-model
feature/booking-service
fix/duplicate-ticket-booking
refactor/clean-architecture-domain-layer
chore/update-spring-boot-3.3
docs/domain-design-doc
```

### Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>`, using the same `type` values as the branch prefixes above (`feat`, `fix`, `refactor`, `chore`, `docs`, `test`).

```
fix(config): remove leading space in application.yml filename
docs: add project README
feat(ticket): add ticket domain model
```

## License

TBD.
