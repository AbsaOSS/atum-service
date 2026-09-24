# Coding Conventions

**Analysis Date:** 2026-09-24

## Code Style

**Formatting:**
- `scalafmt` is used (indicated by `.scalafmt.conf`)
- Scala idiomatic style

## Error Handling

**Patterns:**
- In `server`: ZIO effects (`ZIO[R, E, A]`) are used to handle domain errors (`E`) gracefully.
- Typed errors mapped to HTTP responses using Tapir error mappings.
- In `agent` / `model`: standard Scala `Try`, `Either`, and exceptions (e.g., `AtumAgentException`).

## Logging

**Framework:**
- `zio-logging` and `logback` used heavily in `server`
- `logback` for SLF4J in `agent`

## Function Design

- Functional Programming principles heavily used
- Case classes for immutable data structures
- Type classes for JSON serialization (`circe` codecs)

<!-- refreshed: 2026-09-24 -->
