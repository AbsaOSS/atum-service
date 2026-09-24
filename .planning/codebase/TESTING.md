# Testing Patterns

**Analysis Date:** 2026-09-24

## Test Framework

**Runners:**
- `scalatest` (3.2) - primary for non-ZIO modules (`agent`, `model`, `reader`)
- `zio-test` - primary for the `server` module
- `specs2` - also present in `model` module dependencies

**Mocking:**
- `mockito-scala` for ScalaTest mocking

## Test Structure

- Standard SBT structure (`src/test/scala`)
- `api-tests/` directory contains HTTP API integration tests (potentially using `balta` or HTTP clients)

## Run Commands
```bash
sbt test
sbt "project server" test
```

## Mocking
- ZIO environment (`ZLayer`) is used for dependency injection and mocking in `server` tests.
- `mockito-scala` used in `agent` for mocking Spark or HTTP clients.

<!-- refreshed: 2026-09-24 -->
