# ADR 03 — Consumption Pattern for Audit Queries (Time-Windowed Lineage & Measurements)

|                         |                                                                                                                  |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| **Status**              | Proposed                                                                                                         |
| **Date**                | 2026-09-20                                                                                                       |
| **Deciders**            | Atum Service maintainers, platform architects, audit/consumer stakeholders                                       |
| **Affected components** | `server/` + `database/` (optional time-filter enhancement only), `reader/` (consumption client — already exists) |

---

## 1. Context — what an auditor actually asks for

A typical audit question is:

> "For this dataset, show me the **last 2 months** of integrity measurements, and **how they relate to each
> other** — i.e. which measurement belongs to which dataset, and which dataset was produced from which upstream."

That is two needs:

1. A **time window** (e.g. the last 2 months).
2. The **relationships** between measurements — each measurement's dataset, and the lineage between datasets (what
   produced what).

This ADR describes how to satisfy both using the existing relational model and service API, and the one small
addition that makes the time window efficient.

---

## 2. What the relational model already gives us

Three facts about the current schema make this almost entirely a "use what's there" exercise:

- **A "flow" is one lineage chain.** When partitionings are linked parent→child (including across applications),
  they share **flow membership** (`flows.partitioning_to_flow`). So *one flow = one connected
  lineage graph* — exactly the "set of related datasets" an audit cares about.
- **One call returns every measurement in that chain, already tagged with its dataset.**
  `flows.get_flow_checkpoints(flowId, ...)` returns all checkpoints across the whole flow, and **every returned row
  carries the `id_partitioning` + `partitioning` JSON it belongs to** — plus `measure_name`, `measurement_value`,
  `checkpoint_start_time`, `checkpoint_end_time`, author, etc. That single call delivers *both* the measurements *and*
  their relationships (measurement→dataset; the shared flow ties datasets into one lineage graph). It is
  exposed as `GET /api/v2/flows/{flowId}/checkpoints`.
- **The time data already exists.** `runs.checkpoints.process_start_time` (and `process_end_time`) are stored, and
  `get_flow_checkpoints` already returns results **latest-first** (ordered by `process_start_time DESC`).

A ready-made client also already exists: the **`reader`** module's `FlowReader`, which does exactly the
partitioning → flow → paged-checkpoints walk described below.

```
        Flow  =  one lineage chain (one connected component)

   AQ dataset ─────▶ UU domain ─────▶ UU feed
       │                 │                │
   checkpoints       checkpoints      checkpoints
   (measures)        (measures)       (measures)

   GET /flows/{flowId}/checkpoints  ── returns ALL of these rows, each tagged
                                       with the partitioning it belongs to.
```

---

## 3. The consumption pattern (recipe)

Three steps, all on **today's** v2 API:

1. **Resolve the starting dataset → partitioning id.**
   `GET /api/v2/partitionings?partitioning=<base64-encoded partitioning JSON>` → `{ id }`. *(Resolving that starting
   point from a business key such as `catalog_path`)*
2. **Resolve its lineage chain → flow id.**
   `GET /api/v2/partitionings/{id}/main-flow` → `{ id: <flowId> }`.
3. **Pull all measurements across the chain, newest first, paged.**
   `GET /api/v2/flows/{flowId}/checkpoints?limit=100&offset=0&include-properties=true`.
   Each row = one measure of one checkpoint, **tagged with its partitioning**. That is the audit dataset:
   measurements + their dataset relationships across the whole lineage chain.

### Worked example (HTTP)

```http
# 1) dataset (partitioning JSON, base64-encoded) -> its id
GET /api/v2/partitionings?partitioning=eyJhdWxfZG9tYWluX3V1aWQiOiAiYTZ...   ->  { "id": 2000000010448528 }

# 2) id -> its flow (lineage chain)
GET /api/v2/partitionings/2000000010448528/main-flow                        ->  { "id": 3000000000123456 }

# 3) flow -> every measurement in the chain, latest first, one page at a time
GET /api/v2/flows/3000000000123456/checkpoints?limit=100&offset=0&include-properties=true
```

