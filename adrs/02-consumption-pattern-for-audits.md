# ADR 02 — Audit Consumption: Time-Windowed Measurements and Partitioning Discovery

|                         |                                                                                                                           |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------|
| **Status**              | Accepted                                                                                                                  |
| **Date**                | 2026-11-19                                                                                                                |
| **Affected components** | `server/`, `database/`; optional Scala application support in `reader/`                                                   |
| **Implementation**      | Time filtering, partitioning search, and edge-based lineage reads are implemented; grouping remains a producer convention |

Checkpoint filtering was delivered in [#488](https://github.com/AbsaOSS/atum-service/pull/488) and partitioning search
in [#493](https://github.com/AbsaOSS/atum-service/pull/493). Linking semantics and explicit lineage edges are decided
in [ADR 01](01-lineage-capability.md) (#496, #503); this ADR defines how audit and UI consumers discover partitionings,
query time-windowed measurements, and traverse lineage.

---

## 1. Context and scope

A typical audit question is:

> "For this dataset, show me the last 2 months of integrity measurements, which datasets they belong to,
> and which upstream datasets produced them."

Audit and UI consumers need to answer three distinct questions:

1. **Measurements in a time window**, associated with their partitionings.
2. **Partitioning discovery** by keys, producer (`author`), free-text substring, or measurement activity.
3. **Input/output lineage**, to compare control measures across stages.

Recipe A covers (1) **for a known scope**; Recipe B covers (2) **without known IDs or flows**. Consumers are expected to
integrate via the **server REST API** (`reader/` is an optional JVM client, not a prerequisite). Atum records control
measurements and producer-declared links; consumers remain responsible for comparisons, validation, and reporting.

---

## 2. Model & lineage summary (consumer view)

- **Partitioning identity is exact and ordered:** API represents a partitioning as an ordered `[{key, value}]` array; DB
  uniqueness applies to the whole JSONB envelope (`{"keys": [...], "version": 1, "keysToValuesMap": {...}}`). Exact
  lookup (`GET /api/v2/partitionings?partitioning=...`) does not match subsets or reordered keys.
- **Closure vs. edges:** `GET .../ancestors` and `.../descendants` read the flow closure (includes pre-v0.11.0 links,
  does not mark direct parents); `GET .../parents`, `.../children`, and `.../lineage` read explicit edges (v0.11.0+
  or lazily backfilled if re-declared, with `linkedBy`/`linkedAt`). See [ADR 01](01-lineage-capability.md) for linking
  guarantees and the mixed-chain
  caveat.

---

## 3. REST consumption recipes

| Recipe                           | Starting point                                                          | Uses                                                      |
|----------------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------|
| **A: query a known scope**       | Selected partitioning ID, complete ordered keys, or known flow          | Checkpoint/lineage endpoints with §4 time filtering       |
| **B: discover, then drill down** | Partial/exact key filters, `author`, `search-text`, and/or `[from, to)` | `GET /api/v2/partitionings/search`, then Recipe A / graph |

### Recipe A: query a known scope

| Intended scope                     | API approach                                                                   | Limitation                                                                                              |
|------------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| One exact partitioning             | `/partitionings/{id}/checkpoints`                                              | Excludes ancestors and descendants                                                                      |
| Known main flow (whole downstream) | `/partitionings/{id}/main-flow`, then `/flows/{flowId}/checkpoints`            | Includes unrelated sibling branches                                                                     |
| Recorded ancestors only            | Exhaust `/partitionings/{id}/ancestors`, then checkpoints per ID               | Multi-request; no direct-vs-transitive marker                                                           |
| Direct inputs only                 | `/partitionings/{id}/parents`, then checkpoints per node                       | Empty for un-backfilled links written before v0.11.0                                                    |
| Bounded upstream/downstream graph  | `/partitionings/{id}/lineage?direction=...&depth=n`, then checkpoints per node | Edges v0.11.0+ (or lazily backfilled); expand via `expandable*NodeIds`; stops at edge-less legacy links |

When combining several flows or partitionings, deduplicate by checkpoint UUID and merge in the desired order (`limit`/
`offset` apply per request). Set `include-properties=true` only when property values are needed in the response (server
enriches properties per checkpoint, adding DB queries).

**Flow:** `GET /api/v2/partitionings?partitioning=<base64url>` → `GET /api/v2/partitionings/{id}/main-flow` → page
`GET /api/v2/flows/{flowId}/checkpoints?limit=100&offset=0&from=...&to=...&latest-first=true` while
`pagination.hasMore` is `true` (treat failed requests as incomplete, not empty).

### Recipe B: partitioning discovery and drill-down

`GET /api/v2/partitionings/search` returns `PaginatedResponse[PartitioningWithIdDTO]` (`id`, registration `author`,
ordered `partitioning` array; no checkpoint counts or measurements). No matches return `200` with `data: []` and
`hasMore: false`.

#### Meaning of the time window

When `from`/`to` are supplied, a partitioning qualifies if **at least one checkpoint** satisfies
`from <= process_start_time < to` (historical checkpoints count even if newer checkpoints exist after `to`; omitting
`to`
leaves the upper bound `+infinity`). Searches without time bounds include partitionings that have zero checkpoints.

| Interpretation                      | Dimension                        | Meaning                                                       |
|-------------------------------------|----------------------------------|---------------------------------------------------------------|
| **Measured recently (implemented)** | Checkpoint `process_start_time`  | Partitionings with measurements in the window                 |
| Registered recently                 | Partitioning `created_at`        | Partitionings registered in the window                        |
| Reported recently                   | Checkpoint `created_at`          | Partitionings with checkpoints ingested in the window         |
| Business dates                      | Partition key (e.g. `info_date`) | Filter via `keys` (exact string) or `search-text` (substring) |

#### Search Contract (parameters and validation)

Returns `PaginatedResponse[PartitioningWithIdDTO]`
([`PartitioningWithIdDTO`](../model/src/main/scala/za/co/absa/atum/model/dto/PartitioningWithIdDTO.scala)):
`id`, registration `author` (not necessarily the latest checkpoint author), and ordered `partitioning` array. Search
does not return checkpoint metadata or the underlying measurements, or checkpoint counts. No matches (or an offset past
the end) returns HTTP `200` with `data: []` and `hasMore: false`, not `404`.

| Input Parameter   | Behaviour                                                                                                                                                                                                                                                  |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `keys`            | Optional base64url UTF-8 JSON object (`Map[String, Option[String]]`). String values match exact key/value pairs (empty string is literal); `null` matches key existence (any value). Stored key order is ignored during matching and preserved in results. |
| `exact-keys`      | Optional boolean (default `false`). When `true`, stored key count must equal `keys` key count (exact key set, any order). Requires non-empty `keys`.                                                                                                       |
| `author`          | Optional non-blank exact match on registration author (`runs.partitionings.created_by`; empty or whitespace-only values are rejected).                                                                                                                     |
| `search-text`     | Optional case-insensitive substring (`ILIKE`) against any key name or value (SQL `LIKE` wildcards `%`, `_`, `\` are escaped literally). Must contain at least 2 non-whitespace characters.                                                                 |
| `from`, `to`      | Half-open `[from, to)` on `process_start_time` (ISO-8601 with offset); `to` requires `from`; window ≤ 365 days (`from` alone ≤ 365 days ago).                                                                                                              |
| `latest-first`    | Optional boolean (default `false`, ID ascending). `true` orders by partitioning ID descending.                                                                                                                                                             |
| `limit`, `offset` | `limit` `1–1000` (default `10`), `offset >= 0` (default `0`).                                                                                                                                                                                              |

At least one non-empty `keys`, `author`, `search-text`, or `from` is required (invalid inputs, including whitespace-only
`author` or `search-text` with fewer than 2 non-whitespace characters, return `400`). Search
rounds nanosecond bounds up to microsecond storage precision (returning an empty page if the rounded window is empty).

```http
# Exact key-set with key/value + key-existence wildcard ("only table_name=abc and country=<any>")
GET /api/v2/partitionings/search?keys=eyJ0YWJsZV9uYW1lIjoiYWJjIiwiY291bnRyeSI6bnVsbH0&exact-keys=true&limit=100

# Producer (author) + case-insensitive substring + checkpoint activity window
GET /api/v2/partitionings/search?author=unify&search-text=customer_payments&from=2026-10-01T00:00:00Z&to=2026-10-15T00:00:00Z
```

**Query & cost notes:** `runs.search_partitionings` selects distinct qualifying partitionings **before** pagination
(`LIMIT i_limit + 1` for `hasMore`), so multiple checkpoints or shared flows never duplicate rows. Note that `/search`
discovers partitioning IDs only — rendering per-row checkpoint counts, latest values, or measure-shift status still
requires querying checkpoints per partitioning (or using a single known flow via Recipe A).

### Shared time and consistency contract

`from <= processStartTime < to` is a **measurement-time** window (`AtumContext.createCheckpoint` captures start time
right before measuring). Hold fixed bounds across pages. All paginated endpoints provide **best-effort live pagination,
not snapshot isolation**: concurrent inserts or late reports can shift offsets between pages.

---

## 4. Implementation & index notes

- **Checkpoint endpoints** (`/flows/{flowId}/checkpoints`, `/partitionings/{id}/checkpoints`) use composite B-tree
  `checkpoints_idx2 (fk_partitioning, process_start_time)` and property index `(fk_checkpoint, property_name)`. BRIN was
  rejected because late/backfilled reports break physical `process_start_time` ordering.
- **Search migrations** ([`V0.10.0.12`](../database/src/main/postgres/runs/V0.10.0.12__partitionings_keys_index.ddl),
  [`V0.10.0.13`](../database/src/main/postgres/runs/V0.10.0.13__checkpoints_start_time_partitioning_index.ddl),
  [`V0.10.0.14`](../database/src/main/postgres/runs/V0.10.0.14__search_partitionings.sql),
  [`V0.10.0.15`](../database/src/main/postgres/runs/V0.10.0.15__partitionings_created_by_index.ddl)):
    - `partitionings_keys_to_values_idx` uses default `jsonb_ops` GIN on `partitioning -> 'keysToValuesMap'` so one
      index
      serves both `@>` (key/value containment) and `?&` (key existence).
    - `partitionings_created_by_idx` (`V0.10.0.15`) adds a B-tree index on `runs.partitionings (created_by)` for
      standalone `author` queries; `runs.search_partitionings` uses a dedicated static branch for `author` without
      `keys` or time bounds so cached generic plans keep using this index.
    - `runs.search_partitionings` splits `i_keys` into non-null pairs (`@> _kv_keys`) and all key names
      (`?& _all_keys`),
      and when `i_exact_keys = TRUE` checks `jsonb_array_length(partitioning -> 'keys') = cardinality(_all_keys)` (sound
      because `validation.is_partitioning_valid` enforces unique keys and 1-to-1 parity with `keysToValuesMap`).
    - `i_search_text` requires at least 2 non-whitespace characters (avoiding single-character full-table scans) and
      delegates `ILIKE` matching over `jsonb_each_text(partitioning -> 'keysToValuesMap')` to internal SQL helper
      `runs._partitioning_matches_search_text` rather than raw JSON text so JSON punctuation does not cause false
      matches.
    - Migrations are additive (safe to deploy before server; concurrent index failure recovery is documented in each
      DDL).

---

## 5. Grouping convention (optional rollup parent)

Because exact lookup requires full keys in exact order, producers that want a single flow covering sub-partitions (e.g.,
per `currency`) can register a parent partitioning without `currency` and link each sub-partitioning via
`subPartitionContext` / `getOrCreateAtumSubContext`. Consumers can then query the rollup parent's main flow, noting that
this creates **group membership only** — not aggregate measurements or sibling-to-downstream lineage.

---

## 6. Alternatives considered

- **Partitioning-first `/search` (chosen for Recipe B):** Avoids checkpoint/flow duplicates without altering existing
  endpoints; leaves bulk snapshot export deferred.
- **Overload `GET /api/v2/partitionings` (rejected):** Would change single-object/`404` exact-lookup semantics relied on
  by Agent and Reader; `/search` is additive and unambiguous against numeric `/{id}`.
- **Dedicated graph endpoint:** Delivered as `GET .../lineage`
  ([ADR 01 §7](01-lineage-capability.md#7-hybrid-edges-schema-responsibilities-and-graph-reads-503-493));
  returns structure only, no measurements.
