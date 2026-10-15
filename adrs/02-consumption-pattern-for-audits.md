# ADR 02 — Audit Consumption: Time-Windowed Measurements and Flow Membership

|                         |                                                                            |
|-------------------------|----------------------------------------------------------------------------|
| **Status**              | Proposed; implementation status is separate from acceptance and deployment |
| **Date**                | 2026-10-14                                                                 |
| **Affected components** | `server/`, `database/`; optional Scala application support in `reader/`    |
| **Implementation**      | §4 is implemented in source; §5 conventions are not delivered capabilities |

The implementation spans [#488](https://github.com/AbsaOSS/atum-service/pull/488) and
[#489](https://github.com/AbsaOSS/atum-service/pull/489). See §4 for prerequisites.

---

## 1. Context and scope

A typical audit question is:

> "For this dataset, show me the last 2 months of integrity measurements, which datasets they belong to,
> and which upstream datasets produced them."

These are distinct ideas:

1. **Measurements in a time window**, associated with their partitionings.
2. **Discovery of the relevant partitionings**, including different dates, executions or upstream branches.
3. **Exact input/output lineage**, including immediate parent-child edges and historical provenance.

This ADR delivers the first requirement **for an already identified scope**. Existing flow membership helps with
the second, subject to the restrictions below. It does not deliver the third. A collection of measurements tagged
with partitionings is not an exact lineage graph or proof that a particular source was consumed.

Audit consumers are expected to integrate through the **server REST API**. The Scala Reader is an optional client
for application code, not a prerequisite for audit consumption. As described in the [README](../README.md), Atum
captures control measurements; consumers remain responsible for comparisons, validation and reporting.

---

## 2. What the model and API provide

- **Partitioning identity is exact and ordered.** A partitioning identifies a dataset or subset of one. The API
  represents it as an ordered array of `{key, value}` objects. Storage uses a different JSONB representation:
  `{ "keys": [...], "version": 1, "keysToValuesMap": { ... } }`, with uniqueness over the whole value.
  Neither the exact-lookup endpoint nor a checkpoint time filter searches for other matching partitionings.
- **Flows store membership, not explicit edges.** Each partitioning created through the API gets its own main
  flow. Linking a child to a parent adds that child to the parent's current flows (`flows._add_to_parent_flows`). When
  links are established before descendants are created, this represents a
  downstream set. Linking an existing partitioning later does **not** propagate membership to its existing
  descendants. `PATCH .../ancestors` adds memberships; it does not remove the old parent's memberships.
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

---

## 3. REST consumption recipe

### Establish the scope first

The consumer must know a partitioning ID, its **complete ordered keys**, or an externally resolved set of them.

Choose the query according to the required scope:

| Intended scope                          | Existing API approach                                                                                             | Limitation                                                           |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| One exact partitioning                  | `/partitionings/{id}/checkpoints`                                                                                 | Does not include ancestors or descendants                            |
| Membership of a known main flow         | Resolve `/partitionings/{id}/main-flow`, then `/flows/{flowId}/checkpoints`                                       | Includes all recorded members, potentially other downstream branches |
| Target and its recorded ancestors only  | Exhaust `/partitionings/{id}/ancestors`; query checkpoints for the target and each distinct returned partitioning | More requests; not the exact original edge list                      |
| All dates/executions for a business key | Supply an external mapping, an agreed grouping parent, or explicit partitioning IDs                               | No business-key/subset search endpoint is implemented                |

If whole upstream-root flows are intentionally wanted, discover **all** candidate roots rather than choosing the
first ancestor. Exhaust the paginated ancestor lists and inspect candidates' ancestors until roots with no recorded
ancestors are reached; a target with no ancestors is itself a candidate root. Track visited IDs and report cycles or
inconsistent membership rather than silently choosing a root. The result can contain multiple roots, and their flows
can include sibling branches unrelated to the target. Late-link propagation limitations from §2 still apply.

When combining several flows, deduplicate by checkpoint ID and merge using the requested checkpoint order.
Each endpoint's `limit`/`offset` applies only to that query, not to the combined result.

### Worked example on a supporting server

This example assumes the following complete partitioning has already been registered, and its downstream flow is
the desired scope. It selects **measurement times**, not a range of `information_date` values.

```json
[
  {
    "key": "source",
    "value": "CRM"
  },
  {
    "key": "information_date",
    "value": "2026-06-01"
  }
]
```

1. Encode that JSON as UTF-8, then **base64url**, preserving array order:

```http
GET /api/v2/partitionings?partitioning=W3sia2V5Ijoic291cmNlIiwidmFsdWUiOiJDUk0ifSx7ImtleSI6InNuYXBzaG90X2RhdGUiLCJ2YWx1ZSI6IjIwMjYtMDYtMDEifV0=
```

An illustrative response (IDs and authors depend on the stored data):

```json
{
  "data": {
    "id": 2000000010448528,
    "partitioning": [
      {
        "key": "source",
        "value": "CRM"
      },
      {
        "key": "information_date",
        "value": "2026-06-01"
      }
    ],
    "author": "example-producer"
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
      "author": "example-producer",
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
            "key": "source",
            "value": "CRM"
          },
          {
            "key": "information_date",
            "value": "2026-06-01"
          }
        ],
        "author": "example-producer"
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

### Time and consistency contract

`from <= processStartTime < to` is a **measurement-time** window. The Agent's `createCheckpoint` sets the start
immediately before computing measurements; manually submitted checkpoints can provide their own times.
It is not necessarily the ETL job's start, the dataset's business date, or the database reporting time (`created_at`).
For "last two months", the consumer must choose calendar/time-zone semantics and hold both bounds fixed across pages.

The current endpoints provide **best-effort live pagination, not a point-in-time snapshot**. Their ordering is
process start time (descending by default), then checkpoint UUID ascending. This makes ties deterministic for
unchanged data, but concurrent matching inserts can still shift offsets and cause duplicates or omissions.
Late reports can fall inside a fixed `from`/`to` window, and new flow memberships can change the selected set.
Deduplication removes repeats; it cannot recover omissions.

For a repeatable, complete audit, a snapshot or an agreed finalization/watermark and late-arrival policy is a
prerequisite not supplied by this API. Record the selected scope and bounds with the report. Cursor pagination
could reduce shifting-boundary problems, but alone would not establish historical completeness.

### Explicit fallback for older servers

Before server-side time filtering, consumers could page latest-first and filter **individual checkpoints**:
skip timestamps at or after the fixed upper bound, retain those within the window, and stop on the first timestamp
below the lower bound. Do not discard a whole boundary page containing both matching and older checkpoints.
This is a deliberate compatibility fallback with the same live-pagination limitations, not evidence that an older
server honored `from`/`to`.

---

## 4. Implemented change

### Server and Reader contract

Both `/api/v2/flows/{flowId}/checkpoints` and `/api/v2/partitionings/{id}/checkpoints` accept optional `from` and
`to` ISO-8601 date-times with offsets. The lower bound is inclusive and the upper bound exclusive. Either bound
can be omitted; omitting both preserves the unbounded time scope. Malformed dates or `from >= to` return HTTP 400.
`latest-first` is supported by both endpoints and defaults to latest first. `limit` is 1–1000 (default 10);
`offset` is nonnegative (default 0).

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

## 5. Partial parent lookup and grouping conventions — proposed, not delivered

Exact lookup only works when the submitted keys, values and key order identify an existing partitioning.
Dropping `currency` from `{catalog_path, info_date, currency}` is valid **only if** the resulting
`{catalog_path, info_date}` partitioning exists. It is not a wildcard query over all currencies.
If only per-currency or per-date partitionings exist and their values are unknown, an external inventory or new
search capability is needed.

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

### Containment search — not implemented

A possible future implementation is `runs.get_partitionings_by_keys(...)` with
`WHERE partitioning -> 'keysToValuesMap' @> i_keys`, backed by a `jsonb_path_ops` GIN index on that sub-object.
The public route could be `GET /api/v2/partitionings/search?keys=<base64url partial JSON>&limit=&offset=`. **Neither
that function, index nor route is provided by this change.** The partial-object wire format would need
its own contract; it must not be confused with the ordered-array exact-lookup format.

The existing model can add memberships under multiple parents, but search alone cannot determine those parents.
Ordering, pagination, key validation and linkage behaviour remain design work for this extension.

---

## 6. Alternatives considered

- **Query each partitioning:** unnecessary fan-out when the required scope is exactly a flow's membership, but
  appropriate when an explicitly selected set of ancestors/dates must be queried without including sibling branches.
  Consumers must merge, deduplicate and order the results.
- **A dedicated audit export/graph endpoint:** deferred, not rejected as redundant. Existing endpoints return
  stored measurements and membership; exact graph structure or consistent exports need additional semantics.

---

## 7. Consequences and open decisions

| Question                                                                         | Position                                                                                                                     |
|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Which time does the implemented filter use?                                      | Checkpoint `process_start_time`, not dataset business date or `created_at`. Consumers must confirm this matches their report |
| How is a business dataset mapped to all relevant partitionings?                  | Consumer/inventory prerequisite; generic discovery is not implemented                                                        |
| Does the report want one partitioning, its ancestors, or whole downstream flows? | Consumer scope decision; these are different sets                                                                            |
| Is best-effort live extraction sufficient?                                       | Must be explicit. A repeatable complete audit requires additional snapshot/finalization and late-arrival guarantees          |
| Is the original parent-child graph required?                                     | Not delivered; explicit edges and provenance remain separate work                                                            |
| What performance is acceptable?                                                  | No SLO asserted; measure representative flow sizes, offsets and property enrichment before deployment                        |
| Who can read audit data?                                                         | Authorization/read-only access policy is out of scope here and must be provided                                              |