Each page item carries `checkpointStartTime`, `measureName`/`measurementValue`, and the `partitioning` it belongs
to — so relationships come for free.

### "Last 2 months" today (no server change needed)

Because results are latest-first, page step 3 and **stop at the first row older than the cutoff**:

```
keep the page while  checkpointStartTime >= now - 2 months
stop as soon as a row is older (all later rows are older too)
```

This works on the current API. Its only cost is over-fetching whole pages and re-implementing the cutoff in every
consumer.

### Pure lineage topology (optional)

If an audit wants the dataset graph *without* the measurements, use `GET /api/v2/flows/{flowId}/partitionings`
(all datasets in the chain) and/or `GET /api/v2/partitionings/{id}/ancestors` (a dataset's upstream roots).

---

## 4. The one gap, and the proposed change

**Gap:** there is **no server-side time filter** on any checkpoint endpoint today. `get_flow_checkpoints` filters
by `checkpoint-name` and `checkpoint-properties` only; "last 2 months" must be done client-side (above).

**Proposed change — minimal and backward-compatible** (mirrors the *existing* optional filters, so nothing breaks):

- **Database:** add two optional parameters to `flows.get_flow_checkpoints`
  (and, for the single-dataset case, `runs.get_partitioning_checkpoints`):

  ```sql
  -- new optional params, both default NULL (= no filter, current behaviour)
  i_from_time TIMESTAMP WITH TIME ZONE DEFAULT NULL,
  i_to_time   TIMESTAMP WITH TIME ZONE DEFAULT NULL
  -- ...added to the WHERE clause:
  AND (i_from_time IS NULL OR C.process_start_time >= i_from_time)
  AND (i_to_time   IS NULL OR C.process_start_time <  i_to_time)
  ```

- **API:** add optional `from` / `to` (ISO-8601) query params to `GET /api/v2/flows/{flowId}/checkpoints`
  (and `GET /api/v2/partitionings/{id}/checkpoints`):

  ```http
  GET /api/v2/flows/{flowId}/checkpoints?from=2026-06-01T00:00:00Z&to=2026-08-01T00:00:00Z&limit=100
  ```

- **Index:** add a supporting index on `runs.checkpoints (process_start_time)` — today the only index on that
  table is on `fk_partitioning`, so a large flow's time-window scan has nothing to lean on. A composite
  `(fk_partitioning, process_start_time)` also helps the single-partitioning endpoint.

With this, "last 2 months" is a single server-side query per page instead of client-side over-fetching, and the
cutoff logic lives in one place.

*(Minor, optional: the `/flows/{flowId}/checkpoints` endpoint does not currently expose `latest-first` — the DB
function has it and defaults to `TRUE`, which is what audits want anyway. Expose it only if ascending order is ever
needed.)*

---

## 5. Partial parent lookup — whole-dataset / "ignore a key"

A related consumer need (not audit-specific): **link to / load an upstream *parent* partitioning when you cannot
supply the full key.** Two triggers: you read the **whole** dataset without partition pruning (the upstream is
partitioned by `currency` and you want *all* currencies), or the key simply doesn't apply to you (the upstream
isn't partitioned by it at all).

It splits into two directions, and **only one is actually hard**:

- **Too many keys — "ignore one."** You computed `{catalog_path, info_date, currency}` but want to ignore `currency`.
  You control your own query, so just **drop the key and do the normal exact lookup**. Nothing new is needed.
- **Too few keys — "all currencies."** You only know `{catalog_path, info_date}`; the stored parents are
  `{…, currency: USD}`, `{…, currency: EUR}`, …, and you cannot enumerate the values. This is the one case that
  needs more than an exact match.

### Preferred: a *rollup* parent (no new Atum capability)

The relational model already represents "less-specific parent → more-specific child": `mergeWithParent = true`
makes `child = parent ++ sub`, and AQ already relies on it (a job-level parent whose children merely *add*
`catalog_path` for example). So a producer that partitions by `currency` registers a **rollup node** and the
per-value partitions beneath it, using the existing Agent API:

```scala
val rollup = AtumAgent.getOrCreateAtumContext(
  AtumPartitions("catalog_path" -> x, "info_date" -> y)) // the "whole dataset" node
val usd = rollup.subPartitionContext(AtumPartitions("currency" -> "USD")) // -> {..., currency: USD}
```

Consumption then stays **all exact-match**:

- **whole dataset** → exact-match the **rollup** `{catalog_path, info_date}` and link to it; every currency
  partition is reachable transitively through the flow (`.../ancestors`, `.../flows/{flowId}/checkpoints`).
- **one currency** → exact-match `{..., currency: USD}`.
- **ignore currency** → drop it → exact-match the rollup.

This is the most Atum-idiomatic answer: no new query capability, no schema change, and it reuses the parent-child
flow model, so lineage still connects the rollup to each partition.

### Fallback: containment search (only when no rollup is guaranteed)

The rollup only helps if the producer emits it. When a producer emits **only** per-value nodes and a consumer
reads the whole dataset, exact match cannot reach them. That single case is what a genuinely new **subset /
containment** lookup is for:

- **DB:**
  `runs.get_partitionings_by_keys(i_keys JSONB, i_limit, i_offset) … WHERE partitioning -> 'keysToValuesMap' @> i_keys`
  — containment must target the `keysToValuesMap` sub-object, because the stored value is the envelope
  `{ keys, version, keysToValuesMap }` (with an ordered `keys` array), **not** a flat map — backed by a
  `jsonb_path_ops` **GIN index** on `(partitioning -> 'keysToValuesMap')` (none exists today — the store is
  exact-match only, ADR 03 §5.4).
- **API:** `GET /api/v2/partitionings/search?keys=<base64 partial JSON>&limit=&offset=` (mirrors the existing
  base64-JSON convention of `GET /partitionings`).
- **0 / 1 / many results:** a partial key legitimately matches many parents — link to **all** of them (the flow
  model is many-to-many, so N parents need no schema change), or, for the least-selective `catalog_path`-only
  case, the most-recent — per ADR 03 §3.2's match-resolution strategy.

**Recommendation:** prefer the rollup convention; treat containment search as the fallback for heterogeneous or
uncooperative producers (and ad-hoc audits).

---

## 6. Bigger bets — conceptual changes to the model or service

Everything above works on today's schema. This section steps back and asks the question directly: *if we were
willing to change the relational model or the service, what would make lineage + audit consumption dramatically
easier?* These are **options to inform the roadmap, not commitments** — most are independent and can be adopted
incrementally. They are ordered cheapest-and-most-compatible first.

Two facts (both verified in the schema) frame the whole list:

- **A partitioning is an opaque, exact-match blob.** `runs.partitionings.partitioning` is a single `JSONB` under a
  `UNIQUE` B-tree over the *whole* value; that value is an **envelope** —
  `{ "keys": [...], "version": 1, "keysToValuesMap": { ... } }` — where `keys` is an *ordered array*. So identity
  is the entire structure (key order included), and there is **no way to query by an individual dimension** or by a
  key *subset* without a sequential scan.
- **Lineage is stored only as flow membership, not as edges.** `get_partitioning_ancestors` returns the *flow
  roots* a partitioning sits under (it joins `partitioning_to_flow -> flows -> fk_primary_partitioning`).
  The **immediate parent->child edge and the intermediate topology are not persisted** — only "belongs to a flow whose
  root is R." The true lineage *graph* therefore cannot be reconstructed from the DB today.

### Bet 1 — Make partition keys queryable (index, then optionally normalize)

- **Idea (two sizes).** *Small & backward-compatible:* add a `jsonb_path_ops` **GIN index on
  `(partitioning -> 'keysToValuesMap')`** plus a helper `get_partitionings_by_keys(...) WHERE
  partitioning -> 'keysToValuesMap' @> i_keys`. *Larger:* maintain a normalized child table
  `runs.partitioning_keys(fk_partitioning, key, value)` (a trigger keeps it in sync), or generated columns for the
  hot linkage keys (`catalog_path`, `info_date`).
- **Unlocks.** Subset / containment lookup ("all currencies"), query-by-dimension ("every partitioning where
  `currency = EUR`"), and partial-parent lookup (§5) become **indexed** SQL instead of scans. This is the single
  change that turns ADR 03 **Option C** and ADR 04 **§5's hard case** from "new subsystem" into "one index + one
  function."
- **Cost.** The GIN index is nearly free and fully backward-compatible (no write-path change). The normalized
  table costs a trigger, a backfill, and storage, and must be kept consistent with the JSONB.
- **Verdict.** **Do the GIN index first** — highest value-to-effort in the whole list. Promote to a normalized
  table only if dimension queries become a first-class, high-volume access pattern.

### Bet 2 — Make time a first-class checkpoint dimension

- **Idea.** The infrastructure behind §4: add a **BRIN index on `runs.checkpoints (process_start_time)`** (ideal
  for an append-only, time-ordered table and physically tiny), and consider native **range partitioning** of
  `checkpoints` by time once volume warrants. Pair with the `from`/`to` params from §4.
- **Unlocks.** Cheap time-window audits **at scale** — the literal §1 question ("last 2 months") answered
  server-side instead of by client over-fetch — and old partitions become trivially archivable.
- **Cost.** BRIN index: low and backward-compatible. Table partitioning: medium (migration), defer until needed.
- **Verdict.** **Do the BRIN index with §4.** It is the cheapest structural change that scales the core audit
  query.

### Bet 3 — Persist lineage as explicit edges (a real DAG)

- **Idea.** Add `runs.partitioning_edges(fk_parent, fk_child, created_by, created_at)` capturing the **immediate**
  parent->child relationship at link time. Keep `flows` / `partitioning_to_flow` as a denormalized reachability
  cache - or, longer term, *derive* it from edges.
- **Unlocks.** The exact **edge list** and immediate parent/child (impossible today, per the framing note above),
  arbitrary **N-hop traversal** via a recursive CTE, a true multi-parent DAG **with provenance** (who linked what,
  when), and a real graph endpoint (`nodes + edges`) for audits that must *show the lineage*, not just the set.
- **Cost.** One new table plus dual-maintenance with flows (medium), or a larger refactor if flows become a pure
  projection of edges (higher).
- **Verdict.** Recommend at minimum *recording* edges now (cheap, additive) even before building graph queries — the
  provenance is impossible to reconstruct later.

### Summary

**Recommended sequence.** Ship the two low-cost, backward-compatible indexes first — **Bet 1's GIN** and **Bet 2's
BRIN** (no producer changes, and together they unlock most of the audit + partial-lookup value). Start **recording
edges** (Bet 3) additively so provenance isn't lost.

---

## 7. Alternatives Considered

- **Client-side windowing only (status quo).** Page latest-first and stop at the cutoff. Zero server change;
  acceptable as an interim. Downside: over-fetches whole pages near the boundary, and every consumer must
  re-implement the cutoff.
- **Iterate per partitioning** (`get_partitioning_checkpoints` for each dataset). Rejected: the caller would first
  have to enumerate the flow's datasets and then fan out N calls, re-implementing what `get_flow_checkpoints`
  already does server-side in one call.
- **A new bespoke "audit export" endpoint.** Rejected for now: the flow-checkpoints endpoint plus a time filter
  already returns exactly the needed shape; a dedicated export can come later if a specific report format is
  required.

---

## 8. Consequences & Open Questions

- **Which timestamp is authoritative for "the last 2 months"?** `process_start_time` (when the data was *processed*) vs.
  `created_at` (when the checkpoint was *reported to Atum*). Recommendation: filter on `process_start_time`
  (business time), but confirm with the audit owners as for late/backfilled reports the two can differ.
- **Volume at scale.** A long-lived flow accumulates many checkpoints; the proposed index is what keeps a
  time-window audit query cheap.
- **Explicit parent -> child edges.** Flow membership gives the connected lineage *set*; if an audit needs the exact
  edge list (who is parent of whom), that is reconstructed today via `get_partitioning_ancestors` per node. A
  dedicated "flow graph" (nodes + edges) endpoint is a possible small future addition.
- **Access control** for audit consumers (read-only scopes/authorization) is out of scope here.
