# ADR 02 — Audit Consumption: Time-Windowed Measurements and Flow Membership

|                         |                                                                                                          |
|-------------------------|----------------------------------------------------------------------------------------------------------|
| **Status**              | Proposed; implementation status is separate from acceptance and deployment                               |
| **Date**                | 2026-10-14                                                                                               |
| **Revised**             | 2026-10-15                                                                                               |
| **Affected components** | `server/`, `database/`; optional Scala application support in `reader/`                                  |
| **Implementation**      | Time filtering and partitioning search are implemented in source; grouping remains a producer convention |

Checkpoint filtering spans [#488](https://github.com/AbsaOSS/atum-service/pull/488) and
[#489](https://github.com/AbsaOSS/atum-service/pull/489). Partitioning search is a separate follow-up.
Implementation in source does not establish release or deployment availability; see §4 for prerequisites.

---

## 1. Context and scope

A typical audit question is:

> "For this dataset, show me the last 2 months of integrity measurements, which datasets they belong to,
> and which upstream datasets produced them."

These are distinct ideas:

1. **Measurements in a time window**, associated with their partitionings.
2. **Discovery of the relevant partitionings**, including different dates, executions or upstream branches.
3. **An input/output lineage**, eventually relevant to relationships between control measures.

Recipe A delivers the first requirement **for an already identified scope**. Recipe B adds partitioning-first
discovery by partial keys and/or checkpoint activity for audit and UI consumers that do not already know that
scope. Note on the third requirement: a collection of measurements tagged with partitionings is not an exact lineage
graph or proof that a particular source was consumed, it is just a flow membership.

Audit consumers are expected to integrate through the **server REST API**. The Scala Reader is an optional client
for application code (system-to-system integration), not a prerequisite for audit consumption. As described in
the [README](../README.md), Atum captures control measurements; consumers remain responsible for comparisons, validation
and reporting.

---

## 2. What the model and API provide

- **Partitioning identity is exact and ordered.** A partitioning identifies a dataset or subset of one. The API
  represents it as an ordered array of `{key, value}` objects. Storage uses a different JSONB representation:
  `{ "keys": [...], "version": 1, "keysToValuesMap": { ... } }`, with uniqueness over the whole value.
  Neither the exact-lookup endpoint nor a checkpoint time filter searches for other matching partitionings.
- **Flows store membership, not explicit edges.** Each partitioning created through the API gets its own main
  flow. Every v2 linking path (`POST /api/v2/partitionings` with zero to many parents and `PATCH .../ancestors`) goes
  through one database function, `flows._link_to_parents`. It rejects a self-link or a cycle before anything is
  written (`409`) and adds the child **and everything already below it** (the members of the child's own main flow)
  to every flow that contains a parent, so the resulting membership does not depend on the order in which links are
  made. A redundant link (for example also linking a grandparent) is a no-op. `PATCH .../ancestors` adds
  memberships; it does not remove the old parent's memberships. One global advisory lock is taken only when
  something has to be written; re-linking an existing ancestor takes a lock-free fast path.
- **Ancestors are not only top-level roots.** `GET /api/v2/partitionings/{id}/ancestors` returns the primary
  partitionings of inherited flows, excluding the queried partitioning itself. Since intermediate partitionings
  have main flows too, the response can include both intermediate ancestors and top-level roots. It does not mark
  immediate parents or identify a unique root.
- **Flow checkpoints are paginated measurements plus partitioning identity.**
  `GET /api/v2/flows/{flowId}/checkpoints` returns one item per checkpoint, with nested measurements and a
  `partitioning` containing its ID, ordered keys and author. The underlying SQL returns one row per measurement;
  the server groups these rows into checkpoints. Neither representation includes parent-child edges.

For a producer chain created in order:

```text
Links established: A -> B -> C
mainFlow(A): {A, B, C}
mainFlow(B): {B, C}
mainFlow(C): {C}
ancestors(C): {A, B}  -- B is not a top-level root
```

The API exposes membership/ancestry information, but cannot recover the exact original direct links, redundant
links or their provenance. `GET /api/v2/flows/{flowId}/partitionings` lists membership without measurements; it is
not a graph endpoint. Use it when the report must include members with no checkpoints in the selected window.
Membership itself is not filtered or versioned by the checkpoint time window.

### Linking guarantees and limits

- **One idempotent create/link call.** `POST /api/v2/partitionings` takes zero to many parents
  (`parentPartitioningIds`, at most 100 distinct). `201` = created, `200` = already existing (same body shape; the
  stored `created_by` is returned), `404` = unknown parent, `409` = dependency cycle only. Parent measure
  definitions are copied only if `copyMeasureDefinitions` is `true` (default `false`) and only when the call creates
  a new partitioning; additional data is never copied by `POST`.
- **`PATCH` copy flags.** On `PATCH .../ancestors` (both flags required) `copyAdditionalData: true` **overwrites** the
  child's additional-data values for keys that also exist on the parent; `copyMeasurements: true` copies only the
  definitions the child does not have yet (on a conflict the child's own definition wins).
- **Sound for links created by 1.1.** Every linking path checks for cycles before writing and propagates the child
  and its existing descendants, so a cycle cannot be created and descendants are not left out.
- **No backfill.** Links written by earlier server versions (v0.6 to v0.10), whose late links did not propagate to
  existing descendants, are not repaired automatically. They can be detected with the read-only diagnostics in
  [`database/README.md`](../database/README.md#diagnostics-read-only) and repaired by linking the missing member
  under the flow's primary partitioning.
- **Ancestors are not direct parents.** The ancestors list holds the primary partitionings of all inherited flows,
  intermediate ones included; immediate parents are not marked. `GET .../ancestors` of a partitioning without
  ancestors returns `200` with an empty list.
- **Known race with the deprecated v1 create.** The deprecated v1 create path takes no lock, so it can race a v2
  re-link and lose a descendant. This is a documented limitation (follow-up), not fixed in 1.1.

---

## 3. REST consumption recipes

The two recipes serve different entry points:

| Recipe                                         | Starting point                                                                  | Status                                                                  |
|------------------------------------------------|---------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| **A: query a known scope**                     | An explicitly selected partitioning or a set of known partitionings             | Uses existing endpoints with §4's time filtering                        |
| **B: discover partitionings, then drill down** | Partial partitioning keys and/or an activity window, without known partitioning | Uses the search endpoint; subsequent drill-down uses existing endpoints |

### Recipe A: query a known scope

#### Establish the scope first

The consumer must know a partitioning ID, its **complete ordered keys**, or an externally resolved set of them.

Choose the query according to the required scope:

| Intended scope                          | Existing API approach                                                                                             | Limitation                                                                |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| One exact partitioning                  | `/partitionings/{id}/checkpoints`                                                                                 | Does not include ancestors or descendants                                 |
| Membership of a known main flow         | Resolve `/partitionings/{id}/main-flow`, then `/flows/{flowId}/checkpoints`                                       | Includes all recorded members, potentially other downstream branches      |
| Target and its recorded ancestors only  | Exhaust `/partitionings/{id}/ancestors`; query checkpoints for the target and each distinct returned partitioning | More requests; not the exact original edge list                           |
| All dates/executions for a business key | Search by partial keys, optionally narrowed by checkpoint activity (Recipe B)                                     | Key equality only; no business-date range or automatic lineage resolution |

If whole upstream-root flows are intentionally wanted, discover **all** candidate roots rather than choosing the
first ancestor. Exhaust the paginated ancestor lists and inspect candidates' ancestors until roots with no recorded
ancestors are reached; a target with no ancestors is itself a candidate root. Track visited IDs and report cycles or
inconsistent membership rather than silently choosing a root. The result can contain multiple roots, and their flows
can include sibling branches unrelated to the target. Links created before 1.1 can have incomplete membership (see
"Linking guarantees and limits" in §2); the diagnostics there detect it.

When combining several flows, deduplicate by checkpoint ID and merge using the requested checkpoint order.
Each endpoint's `limit`/`offset` applies only to that query, not to the combined result.

#### Worked example on a supporting server

This example assumes the following complete partitioning has already been registered, and its downstream flow is
the desired scope. It selects **measurement times**, not a range of `info_date` values.

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

1. Encode that JSON as UTF-8, then **base64url**, preserving array order:

```http
GET /api/v2/partitionings?partitioning=W3sia2V5IjoiY2F0YWxvZ19wYXRoIiwidmFsdWUiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0seyJrZXkiOiJpbmZvX2RhdGUiLCJ2YWx1ZSI6IjIwMjYtMDctMDEifV0=
```

An illustrative response (IDs and authors depend on the stored data):

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

2. Read **`data.id`**, then resolve its main flow:

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

3. Read the flow's **`data.id`**, choose fixed bounds once, and fetch pages:

```http
GET /api/v2/flows/3000000000123456/checkpoints?limit=100&offset=0&from=2026-06-01T00:00:00Z&to=2026-08-01T00:00:00Z&latest-first=true
```

Example final page containing one checkpoint:

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

Process `data`; if `pagination.hasMore` is true, request the next page at
`pagination.offset + pagination.limit`, retaining the same scope, filters and order. Stop when `hasMore` is false.
This rule also applies to ancestor and flow-partitioning lists. Their default page size is 10, not "all".
A failed request must leave the extraction explicitly incomplete, not be reported as an empty or complete result.

Set `include-properties=true` only when checkpoint properties are needed, for example an execution identifier.
Property filtering does not itself require properties to be returned. Properties add database work; see §4.

### Recipe B: partitioning discovery and drill-down

The entry question is: "Show me partitionings with measurement activity in
the last two weeks, then let me choose which ones to inspect." `GET /api/v2/partitionings/search` supports this
query and optional partial-key filtering. This avoids needing a known flow and avoids
returning a partitioning repeatedly merely because it has many checkpoints or belongs to several flows.

This is discovery of distinct **Atum partitioning IDs**, not distinct business datasets. Different dates,
currencies or executions can legitimately have different IDs. Collapsing these into a dataset-level row would
require a separate grouping contract.

#### Meaning of the time window

When a time bound is supplied, the criterion is **at least one checkpoint measured within the window**:
return a partitioning once if any of its checkpoints satisfies `from <= process_start_time < to`.
An old partitioning measured yesterday must qualify; a newly registered partitioning without a matching checkpoint
must not. An empty activity result therefore does not establish that no partitionings were registered.
Activity search requires both bounds and permits a window of at most 365 days. A key-only search has no
checkpoint-activity requirement and can return partitionings without checkpoints.

Other interpretations are not supported time filters and must remain distinct:

| Interpretation                      | Timestamp or dimension                     | What it answers                                                                             |
|-------------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------|
| **Measured recently (implemented)** | Checkpoint `process_start_time`            | Which partitionings have measurements in the selected period?                               |
| Registered recently                 | Partitioning `created_at`                  | Which partitionings were registered in the period, including those without checkpoints?     |
| Reported recently                   | Checkpoint `created_at`                    | Which partitionings had checkpoints reported to Atum in the period, including late reports? |
| Business dates in the period        | A partition key such as `information_date` | Which dataset partitions represent that period, according to agreed producer semantics?     |

For a historical window, eligibility must use **any matching checkpoint**, not the latest-ever checkpoint.
A partitioning with one checkpoint inside the window and another after `to` still qualifies.
The consumer chooses fixed bounds once and keeps them for subsequent pages and drill-down.

#### Response

The endpoint returns a `PaginatedResponse` containing the existing
[`PartitioningWithIdDTO`](../model/src/main/scala/za/co/absa/atum/model/dto/PartitioningWithIdDTO.scala):
`id`, `author` and `partitioning` (the public ordered array of key/value objects, not the storage JSONB envelope).
These contents let a UI label each result without a second request per row. An IDs-and-authors-only response would
usually defer necessary work to the consumer; a compact projection should be driven by an actual payload need.

`author` means the partitioning's registration author, not necessarily the author of its recent checkpoints or
its business owner.

An optional future summary is `latestMatchingCheckpointTime`: the maximum process start time **among the
checkpoints matching this query**, not the latest overall. It can explain why a result appears and support a
recency-oriented list. It is not a field of the current DTO and would need an explicit response extension.
It is not returned by search. Checkpoint counts and embedded measurements are also not part of the discovery response.

#### Search request contract

| Parameter | Meaning                                                                                                                                             |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `keys`    | Optional base64url-encoded UTF-8 JSON object of string values, e.g. `{"source":"CRM"}`. All supplied pairs must match; other keys are unconstrained |
| `from`    | Inclusive lower bound on checkpoint process start time, as an ISO-8601 date-time with an offset; required with `to`                                 |
| `to`      | Exclusive upper bound on the same timestamp; required with `from`, must be later than `from`, and the window must not exceed 365 days               |
| `limit`   | Page size, 1–1000; default 10                                                                                                                       |
| `offset`  | Nonnegative starting position; default 0                                                                                                            |

At least one non-empty key filter or a complete time window is required. A request with neither, with only one
time bound, or with only `keys={}` (encoded), returns HTTP 400. An empty key object combined with a complete time
window is valid and adds no key restriction.
Malformed base64/JSON, non-string key values, NUL characters (`\u0000`) in keys or values, invalid date-times and
invalid pagination also return HTTP 400.
Empty string values are valid exact values, not wildcards.
Checkpoint timestamps are stored at microsecond precision. Search preserves the half-open comparisons when
nanosecond bounds are supplied; a valid window containing no representable timestamp returns an empty page.

For example, `eyJjYXRhbG9nX3BhdGgiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0=`
encodes `{"catalog_path":"edla_dev_cams_za_publish_rl.aq_eb_cams_tcsctot_qst_ah_tots"}`:

```http
# Key-only discovery, including partitionings without checkpoints
GET /api/v2/partitionings/search?keys=eyJjYXRhbG9nX3BhdGgiOiJlZGxhX2Rldl9jYW1zX3phX3B1Ymxpc2hfcmwuYXFfZWJfY2Ftc190Y3NjdG90X3FzdF9haF90b3RzIn0==&limit=100&offset=0

# Activity-only discovery, independent of partitioning registration time
GET /api/v2/partitionings/search?from=2026-10-01T00:00:00Z&to=2026-10-15T00:00:00Z&limit=100&offset=0

# Both filters: the key condition AND at least one matching checkpoint
GET /api/v2/partitionings/search?keys=eyJzb3VyY2UiOiJDUk0ifQ==&from=2026-10-01T00:00:00Z&to=2026-10-15T00:00:00Z&limit=100&offset=0
```

Results are ordered by **partitioning ID ascending**, not by the latest checkpoint or business date.
Advance using `pagination.offset + pagination.limit` while `pagination.hasMore` is true. No matches, including
an offset beyond the result set, return HTTP 200 with empty `data` and `hasMore=false`, not HTTP 404.

#### Interaction

1. **Discover:** request a page from `/api/v2/partitionings/search` using keys, time bounds, or both.
   The existing exact-lookup `GET /api/v2/partitionings?partitioning=...` contract is unchanged.
2. **Inspect on selection:** request `/api/v2/partitionings/{id}/checkpoints` with the same `from` and `to`,
   paging checkpoints independently. Measurements are already nested in each checkpoint.
   Because the list includes the partitioning's contents, `/partitionings/{id}` is not normally needed just to
   render the detail view. Fetching every result's details eagerly would defeat the UI's lazy-loading benefit.
3. **Expand history explicitly:** let the user widen or remove the checkpoint window, making that scope change
   visible rather than silently loading all history.
4. **Explore related activity on demand:** resolve `/partitionings/{id}/main-flow`, then page its partitionings
   or checkpoints. Main-flow membership is downstream context and can broaden the scope substantially;
   `/partitionings/{id}/ancestors` is a separate option for upstream context. Membership lists themselves are not
   time-filtered; a checkpoint timeline can retain the selected window. Present this as flow membership or a
   related-activity timeline, not a parent-child graph or evidence of causality.

#### Query, pagination and cost requirements

The database selects **qualifying partitionings before pagination**, returning at most `limit` results and using
one extra qualifying partitioning to determine `hasMore`. It does not page checkpoint rows and deduplicate
afterwards. Shared membership in multiple flows does not multiply discovery results.

ID ordering provides a deterministic, unique order for unchanged results. Offset pagination matches existing
endpoints. Activity search requires a complete window of at most 365 days. Even a two-week window can match many
partitionings, so results remain paginated.

The existing `(fk_partitioning, process_start_time)` index serves known-partitioning queries. A service-wide
activity search has a different access pattern; search-specific index support is described in §4.
Returning fewer entities does not by itself make the database query cheap. Keys are business filters, not access
controls: search inherits the deployment's existing API visibility and adds no authorization boundary.

This recipe is particularly useful for a UI that opens only selected results. It is not automatically the cheapest
bulk audit extraction: retrieving measurements for 1,000 discovered partitionings still needs at least 1,000
checkpoint requests, potentially with further pages. When the required scope is already a known flow, Recipe A
may avoid that fan-out. A batch/export capability would be separate work.

Activity-based discovery is also distinct from the key-containment search in §5. It finds partitionings with
matching checkpoint activity, but does not resolve a business key or discover inactive grouping parents.
Both capabilities share the search endpoint, with independent filters combined using AND.

### Shared time and consistency contract

`from <= processStartTime < to` is a **measurement-time** window. The Agent's `createCheckpoint` sets the start
immediately before computing measurements; manually submitted checkpoints can provide their own times.
It is not necessarily the ETL job's start, the dataset's business date, or the database reporting time (`created_at`).
For relative windows such as "last two weeks" or "last two months", the consumer must choose calendar/time-zone
semantics and hold both bounds fixed across pages.

The current checkpoint endpoints provide **best-effort live pagination, not a point-in-time snapshot**. Their
ordering is process start time (descending by default), then checkpoint UUID ascending. This makes ties deterministic
for
unchanged data, but concurrent matching inserts can still shift offsets and cause duplicates or omissions.
Late reports can fall inside a fixed `from`/`to` window, and new flow memberships can change the selected set.
Deduplication removes repeats; it cannot recover omissions.

Recipe B has the same live-data issue: late checkpoints can make an existing partitioning newly eligible between
pages, shifting offsets even though IDs themselves do not change. Selecting distinct IDs does not freeze eligibility.

For a repeatable, complete audit, a snapshot or an agreed finalization/watermark and late-arrival policy is a
prerequisite not supplied by this API. Record the selected scope and bounds with the report. Cursor pagination
could reduce shifting-boundary problems, but alone would not establish historical completeness.

### Explicit fallback for older servers

Before server-side time filtering, consumers could page latest-first and filter **individual checkpoints**:
skip timestamps at or after the fixed upper bound, retain those within the window, and stop on the first timestamp
below the lower bound. Do not discard a whole boundary page containing both matching and older checkpoints.
This is a deliberate compatibility fallback with the same live-pagination limitations, not evidence that an older
server honored `from`/`to`. It applies to querying an already known scope; it does not supply service-wide discovery
on servers without the search endpoint.

---

## 4. Implemented change

### Server and Reader contract

Both `/api/v2/flows/{flowId}/checkpoints` and `/api/v2/partitionings/{id}/checkpoints` accept optional `from` and
`to` ISO-8601 date-times with offsets. The lower bound is inclusive and the upper bound exclusive. Either bound
can be omitted; omitting both preserves the unbounded time scope. Malformed dates or `from >= to` return HTTP 400.
`latest-first` is supported by both endpoints and defaults to latest first. `limit` is 1–1000 (default 10);
`offset` is nonnegative (default 0). The partitioning **search** endpoint is stricter: it requires both bounds
for activity search and caps their duration at 365 days.

The database functions apply the same window to both checkpoint selection and the `has_more` calculation:

```sql
AND
    (i_from_time IS NULL OR C.process_start_time >= i_from_time)
    AND
    (i_to_time IS NULL OR C.process_start_time < i_to_time)
```

`FlowReader` and `PartitioningReader` expose these options through `CheckpointFilter` in `getCheckpointsPage`.
The reader resolves identifiers and returns one page; it does not discover business-key matches, select upstream
roots, or collect all pages. No `getAllCheckpoints` helper is provided. Scala applications control their own paging;
REST audit consumers do not need the Reader.

### Partitioning search and deployment

`GET /api/v2/partitionings/search` is a separate REST operation, backed by `runs.search_partitionings`.
It combines optional key containment and checkpoint-time selection as specified in Recipe B. Search results reuse
the public partitioning DTO; no Reader search helper or new UI is included.

| Forward migration                                                                                                                                       | Purpose                                                                                                         |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [`V0.8.0.6__partitionings_keys_index.ddl`](../database/src/main/postgres/runs/V0.8.0.6__partitionings_keys_index.ddl)                                   | Concurrent GIN index on `partitioning -> 'keysToValuesMap'`, using `jsonb_path_ops` for partial-key containment |
| [`V0.8.0.7__checkpoints_start_time_partitioning_index.ddl`](../database/src/main/postgres/runs/V0.8.0.7__checkpoints_start_time_partitioning_index.ddl) | Concurrent `(process_start_time, fk_partitioning)` index for selective service-wide activity windows            |
| [`V0.8.0.8__search_partitionings.sql`](../database/src/main/postgres/runs/V0.8.0.8__search_partitionings.sql)                                           | Validated search function with distinct-ID ordering and bounded page lookahead                                  |

The existing partitioning-leading checkpoint index remains useful for known-partitioning searches. The new
time-leading index provides another access path, not a guarantee of constant-cost discovery. Indexes also add
storage and write overhead; monitor query plans against the deployment's actual data distribution.

Deploy the forward search migrations before a server build exposing the endpoint. Existing lookup/checkpoint
operations are not renamed or removed. Search is additive: reverting the server can leave its unused SQL function
and indexes in place without changing stored data. If removing those database objects, first remove all server
callers, then drop only the search function and its dedicated indexes; do not roll back unrelated migrations or
edit previously applied migration files. If a concurrent index build fails, drop its invalid index concurrently,
run `flywayRepair`, then rerun the migration; do not edit the migration file.
`flywayRepair` is acceptable here only because it removes the failed history row of a migration that did not complete; never use it to silence a checksum mismatch (see `../database/README.md`, "Resetting a development database").
Concurrent index migrations include
the affected index names.

### Index choice and performance limits

The chosen index is a composite B-tree on `runs.checkpoints (fk_partitioning, process_start_time)`
(`checkpoints_idx2`), built `CONCURRENTLY`. It supports time-range access within partitionings for both checkpoint
endpoints. Property lookups additionally use `(fk_checkpoint, property_name)`. A BRIN index is not part of this
implementation: physical ordering by client-supplied process time is not guaranteed for late/backfilled reports.

Fetching a page is **one HTTP request**, not necessarily one database query. With `include-properties=true`, the
server retrieves properties separately per checkpoint; 100 returned checkpoints can mean one checkpoint retrieval
plus 100 property retrievals. Bounds reduce the candidate set, but large flows, sorting, deep offsets and property
enrichment still have costs.

No end-to-end latency guarantee is established by this ADR. Capacity claims need reproducible data generation,
PostgreSQL version/configuration, `EXPLAIN (ANALYZE, BUFFERS)`, page offset, filter selectivity, cache conditions
and HTTP measurements with and without property enrichment. Unattached timing figures are not deployment evidence.

---

## 5. Partial parent lookup and optional grouping conventions

Exact lookup only works when the submitted keys, values and key order identify an existing partitioning.
Dropping `currency` from `{catalog_path, info_date, currency}` is valid **only if** the resulting
`{catalog_path, info_date}` partitioning exists. It is not a wildcard query over all currencies.
If only per-currency or per-date partitionings exist and their values are unknown, use containment search with the
known key/value pairs or an external inventory. Search does not require the producer to register a grouping parent.

### Grouping parent (sometimes called a rollup)

A cooperating producer can register a grouping parent and create per-value children with the existing Agent API.
This is an optional producer convention, not an automatically created representation:

```scala
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.model.types.basic.AtumPartitions

val rollup = AtumAgent.getOrCreateAtumContext(
  AtumPartitions(List("catalog_path" -> "example/dataset", "info_date" -> "2026-06-01"))
)
val usd = rollup.subPartitionContext(AtumPartitions("currency" -> "USD"))
val eur = rollup.subPartitionContext(AtumPartitions("currency" -> "EUR"))
```

`mergeWithParent = true` makes each child's keys `parent ++ sub`. Consumers can resolve the grouping parent
exactly and query **its** main flow to discover members and measurements. Other downstream members may also be
present, so the consumer still needs a rule for selecting the intended currency/date partitionings.
A group keyed by `info_date` does not automatically group other dates.

This establishes **group membership, not aggregate measurements or exact consumed-input lineage**. Whole-dataset
measurements must be explicitly produced. If `USD`, `EUR` and consumer `C` all link to group `R`, they share
`R`'s flow; `USD` and `EUR` do not become ancestors of `C` merely through that shared group.
Where consumed-input ancestry matters, identify and link the actual input partitionings, subject to the membership
limitations in §2. Persisting their exact direct edges and provenance requires separate change.

### Containment search

`runs.search_partitionings` matches `partitioning -> 'keysToValuesMap' @> i_keys`, with a `jsonb_path_ops` GIN index
on that sub-object. The REST `keys` parameter is a base64url-encoded JSON object, not the ordered array used by exact
lookup. Every supplied key/value pair must match; additional keys are allowed. Matching ignores the order of the
supplied keys but preserves each result's original ordered partitioning contents. Partitionings that differ only
in stored key order still have distinct IDs and are returned separately.

This is exact string-value containment, not substring matching, wildcard matching or a business-date range query.
Keys alone include matching partitionings without checkpoints; adding `from` or `to` requires at least one matching
checkpoint as well. Unknown keys or values yield an empty result. An empty result does not prove that a source
dataset is empty or that all source metadata has been registered.

The existing model can add memberships under multiple parents, but search alone cannot determine those parents.
The consumer must select the actual consumed set from zero, one or many matches. Automatic linkage and "latest"
selection policies remain separate work; search does not arbitrarily choose one parent.

---

## 6. Alternatives considered

- **Partitioning-first discovery:** selected for browsing activity without a known scope (Recipe B). It avoids
  duplicates from checkpoint multiplicity or overlapping flows, but does not replace known-scope queries or provide
  a bulk extraction/snapshot protocol.
- **Query each partitioning:** unnecessary fan-out when the required scope is exactly a flow's membership, but
  appropriate when an explicitly selected set of ancestors/dates must be queried without including sibling branches.
  Consumers must merge, deduplicate and order the results.
- **Search via query parameters on `GET /api/v2/partitionings`:** rejected for v2. Filtering the collection
  resource is the conventional REST shape, but this path already serves exact lookup (`?partitioning=`), returning
  a single object or HTTP 404, and the Agent and Reader depend on that contract. Overloading it would make the
  response shape depend on parameters (single object vs. paginated list) and be awkward to describe in OpenAPI;
  converting it into a list is a breaking change for deployed clients. The separate `/search` operation is
  additive, and it cannot be confused with `/{id}` because IDs are numeric. A future API version may unify both
  as one filtered, paginated collection, with exact lookup as one filter.
- **A dedicated audit export/graph endpoint:** deferred, not rejected as redundant. Existing endpoints return
  stored measurements and membership; exact graph structure or consistent exports need additional semantics.

---

## 7. Consequences and open decisions

| Question                                                                         | Position                                                                                                                                             |
|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Which time does the implemented filter use?                                      | Checkpoint `process_start_time`, not dataset business date or `created_at`. Consumers must confirm this matches their report                         |
| What does "recent partitionings" mean in Recipe B?                               | At least one checkpoint measured in the window. Registration time, reporting time and business dates are distinct semantics                          |
| What does discovery return?                                                      | Paginated `id`, registration `author` and ordered partitioning contents; no latest-checkpoint summary                                                |
| What ordering and pagination are used?                                           | ID ascending; limit 1–1000 (default 10), nonnegative offset (default 0); activity windows are at most 365 days                                       |
| Can search enumerate everything without a filter?                                | No: non-empty partial keys or both time bounds are required; otherwise HTTP 400                                                                      |
| How is a business dataset mapped to all relevant partitionings?                  | Search matches known partial keys; their business meaning, completeness and selection of the consumed set remain consumer/inventory responsibilities |
| Does the report want one partitioning, its ancestors, or whole downstream flows? | Consumer scope decision; these are different sets                                                                                                    |
| Is best-effort live extraction sufficient?                                       | Must be explicit. A repeatable complete audit requires additional snapshot/finalization and late-arrival guarantees                                  |
| Is the original parent-child graph required?                                     | Not delivered; explicit edges and provenance remain separate work                                                                                    |
| What performance is acceptable?                                                  | No SLO asserted; measure representative flow sizes, offsets and property enrichment before deployment                                                |
| Who can read audit data?                                                         | Authorization/read-only access policy is out of scope here and must be provided                                                                      |
