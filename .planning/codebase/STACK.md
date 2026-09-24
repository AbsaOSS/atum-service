# Tech Stack

**Analysis Date:** 2026-09-24

## Core Technologies

**Languages:**
- Scala (2.13 primary)
- Java (Required/Recommended bounds via build.sbt)

**Runtimes & Frameworks:**
- Apache Spark (3.5.5) - For the `agent` module processing data
- ZIO (2.0.19) - Core effect system and DI for `server`
- HTTP4S (0.23) & Tapir (1.9) - HTTP server and API definitions
- STTP (3.5/3.9) - HTTP client

## Infrastructure

**Databases:**
- PostgreSQL (Flyway for migrations via `sbt-flyway`)
- Doobie / `fa-db` (0.7.0) for DB access

**Cloud & Deployment:**
- AWS SDK (SecretsManager, STS)

## Key Dependencies

**Libraries:**
- `circe` for JSON serialization
- `scalatest` & `mockito-scala` & `zio-test` & `specs2` for testing
- `logback` & `zio-logging` for logging

## Configuration

**Tools:**
- SBT (build tool, `projectMatrix` used for cross-building)
- TypeSafe Config & ZIO Config

<!-- refreshed: 2026-09-24 -->
