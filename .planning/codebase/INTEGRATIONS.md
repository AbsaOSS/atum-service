# Integrations

**Analysis Date:** 2026-09-24

## External APIs

**AWS Services:**
- AWS Secrets Manager (`software.amazon.awssdk:secretsmanager`)
- AWS STS (`software.amazon.awssdk:sts`)

**Data Processing:**
- Apache Spark (via `agent` plugin)

## Databases

**PostgreSQL:**
- Connected via `doobie` / `fa-db` in the `server` module
- Migrations managed via Flyway

## Metrics & Observability

**Prometheus:**
- `zio-metrics-connectors-prometheus`
- `http4s-prometheus-metrics`
- `tapir-prometheus-metrics`

<!-- refreshed: 2026-09-24 -->
