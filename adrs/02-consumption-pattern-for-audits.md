# ADR 02 — Audit Consumption: Time-Windowed Measurements and Flow Membership

|                         |                                                                                                                          |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Status**              | Accepted; checkpoint time filtering released in v0.10.0, partitioning search and lineage reads in v0.11.0                |
| **Date**                | 2026-10-14                                                                                                               |
| **Revised**             | 2026-11-17 (search contract as merged, explicit lineage edges)                                                           |
| **Affected components** | `server/`, `database/`; optional Scala application support in `reader/`                                                  |
| **Implementation**      | Time filtering, partitioning search and edge-based lineage reads are implemented; grouping remains a producer convention |

Checkpoint filtering was delivered in [#488](https://github.com/AbsaOSS/atum-service/pull/488) and partitioning search
in
[#493](https://github.com/AbsaOSS/atum-service/pull/493). Linking semantics and explicit lineage edges are decided in
[ADR 01](01-lineage-capability.md) (#496, #503); this ADR covers how audit consumers read measurements, search
partitionings, and navigate lineage.

---

## 1. Context and scope

A typical audit question is:

> "For this dataset, show me the last 2 months of integrity measurements, which datasets they belong to,
> and which upstream datasets produced them."

These are distinct concerns:

1. **Measurements in a time window**, associated with their partitionings.
2. **Discovery of the relevant partitionings**, across dates, executions, or upstream branches.
3. **An input/output lineage**, relevant when comparing control measures across stages.

Recipe A covers (1) **for an already identified scope**. Recipe B adds partitioning-first discovery by partial keys
and/or checkpoint activity for audit and UI consumers without a known flow. Note on (3): flow membership and declared
edges record what producers submitted, not proof that a source dataset was actually consumed.

Audit consumers are expected to integrate through the **server REST API**. The Scala Reader (`reader/`) is an optional
client for JVM applications, not a prerequisite. As described in the [README](../README.md), Atum captures control
measurements; consumers remain responsible for comparisons, validation, and reporting.

---

## 2. What the model and API provide

- **Partitioning identity is exact and ordered.** The API represents a partitioning as an ordered array of
  `{key, value}` objects; storage uses `{ "keys": [...], "version": 1, "keysToValuesMap": { ... } }` with uniqueness
  over the whole JSONB value. Exact lookup (`GET /api/v2/partitionings?partitioning=...`) and checkpoint time filters
  do not search for other matching partitionings.
- **Flows store transitive closure; edges store direct links.** Every partitioning gets its own main flow `F(A)`.
  Under v0.11.0+ linking rules ([ADR 01](01-lineage-capability.md)), only leaf partitionings can be linked under new
  parents (`409` otherwise), cycles are rejected (`409`), and `members(F(A)) = {A} ∪ descendants(A)` holds by
  construction. For write-side guarantees, copy flags, advisory locking, and the deprecated v1 create race, see ADR 01
  §2–§3 and §7.
- **`ancestors` / `descendants` vs. `parents` / `children` / `lineage`.**
    - `GET .../ancestors` and `.../descendants` read the flow closure (paginated, default `limit=10`): `ancestors`
      returns
      all intermediate and root primary partitionings whose flows contain `X`, without marking direct parents or
      identifying a unique root. Empty lists return `200` with `data: []`.
    - `GET .../parents`, `.../children`, and `.../lineage?direction=&depth=` read explicit edges
      (`lineage.partitioning_edges`, written from v0.11.0 onward) with `linkedBy`/`linkedAt` metadata.
- **No backfill for pre-v0.11.0 data.** Links written by v0.6–v0.10 servers have no edges (unless a producer re-runs and
  re-declares an already-known ancestor, which lazily backfills the missing edge) and may have incomplete descendant
  membership from legacy `PATCH` calls. For pre-v0.11.0 links, `parents` and `children` return `[]` and `lineage`
  returns only the focal node, while `ancestors` and `descendants` still read the closure.
- **Checkpoints and flow partitionings.** `GET /api/v2/flows/{flowId}/checkpoints` returns paginated checkpoints with
  nested measurements and `partitioning` (`id`, ordered keys, `author`) — no parent-child edges.
  `GET /api/v2/flows/{flowId}/partitionings` lists membership without measurements (including members with no
  checkpoints in the time window). Membership itself is not time-filtered.

```text
Links established in v0.11.0+: A -> B -> C
  mainFlow(A): {A, B, C}     ancestors(C): {A, B}  (includes intermediate B)
  mainFlow(B): {B, C}        parents(C):   {B}     (direct edge only; empty for pre-v0.11.0 links)
  mainFlow(C): {C}
```

---

## 3. REST consumption recipes

| Recipe                                         | Starting point                                                         | Uses                                                      |
|------------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------|
| **A: query a known scope**                     | An explicitly selected partitioning ID, complete keys, or known flow   | Existing endpoints with §4 checkpoint time filtering      |
| **B: discover partitionings, then drill down** | Partial partitioning keys and/or a checkpoint activity window (no IDs) | `GET /api/v2/partitionings/search`, then Recipe A / graph |

### Recipe A: query a known scope

#### Establish the scope first

| Intended scope                          | API approach                                                                        | Limitation                                                                          |
|-----------------------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| One exact partitioning                  | `/partitionings/{id}/checkpoints`                                                   | Excludes ancestors and descendants                                                  |
| Membership of a known main flow         | Resolve `/partitionings/{id}/main-flow`, then `/flows/{flowId}/checkpoints`         | Includes all downstream members, potentially unrelated sibling branches             |
| Target and its recorded ancestors only  | Exhaust `/partitionings/{id}/ancestors`, then query checkpoints per distinct ID     | Multiple requests; ancestors are not marked as direct or transitive                 |
| Target and its direct inputs only       | `/partitionings/{id}/parents`, then checkpoints of target and each parent           | Empty for links written before v0.11.0                                              |
| Upstream or downstream graph to a depth | `/partitionings/{id}/lineage?direction=upstream&depth=n`, then checkpoints per node | Edge-based (v0.11.0+ links); expand via `expandable*NodeIds` when `depth`/cap stops |
| All dates/executions for a business key | Search by partial keys, optionally narrowed by checkpoint activity (Recipe B)       | Exact key/value match only; no business-date range or automatic lineage resolution  |

If whole upstream-root flows are wanted, discover **all** candidate roots rather than picking the first ancestor. Page
`/ancestors` until reaching nodes with no ancestors (a target with no ancestors is itself a candidate root), tracking
visited IDs. Remember that root flows can include sibling branches unrelated to the target, and pre-v0.11.0 links may
have incomplete membership.

Where edges exist, `/lineage?direction=upstream` finds candidate roots as nodes with no incoming edges that are **not**
in `expandableUpstreamNodeIds`, with two caveats: (1) if `depth` (max 20) or the 5,000-node cap cuts traversal, you must
follow `expandableUpstreamNodeIds` to reach deeper roots; and (2) in a **mixed** chain where a v0.11.0+ link sits
downstream of a pre-v0.11.0 link (which has no edge), `/lineage` stops at the older node without listing it as
expandable, whereas `/ancestors` still includes the older upstream ancestors.

When combining several flows or partitionings, deduplicate by checkpoint ID and merge in the desired checkpoint order;
`limit`/`offset` apply per request, not globally.

#### Worked example

Assumes the partitioning below is registered and its downstream flow is the desired scope (filtering by **measurement
times**, not `info_date`):

```json
[
  {
    "key": "catalog_path",
    "value": "edla_dev_cams_za_publish_rl.aq_eb_cams_tcsctot_qst_ah_tots"
  },
  {
    "key": "info_date",
    "value": "2026-06-01"
  }
]
```

1. Base64url-encode the UTF-8 JSON (preserving array order) and look up its ID:

```http
GET /api/v2/partitionings?partitioning=W3sia2V5IjoiY2F0YWxvZ19wYXRoIiwidmFsdWUiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0seyJrZXkiOiJpbmZvX2RhdGUiLCJ2YWx1ZSI6IjIwMjYtMDYtMDEifV0=
```

```json
{
  "data": {
    "id": 2000000010448528,
    "partitioning": [
      {
        "key": "catalog_path",
        "value": "edla_dev_cams_za_publish_rl.aq_eb_cams_tcsctot_qst_ah_tots"
      },
      {
        "key": "info_date",
        "value": "2026-06-01"
      }
    ],
    "author": "aqueduct"
  },
  "requestId": "29ce91a7-b668-41d2-a160-26402551fb0b"
}
```

2. Resolve **`data.id`** to its main flow:

```http
GET /api/v2/partitionings/2000000010448528/main-flow
```

```json
{
  "data": {
    "id": 3000000000123456,
    "name": "Custom flow #3000000000123456",
    "description": "",
    "fromPattern": false
  },
  "requestId": "985ce60c-8a95-4390-a2fa-776c3e8f07c6"
}
```

3. Page checkpoints with fixed bounds (`limit=100`, default is `10`):

```http
GET /api/v2/flows/3000000000123456/checkpoints?limit=100&offset=0&from=2026-06-01T00:00:00Z&to=2026-08-01T00:00:00Z&latest-first=true
```

```json
{
  "data": [
    {
      "id": "41ea35cd-6398-42ed-b6d3-a8d613176575",
      "name": "Source",
      "author": "aqueduct",
      "measuredByAtumAgent": true,
      "processStartTime": "2026-06-01T08:00:00Z",
      "processEndTime": "2026-06-01T08:00:01Z",
      "measurements": [
        {
          "measure": {
            "measureName": "count",
            "measuredColumns": []
          },
          "result": {
            "mainValue": {
              "value": "105",
              "valueType": "Long"
            },
            "supportValues": {}
          }
        }
      ],
      "partitioning": {
        "id": 2000000010448528,
        "partitioning": [
          {
            "key": "catalog_path",
            "value": "edla_dev_cams_za_publish_rl.aq_eb_cams_tcsctot_qst_ah_tots"
          },
          {
            "key": "info_date",
            "value": "2026-06-01"
          }
        ],
        "author": "aqueduct"
      },
      "properties": null
    }
  ],
  "pagination": {
    "limit": 100,
    "offset": 0,
    "hasMore": false
  },
  "requestId": "5869a6b5-80b1-441f-ad48-286ab3bbf59b"
}
```

Advance using `pagination.offset + pagination.limit` while `pagination.hasMore` is `true` (default `limit` across
paginated endpoints is `10`). A failed request must mark the extraction incomplete rather than empty. Set
`include-properties=true` only when checkpoint properties must be returned (property filtering does not require it, and
enrichment adds database queries; see §4).

### Recipe B: partitioning discovery and drill-down

`GET /api/v2/partitionings/search` discovers distinct **Atum partitioning IDs** (not collapsed business datasets) by
partial keys and/or checkpoint activity without needing a known flow or repeating partitionings that have many
checkpoints or flows.

#### Meaning of the time window

When a time bound is supplied, a partitioning qualifies if **at least one checkpoint** satisfies
`from <= process_start_time < to` (not only its latest-ever checkpoint; an old partitioning measured inside `[from, to)`
qualifies even if newer checkpoints exist after `to`, and a newly registered partitioning with no checkpoint in the
window does not). Omitting `to` leaves the upper bound unbounded (`+infinity`, not "until now"), so future-dated
checkpoints also qualify; supply an explicit fixed `to` when an audit window must end at a chosen cutoff. A key-only
search has no checkpoint requirement and includes partitionings without checkpoints.

| Interpretation                      | Timestamp or dimension                     | What it answers                                                                             |
|-------------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------|
| **Measured recently (implemented)** | Checkpoint `process_start_time`            | Which partitionings have measurements in the selected period?                               |
| Registered recently                 | Partitioning `created_at`                  | Which partitionings were registered in the period, including those without checkpoints?     |
| Reported recently                   | Checkpoint `created_at`                    | Which partitionings had checkpoints reported to Atum in the period, including late reports? |
| Business dates in the period        | A partition key such as `information_date` | Which dataset partitions represent that period, according to agreed producer semantics?     |

#### Search contract and response

Returns `PaginatedResponse[PartitioningWithIdDTO]` ([
`PartitioningWithIdDTO`](../model/src/main/scala/za/co/absa/atum/model/dto/PartitioningWithIdDTO.scala)):
`id`, registration `author` (not necessarily the latest checkpoint author), and ordered `partitioning` array. Search
does
not return `latestMatchingCheckpointTime`, checkpoint counts, or measurements. No matches (or an offset past the end)
returns HTTP `200` with `data: []` and `hasMore: false`, not `404`.

| Parameter      | Meaning                                                                                                                                                               |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `keys`         | Optional base64url-encoded UTF-8 JSON object of string values, e.g. `{"source":"CRM"}`. Exact string matching; all supplied pairs must match; empty string is literal |
| `from`         | Inclusive lower bound on checkpoint `process_start_time` (ISO-8601 with offset); required if `to` is supplied; alone, at most 365 days in the past                    |
| `to`           | Optional exclusive upper bound on `process_start_time`; must be later than `from`, and `to - from` must not exceed 365 days                                           |
| `latest-first` | Optional; `true` orders by partitioning ID descending (roughly most recently registered, not checkpoint recency); default `false` (ascending)                         |
| `limit`        | Page size, `1–1000` (default `10`)                                                                                                                                    |
| `offset`       | Nonnegative starting position (default `0`)                                                                                                                           |

HTTP `400` is returned if neither a non-empty key filter nor `from` is provided (`keys={}` alone is rejected; `keys={}`
with `from` is allowed), if `to` is given without `from`, if `from` alone is > 365 days old or `to - from` > 365 days,
on malformed base64/JSON, non-string key values, NUL (`\u0000`) characters, invalid dates, or invalid pagination.
Checkpoint timestamps are stored at microsecond precision; search preserves half-open comparisons for nanosecond bounds
(ceiling both bounds to microseconds and returning an empty page if the rounded window is empty).

Example (`eyJjYXRhbG9nX3BhdGgiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0=`
encodes `{"catalog_path":"edla_dev_cams_za_publish_rl.aq_eb_cams_tcsctot_qst_ah_tots"}`):

```http
# Key-only discovery, including partitionings without checkpoints
GET /api/v2/partitionings/search?keys=eyJjYXRhbG9nX3BhdGgiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0=&limit=100&offset=0

# Activity-only discovery, independent of partitioning registration time
GET /api/v2/partitionings/search?from=2026-10-01T00:00:00Z&to=2026-10-15T00:00:00Z&limit=100&offset=0

# Both filters (AND)
GET /api/v2/partitionings/search?keys=eyJzb3VyY2UiOiJDUk0ifQ==&from=2026-10-01T00:00:00Z&to=2026-10-15T00:00:00Z&limit=100&offset=0
```

#### Interaction flow

1. **Discover** via `/api/v2/partitionings/search` (results already include `id`, `author`, and `partitioning`, so do
   not fetch `/partitionings/{id}` per row).
2. **Inspect on selection** via `/api/v2/partitionings/{id}/checkpoints` with the same `from`/`to`.
3. **Widen history explicitly** when the user asks for a broader window.
4. **Explore flow or graph on demand** via `/partitionings/{id}/main-flow` (downstream context, can broaden scope),
   `/ancestors`, or `/lineage` (start with small `depth` and expand via `expandable*NodeIds`).

#### Query, pagination, and cost notes

`runs.search_partitionings` selects distinct qualifying partitionings **before** pagination (`LIMIT i_limit + 1` for
`hasMore`), so shared flows or multiple checkpoints never multiply results. Keys are business filters, not access
controls: search inherits existing API visibility. For bulk extraction across 1,000+ discovered partitionings, Recipe B
still requires one checkpoint query per partitioning; when a single known flow covers the scope, Recipe A avoids that
fan-out.

### Shared time and consistency contract

`from <= processStartTime < to` is a **measurement-time** window (`AtumContext.createCheckpoint` captures start time
right before measuring; manual checkpoints supply their own). Hold fixed bounds across all pages of a relative window.

All paginated endpoints provide **best-effort live pagination, not a point-in-time snapshot**:

- Checkpoint endpoints order by `process_start_time` (desc by default) then checkpoint UUID ascending; search orders by
  partitioning ID.
- Concurrent inserts, late reports inside `[from, to)`, or new flow links can shift offsets between pages (causing
  duplicates or omissions; deduplication removes repeats but cannot recover omissions).
- A repeatable, complete audit requires an external snapshot or watermark/late-arrival policy, not provided by this API.

### Explicit fallback for older servers

Against pre-v0.10.0 servers without `from`/`to`, page `latest-first=true` and filter individual checkpoints client-side:
skip `>= to`, keep `[from, to)`, and stop on the first timestamp `< from` (do not discard a whole boundary page). This
does not provide service-wide discovery where `/search` is absent.

---

## 4. Implemented change

### Server and Reader contract

Both `/api/v2/flows/{flowId}/checkpoints` and `/api/v2/partitionings/{id}/checkpoints` accept optional `from` and `to`
(ISO-8601 date-times with offset; omitting both leaves time unbounded; malformed or `from >= to` returns HTTP `400`),
`latest-first` (default `true`), `limit` (`1–1000`, default `10`), and `offset` (`>= 0`, default `0`). Unlike `/search`,
checkpoint endpoints pass `from`/`to` directly to Postgres without sub-microsecond ceiling rounding.

```sql
AND
    (i_from_time IS NULL OR C.process_start_time >= i_from_time)
    AND
    (i_to_time IS NULL OR C.process_start_time < i_to_time)
```

`FlowReader` and `PartitioningReader` expose these through `CheckpointFilter` in `getCheckpointsPage` (applications
manage their own paging; no `getAllCheckpoints` helper or Reader search method is provided).

### Partitioning search and deployment

`GET /api/v2/partitionings/search` is backed by [
`runs.search_partitionings`](../database/src/main/postgres/runs/V0.10.0.14__search_partitionings.sql):

| Forward migration                                                                                                                                           | Purpose                                                                                                         |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [`V0.10.0.12__partitionings_keys_index.ddl`](../database/src/main/postgres/runs/V0.10.0.12__partitionings_keys_index.ddl)                                   | Concurrent GIN index on `partitioning -> 'keysToValuesMap'`, using `jsonb_path_ops` for partial-key containment |
| [`V0.10.0.13__checkpoints_start_time_partitioning_index.ddl`](../database/src/main/postgres/runs/V0.10.0.13__checkpoints_start_time_partitioning_index.ddl) | Concurrent `(process_start_time, fk_partitioning)` index for selective service-wide activity windows            |
| [`V0.10.0.14__search_partitionings.sql`](../database/src/main/postgres/runs/V0.10.0.14__search_partitionings.sql)                                           | Validated search function with distinct-ID ordering (either direction) and bounded page lookahead               |

Deploy the forward search migrations before a server build exposing `/search`. Search is additive (a server rollback can
leave the function and indexes unused; if a concurrent index build fails, follow the recovery instructions in the DDL
header comments and [ADR 01 §7](01-lineage-capability.md#7-copy-policy-and-compatibility)).

### Index choice and performance limits

Within known partitionings/flows, checkpoints use a composite B-tree `checkpoints_idx2` on
`runs.checkpoints (fk_partitioning, process_start_time)` (built `CONCURRENTLY`), and property lookups use
`(fk_checkpoint, property_name)`. BRIN was not chosen because physical ordering by client-supplied `process_start_time`
is not guaranteed for late/backfilled reports.

Fetching a page is **one HTTP request**, not one database query: with `include-properties=true`, the server enriches
each checkpoint separately (100 checkpoints = 1 checkpoint query + 100 property queries). No end-to-end latency SLO is
asserted; benchmark representative flows, offsets, and `EXPLAIN (ANALYZE, BUFFERS)` plans before production rollout.

---

## 5. Partial parent lookup and optional grouping conventions

Exact lookup requires matching keys, values, and key order. Dropping `currency` from
`{catalog_path, info_date, currency}`
works **only if** `{catalog_path, info_date}` was explicitly registered; it is not a wildcard. Without a grouping
parent,
use containment search (`keys`).

### Grouping parent (rollup convention)

A cooperating producer can register a grouping parent and create per-value children via the Agent API:

```scala
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.model.types.basic.AtumPartitions

val rollup = AtumAgent.getOrCreateAtumContext(
  AtumPartitions(List("catalog_path" -> "example/dataset", "info_date" -> "2026-06-01"))
)
val usd = rollup.subPartitionContext(AtumPartitions("currency" -> "USD"))
val eur = rollup.subPartitionContext(AtumPartitions("currency" -> "EUR"))
```

`mergeWithParent = true` builds `parent ++ sub` (note: `AtumContext.subPartitionContext` throws
`PartitioningUpdateException` on overlapping keys, whereas `AtumAgent.getOrCreateAtumSubContext` lets `sub` win).
Consumers can resolve the grouping parent and query **its** main flow, bearing in mind that this creates **group
membership, not aggregate measurements or sibling-to-downstream lineage**: whole-dataset measurements must be produced
explicitly, and if `USD`, `EUR`, and consumer `C` all link to `R`, `USD`/`EUR` do not become ancestors of `C`.

### Containment search mechanics

`runs.search_partitionings` evaluates `(partitioning -> 'keysToValuesMap') @> i_keys` using the `jsonb_path_ops` GIN
index. Matching ignores key order in `keys` while returning each partitioning's stored key order (partitionings
differing only in key order keep distinct IDs and are returned separately). Search alone cannot resolve which
matches were actually consumed as parents by a downstream job — the consumer or external inventory must select the
consumed set.

---

## 6. Alternatives considered

- **Partitioning-first discovery (chosen for Recipe B):** avoids duplicates from checkpoints or overlapping flows
  without
  changing existing endpoints, though it does not provide bulk snapshot exports.
- **Query each partitioning:** avoids pulling sibling branches when an explicit set of ancestors/dates is needed, at the
  cost of fan-out requests and client-side merge/deduplication.
- **Overload `GET /api/v2/partitionings` with search parameters (rejected):** that path serves exact lookup
  (`?partitioning=...`) returning a single object or `404`, which Agent and Reader depend on. Overloading it would vary
  the response shape by parameter and break existing clients. `/search` is additive and unambiguous against numeric
  `/{id}`.
- **Dedicated graph endpoint:** delivered as `GET .../lineage`
  ([ADR 01 §10](01-lineage-capability.md#10-explicit-edges-and-the-lineage-schema-503-493));
  returns structure only, no measurements.
- **Dedicated audit export endpoint:** deferred (not rejected); consistent bulk exports require snapshot semantics that
  live paginated endpoints do not provide.

---

## 7. Open consumer decisions

| Open question                               | Consumer / deployment responsibility                                                                          |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Does `process_start_time` match the report? | Confirm measurement time (rather than business date or `created_at`) fits the audit question                  |
| Which scope is required?                    | Decide per report between one partitioning, its ancestors/parents, or its full downstream flow                |
| Is best-effort live pagination sufficient?  | A repeatable complete audit requires an external snapshot, watermark, or late-arrival policy                  |
| What performance is acceptable?             | Benchmark representative flow sizes, offsets, search selectivity, and `include-properties=true` load          |
| Who can read audit data?                    | Authentication, authorization, and read-only access policies are out of scope and must be provided externally |
