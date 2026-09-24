# Phase 1: Server Backend Support for Multi-Value Filters

## Goal
Enhance the database queries and API models in `atum-server` to support `IN`-list property filters.

## Requirements
- CHK-01: Server API accepts multiple values for properties.
- CHK-02: Server backend translates these to SQL `IN` clauses.

## Plans

### 1. Update Checkpoint Query Models
**What:** Update the query parameter model for checkpoint filtering to support arrays.
**Implementation:**
- Change `checkpointProperties` in `GetFlowCheckpoints` and `GetPartitioningCheckpoints` endpoints from `Option[Map[String, String]]` to `Option[Map[String, Seq[String]]]`.
- Update Tapir endpoint definitions in `server/src/main/scala/za/co/absa/atum/server/api/v2/endpoints/` (e.g., `FlowEndpoints` and `PartitioningEndpoints`).
- Ensure the base64 decoding logic correctly parses `Map[String, Seq[String]]` from JSON.

### 2. Update Database Access Logic
**What:** Adapt Doobie queries to use `IN` clauses when multiple values are provided for a property.
**Implementation:**
- In the repository layer (e.g. `FlowRepositoryImpl` or wherever the Doobie `fr` fragments are built), intercept `checkpointProperties`.
- For each property key, if the sequence has multiple values, use `IN` clause (e.g. `fr"property_value IN (${values.toList})"`).
- Ensure backward compatibility: if a sequence has one value, `IN` or `=` are both fine.

### 3. Update Tests
**What:** Update existing unit and integration tests to match the new type signature.
**Implementation:**
- Update `GetFlowCheckpointsEndpointUnitTests` and `GetPartitioningCheckpointsEndpointUnitTests` to pass `Seq("value")` instead of `"value"`.
- Add new test cases with multiple values to verify the `IN` clause works properly.

## Risks
- **Backward Compatibility:** Existing clients sending `Map[String, String]` JSON may break if Circe cannot decode it into `Map[String, Seq[String]]`. We need to configure Circe decoding to accept both or ensure the reader update lands concurrently.

## Verification
- Unit tests pass.
- Integration tests confirm correct filtering with multiple values.
