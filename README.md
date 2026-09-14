# TicketingSystem

A Spring Boot ticketing system built with a clean (onion) architecture, split across Maven modules so business rules stay independent of frameworks and infrastructure.

> **Status:** early scaffolding. Module boundaries, build config, and datasource wiring are in place; domain/application logic has not been implemented yet.

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

## License

TBD.
