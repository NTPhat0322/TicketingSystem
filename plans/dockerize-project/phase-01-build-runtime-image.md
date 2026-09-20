# Phase 1: Build & Runtime Image

## Requirements
A `docker build` at the repo root produces a runnable image containing the packaged `bootstrap` module, without shipping build tooling or unrelated repo files.

## Steps
1. Add a multi-stage `Dockerfile` at repo root: build stage on a Maven+JDK 21 base, runtime stage on a slim JRE 21 base.
2. In the build stage, copy the root `pom.xml` and every module's `pom.xml` (domain, application, infrastructure, presentation, bootstrap) first, then run an offline dependency resolution step to enable Docker layer caching.
3. Copy the rest of the source tree and run the Maven reactor package for the `bootstrap` module (with its reactor dependencies), skipping tests.
4. In the runtime stage, copy only the built `bootstrap` jar from the build stage and set it as the container entrypoint.
5. Expose the application's HTTP port and confirm the image runs standalone as a sanity check before wiring it into Compose.
6. Add a `.dockerignore` at repo root excluding `target/`, `.git`, IDE metadata, and other non-source artifacts from the build context.

## Success Criteria
- `docker build -t ticketing-system .` completes successfully from repo root.
- `docker run --rm ticketing-system` starts the Spring Boot application process (log output shows Tomcat/Spring startup) even though it will fail to reach a database at this stage — that's expected and resolved in Phase 2.

## Risks
- Reactor build fails if module poms are copied in the wrong structure/order: mitigate by mirroring the exact directory layout (`domain/pom.xml`, `application/pom.xml`, etc.) relative to root `pom.xml` before copying sources.
- Slim JRE base image missing a required library for the packaged jar: mitigate by using an official `eclipse-temurin:21-jre` (or equivalent) image and validating with the standalone `docker run` check.
