# Codebase Concerns

**Analysis Date:** 2026-09-24

## Tech Debt

**Java Version Requirements:**
- Issue: `build.sbt` contains a check limiting project loading if Java is older than the recommended version (warning on Java version affecting `atum-server` and `atum-database`).
- Files: `build.sbt`
- Fix approach: Ensure build environment always meets the recommended Java version (Java 11 or 17).

## Security Considerations

**AWS Credentials:**
- `server` integrates with AWS Secrets Manager and STS. Ensure local dev doesn't check in AWS keys. Use IAM roles where possible.

## Fragile Areas

**Cross-Building:**
- Issue: Heavy use of `projectMatrix` to build for multiple Spark/Scala versions. This can make the build complex and brittle to dependency upgrades (e.g., Spark 3.5.5 update might require updating other test libraries).

## Missing Critical Features

- No immediate missing features identified during map generation. Look at issues list for more context.

<!-- refreshed: 2026-09-24 -->
