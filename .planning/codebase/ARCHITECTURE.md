# Architecture

**Analysis Date:** 2026-09-24

## System Design

**Core Concept:**
The Atum Service collects and stores measured data from Spark applications and allows retrieval.

**Key Components:**
- `agent`: Library plugged into Spark applications to measure data and send it to the server.
- `server`: ZIO/HTTP4S-based API service that receives, stores, and serves measured data.
- `model`: Shared data model between agent, server, and reader.
- `database`: Relational database schema (PostgreSQL) and Doobie access patterns.
- `reader`: Client library for reading measured data from the server.

## Data Flow
1. Spark application with `agent` measures data.
2. `agent` serializes data using `model` and sends HTTP request to `server`.
3. `server` validates and persists data into `database` using ZIO and Doobie.
4. Client application uses `reader` (and `model`) to fetch data from `server` via HTTP.

## Key Abstractions
- **Effect System:** ZIO is heavily used in `server` for dependency injection and effect management.
- **API Definition:** Tapir is used to define HTTP endpoints declaratively, providing swagger UI and decoupling from HTTP4S.
- **Cross-building:** SBT `projectMatrix` is used for supporting different Scala/Spark combinations across modules.

<!-- refreshed: 2026-09-24 -->
