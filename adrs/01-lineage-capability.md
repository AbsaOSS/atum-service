# ADR 01 — Lineage Capability: Partitioning Linking, Flow Membership and Explicit Edges

|                         |                                                                                                                                                                                                                                                                                                                              |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Status**              | Accepted; implemented on `master`, first released in v0.11.0                                                                                                                                                                                                                                                                 |
| **Date**                | 2026-11-04                                                                                                                                                                                                                                                                                                                   |
| **Revised**             | 2026-11-17 (explicit edges, `lineage` schema, graph API)                                                                                                                                                                                                                                                                     |
| **Affected components** | `database/`, `server/`, `model/`, `agent/`, `api-tests/`                                                                                                                                                                                                                                                                     |
| **Issue / PRs**         | [#475](https://github.com/AbsaOSS/atum-service/issues/475) / [#496](https://github.com/AbsaOSS/atum-service/pull/496) (multi-parent linking, breaking change), [#503](https://github.com/AbsaOSS/atum-service/pull/503) (explicit edges), [#493](https://github.com/AbsaOSS/atum-service/pull/493) (bounded graph traversal) |

This ADR records what the linking ("lineage") capability is, how it works, which alternatives were weighed and why the
final design looks the way it does. Sections 1–9 record the decision taken in #496 (closure-only storage). Section 10
records the follow-up in #503/#493, which adds explicit direct edges and a dedicated `lineage` schema on top of it
without changing any #496 decision.

---

## 1. Context and scope

Atum partitionings (datasets or subsets of them) can be related: a *child* partitioning is derived from one or more
*parent* partitionings (e.g. an ingestion tool creates a raw dataset, a curation job creates a downstream dataset
from it). Consumers (audits, UIs) want to ask "which partitionings belong together" and "which upstream partitionings
does this one come from".

State before #475 (released v0.10.0):

- The relation was stored only as **flow membership** (no edge table). A partitioning created through the API gets
  its own *main flow* (the flow whose primary partitioning it is). Linking a child under a parent adds the child to
  every flow that contains the parent.
- A child could have **one** parent at creation time (`parentPartitioning` in the v1 request) and could get another
  one afterwards through `PATCH /api/v2/partitionings/{id}/ancestors`.
- Creation with a parent also **copied measure definitions and additional data** from the parent to the child, and
  the agent decided between "create" and "link" through several round trips (GET, POST, PATCH).
- Nothing prevented a **cycle** through `PATCH`, and nothing defined what happens to the *descendants* of a child
  that is linked under a new parent.

Goals of the work: allow **0..N parents** on creation, make the link semantics **well defined, idempotent and cycle
free**, make copying of data **explicit and minimal**, and consolidate the API into one create path.

Non-goals of #496: an exact lineage graph. Flows store membership, not edges; a flow is "good enough" lineage when its
members are ordered by creation time. *(Revisited by #503: direct edges are now recorded for new links, see §10.)*
The model still does not prove that a partitioning really consumed another one; an edge records what the producer
declared.

---

## 2. Decision summary

| #   | Decision                                                                                                                                                                                                                                                                                                                                                                                                            |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D1  | One consolidated create: `POST /api/v2/partitionings` takes **0..N parents** (max 100 distinct, `400` beyond). It is **idempotent**: `201` created, `200` already existing (same `Location`, stored `author`). The legacy single `parentPartitioningId` key is still decoded.                                                                                                                                       |
| D2  | **One linking routine** for every v2 linking path (`lineage.link_to_parents`), shared by `POST` and `PATCH .../ancestors`.                                                                                                                                                                                                                                                                                          |
| D3  | **Cycle detection** before any write: a self-link or a parent that is already a descendant is `409` (DB status `32`).                                                                                                                                                                                                                                                                                               |
| D4  | **Flow of X = X + everything downstream of X** (closure). A newly created child joins every flow that contains its parent (this rule already existed).                                                                                                                                                                                                                                                              |
| D5  | **Descendants are never propagated.** Linking an *existing* partitioning that **already has descendants** under a new parent is **refused**: `409` (DB status `33`), nothing written. A newly created partitioning is always a leaf, so creation-time linking is unaffected.                                                                                                                                        |
| D6  | **Additional data is never copied** by `POST`. **Measure definitions** are copied only when `copyMeasureDefinitions` is `true` (default **`false`**; the one exception is a legacy v0.6 – v0.10 body with `parentPartitioningId` and no flag, which defaults to `true` to keep those agents' behaviour), only to a **newly created** child, as a distinct union over the parents (the child's own definitions win). |
| D7  | `PATCH .../ancestors` keeps its **explicit copy flags** and requires both (an absent flag is `400`). `copyAdditionalData=true` overwrites the child's same-named keys.                                                                                                                                                                                                                                              |
| D8  | **API v1 is deprecated** (Swagger `.deprecated()` + README), not removed. v1-only agents (≤ v0.5.x) are not used in practice, so no compatibility effort is spent there.                                                                                                                                                                                                                                            |
| D9  | **Deploy the server before agents.** Agents of the same or older version only; no new-agent/old-server shim.                                                                                                                                                                                                                                                                                                        |
| D10 | *(#503)* **Explicit direct edges** in `lineage.partitioning_edges`, written in the same transaction as the closure (dual write). Forward-only: no migration backfill. The closure stays authoritative for transitive reads and cycle/leaf checks. See §10.                                                                                                                                                               |
| D11 | *(#503)* **Dedicated `lineage` schema** for all lineage tables and functions, with a **direct cutover**: moved functions are dropped from their old schema, no compatibility wrappers. See §10.2.                                                                                                                                                                                                                   |
| D12 | *(#503, #493)* **Edge-based read API**: `GET .../parents`, `.../children`, `.../descendants` and `.../lineage` (bounded sub-graph). See §10.3.                                                                                                                                                                                                                                                                      |

---

## 3. Mechanics

### 3.1 The flow model

- Every partitioning has a **main flow** `F(A)` (the row in `flows.flows` with `fk_primary_partitioning = A`).
  `A` is a member of its own main flow.
- **Closure invariant:** `members(F(A)) = {A} ∪ descendants(A)`.
- **Ancestors of X** = the primary partitionings of the flows that contain `X`, excluding `X`
  (`GET /api/v2/partitionings/{id}/ancestors`). These are *all* ancestors (intermediate and root), not only the
  direct parents; the response does not mark direct parents or identify a unique root. Direct parents come from the
  edge table (`GET .../parents`, §10).
- Reads (ancestors, descendants, flow partitionings, flow checkpoints) are non-recursive lookups over this
  membership, which is why the invariant matters: they are correct only while it holds.

### 3.2 `lineage.link_to_parents(child, parents[], user)`

The only place that links an existing child under parents (introduced in #496 as `flows._link_to_parents`, moved and
renamed in #503). Order of work:

1. **Validate:** `i_by_user` not `NULL` (status `51`); normalise parents (`NULL`s and duplicates dropped); child exists
   (status `42`); every parent exists (status `41`, naming the missing ids).
2. **Self-link** (`child ∈ parents`): status `32`. Checked first, because the child is a member of its own flow and
   would otherwise look "already linked".
3. **Fast path, no lock:** if every parent is already an ancestor of the child (the child is in each parent's flow —
   e.g. re-declaring an existing parent or declaring a grandparent directly), the closure needs nothing. Only missing
   **edge** rows are inserted (`ON CONFLICT DO NOTHING`): status `11` if an edge was inserted/backfilled, `14` if
   nothing changed. This also holds for a child that has descendants, so re-running a job is idempotent and cheap. An
   already-established ancestor cannot be a descendant (that would already be a cycle) and its flow already contains the
   child's descendants, so the edge-only write is always safe.
4. **Slow path:** take **one global transaction-scoped advisory lock** (key `atum.flows.link_to_parents`, unchanged
   since #496); everything after it re-checks under a fresh snapshot (sound **only under READ COMMITTED**, the
   Hikari default).
5. **Cycle check:** is a parent a member of the child's flow, i.e. a descendant of the child? Status `32`, naming the
   offending ids.
6. **Leaf & parent-flow checks:** does the child's flow contain anything other than the child itself (status `33`, D5)?
   Does every parent have at least one flow membership (status `50`, data inconsistency)?
7. **Write:** insert the edges, then add **only the child** to every flow that contains a parent. Status `11` (or
   `14` if nothing was new).

Every non-OK status in `lineage.link_to_parents` is returned **before its first write**, because fa-db commits first
and inspects the status afterwards. *(Note: `runs.create_partitioning_with_parents` inserts a brand-new child
partitioning and its main flow before calling `link_to_parents`; `3x`/`4x` errors cannot trigger for a new child, but
if a parent has corrupt metadata triggering status `50`, the new child partitioning and flow are still committed.)*

### 3.3 Entry points and statuses

| Path                                    | Behaviour                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `runs.create_partitioning_with_parents` | Creates the partitioning or reuses the existing one, links all parents in the same transaction through the helper, copies measure definitions only if the child was created by the call and the flag is `true`. Statuses: `11` created (no parents), `12` created with parents, `13` parents registered on an existing partitioning (including edge backfill), `14` already present, `32` cycle, `33` existing partitioning has descendants and would get a new parent, `41` parent not found, `51` `i_by_user` is `NULL`. |
| `runs.update_partitioning_parent`       | Validates (`42` child, `41` parent), links through the helper (`32`, `33`, `50`, `51`), then applies the copy flags even when the link already existed: measure-definition copy is `ON CONFLICT DO NOTHING` (idempotent), whereas `copyAdditionalData=true` still overwrites child keys modified since the original link (a repeat is only a no-op when `copyAdditionalData=false`). |

HTTP mapping: `201`/`200` created/existing, `204` for `PATCH` (only a link/data no-op when already linked and
`copyAdditionalData=false`), `400` for more than 100
distinct parents or an absent `PATCH` flag, `404` for an unknown parent or child (nothing created), `409` for a
cycle, self-link, or "already has descendants". fa-db maps the 3x status class to a conflict and 4x to not found, so
status `33` needed no new server mapping, only messages, docs and a test.

### 3.4 Concurrency

- One **global** advisory lock, taken only when something must be written to the closure. There is no
  `lock_timeout`: a waiting writer holds one of at most 10 pooled connections.
- The lock-free **v1** create can race a v2 link of its parent under a new ancestor and then miss that ancestor's
  flow (only the brand-new child can be missed; no existing descendant is ever lost). No diagnostic or repair tool
  ships for this (§8).
- Callers must not hold a `FOR UPDATE` row lock when calling the helper (lock-order inversion).

---

## 4. Examples

Partitionings created by two tools, each one created **with its parents at creation time**, upstream before
downstream:

- Ingest (Aqueduct): `P1`, and `P1.1`, `P1.2`, `P1.3` with parent `P1`.
- Curate, process UU1: `P1.2.1`, `P1.2.2` with parent `P1.2`.
- Curate, process UU2: `P1.2.1.1` (parent `P1.2.1`), `P1.2.2.1` (parent `P1.2.2`).

### 4.1 Resulting flows and edges

| Flow                           | Members                                                  |
|--------------------------------|----------------------------------------------------------|
| F1                             | P1, P1.1, P1.2, P1.3, P1.2.1, P1.2.2, P1.2.1.1, P1.2.2.1 |
| F1.2                           | P1.2, P1.2.1, P1.2.2, P1.2.1.1, P1.2.2.1                 |
| F1.2.1                         | P1.2.1, P1.2.1.1                                         |
| F1.2.2                         | P1.2.2, P1.2.2.1                                         |
| F1.1, F1.3, P1.2.1.1, P1.2.2.1 | themselves only                                          |

A flow is "X plus everything downstream"; it never contains upstream partitionings. Ancestors of `P1.2.1.1` are
`P1.2.1`, `P1.2`, `P1`. Sorting `F1` by creation time gives the whole lineage as one timeline.

With #503 the same calls also write seven edges (one per declared parent): `P1→P1.1`, `P1→P1.2`, `P1→P1.3`,
`P1.2→P1.2.1`, `P1.2→P1.2.2`, `P1.2.1→P1.2.1.1`, `P1.2.2→P1.2.2.1`. `GET .../parents` of `P1.2.1.1` returns only
`P1.2.1`, while `GET .../ancestors` returns all three.

Creating `P1.2.1.1` with parent `P1.2.1` adds it to every flow that contains `P1.2.1` (`F1.2.1`, `F1.2`, `F1`). This is
the "new child joins every flow of its parent" rule, not descendant propagation.

### 4.2 Where D5 matters: retroactive linking

`P1.2.1` is created *without* a parent and `P1.2.1.1` is created under it. Later someone links `P1.2.1` under `P1.2`
with `PATCH`:

- **Before this decision (propagation):** `F1.2` and `F1` gain `P1.2.1` **and** `P1.2.1.1`.
- **Now:** `409` (status `33`), nothing written, because `P1.2.1` already has a descendant.
- **Unaffected:** if `P1.2.1` has no descendants yet, the same `PATCH` succeeds; an idempotent re-link of an
  already-linked non-leaf child via `POST` (or `PATCH` with `copyAdditionalData=false`) is still a no-op (or an
  edge-only backfill, §3.2 step 3).

### 4.3 Diamond

`P1.2.1` is derived from `P1.2` and from an unrelated `P2`. Passing both parents on the creation `POST` works (leaf).
Adding `P2` later with `PATCH` to a `P1.2.1` that already has `P1.2.1.1` is refused: with propagation it would have
put `P1.2.1.1` (and any deeper subtree) into `F2` as well.

### 4.4 Why simply deleting propagation would be unsound

Suppose descendants were silently not propagated and nothing else changed. Create `C` under `B`, then link `B` under
`A`: `F(A)` lacks `C`. The cycle check ("is the parent in the child's flow?") for linking `A` under `C` now passes
because `C ∉ F(A)`, and a cycle `A → B → C → A` is created. The cycle check, `GET /ancestors` and the flow reads all
rely on the closure invariant, and the membership would become **order dependent**. That is why the decision is
"refuse", not "ignore".

---

## 5. Alternatives considered

| Option                                                                                                                                                                              | Verdict                                                                                                                                                                                                          |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **A. Propagate the child's descendants** into every parent flow (the implementation up to the previous commit). Order independent, keeps the invariant, can heal retroactive links. | Rejected. Costs below.                                                                                                                                                                                           |
| **B. Silently drop propagation.**                                                                                                                                                   | Rejected. Breaks the invariant: unsound cycle check, order-dependent flows (§4.4).                                                                                                                               |
| **C. Direct-edge table** with recursive cycle check, no closure.                                                                                                                    | Rejected *as a replacement* in #496: largest redesign, changes flow and ancestor semantics for existing consumers. **Adopted additively in #503** (edges next to the closure, closure still authoritative; §10). |
| **D. Flow = X + direct children only** (no upward rule).                                                                                                                            | Rejected: loses the single-timeline view of a whole lineage, changes `GET /ancestors` to direct parents only, and breaks behaviour that already exists on master.                                                |
| **E. Propagation behind an explicit opt-in** (e.g. `includeDescendants`, default false).                                                                                            | Deferred; see §8.                                                                                                                                                                                                |
| **F. Refuse linking a non-leaf child (chosen).**                                                                                                                                    | Keeps closure semantics and a sound cycle check, removes the unbounded bulk write.                                                                                                                               |

### Costs of option A (why it was removed)

1. **Irreversible.** There is no unlink API; one mistaken `PATCH` can put a whole subtree into other flows
   permanently.
2. **Surprising diamonds.** A second parent of a mid-tree partitioning pulls its entire downstream into the new
   parent's flow.
3. **Unbounded write size under the global lock.** One `PATCH` writes (parent flows × subtree size) rows while
   holding the global advisory lock with no timeout and a 10-connection pool; a large re-link blocks all linking and
   can exhaust the pool. The `204` response says nothing about how much was written.
4. **Concurrency fragility:** correctness rests on READ COMMITTED plus the lock, and the lock-free v1 create can
   still race it.
5. **Larger test and documentation surface** (exact-set flow assertions, diamond cases, the missing-descendant
   diagnostic).

Costs 1, 3 and 2 were judged the major ones.

---

## 6. Consequences

**Positive**

- Closure invariant holds *by construction*: only leaves are linked under new parents and a new child joins the
  existing flows of its parent, so `members(F(A)) = {A} ∪ descendants(A)` stays true.
- Cycle detection and ancestor/descendant/flow reads stay sound and non-recursive.
- A link writes a bounded number of rows (the child into the parents' flows, plus one edge per parent), so the global
  lock is held briefly.
- Creation-time linking, the common pipeline (upstream created before downstream), is unaffected and idempotent.

**Negative / trade-offs**

- A missed link **cannot be fixed through the API** once downstream partitionings exist. Fixing it is a DBA-level
  operation (insert the missing flow memberships and edge by hand); no diagnostic queries or repair recipe ship with
  the service.
- Pipelines must create a partitioning **with its parents** (or link it before anything is created below it). A
  consumer that builds downstream-first has to be changed.
- Legacy data that was written by older servers is **not backfilled**: it may lack descendants in ancestor flows, and
  it has no edges (§10.1).
- Released agents v0.8 – v0.10 **ignore a `PATCH` `409`** (they treat it as "link already exists"), so a refused
  link is not reported by those agents; the server logs a WARN.
- Still a **single global lock**; no `lock_timeout`; no READ COMMITTED guard; no unlink.

---

## 7. Copy policy and compatibility

- **Additional data is never copied by `POST`.** The user can set it for the child through the API. Copying
  additional data between related partitionings is messy and rarely what is wanted.
- **Measure definitions** are copied only on explicit request (`copyMeasureDefinitions = true`), only to a
  newly created child, as a distinct union over parents by `(measure_name, measured_columns)`; the child's own
  definitions win; the copy author is the caller. The default is `false` (parent and child can have different schemas
  and a measure on a column that does not exist in the child cannot be copied meaningfully), which is consistent with
  the rest of the API.
- **Existing agents keep inheriting:** released agents v0.6 – v0.10 send only the legacy `parentPartitioningId` and
  never the flag; for such a body the flag defaults to `true`, so their single-parent children keep inheriting the
  parent's measure definitions after the server upgrade. Current agents always send the flag explicitly.
- The multi-parent `AtumContext.subPartitionContext(sub, parents)` overload was removed; the agent now creates a
  partitioning with one `POST` for 0..N parents and exposes the copy opt-in through new overloads (existing
  signatures delegate; binary compatibility checked against v0.10.0).
- **Deployment order:** server first, agents same or older. An agent of this release against an older server loses
  the parents (the older server ignores `parentPartitioningIds`).
- **Server and schema move in lockstep.** The `lineage` cutover (§10.2) drops `runs.get_partitioning_ancestors`,
  which the v0.10.0 server calls. After the v0.11.0 migrations a v0.10.0 server binary is **not** supported
  (`GET .../ancestors` fails); rolling the server back requires restoring the database as well. Migrations are
  forward-only.
- The `V0.10.0.x` migrations were **edited in place** while unreleased; a development database that applied an
  earlier build of this change fails Flyway validation and must be dropped and recreated (never run `flywayRepair`
  to silence a checksum mismatch).

---

## 8. Potential future improvements

Done since the initial #496 draft: the direct-edge table (#503, §10), `REVOKE EXECUTE ... FROM PUBLIC` on the linking
functions, and a node cap on graph traversal (#493).

1. **Explicit opt-in propagation** (`includeDescendants`, default false) for retroactive links, with a **subtree-size
   cap**, a response that reports how many memberships were written, and the lock held only for the write. Keeps the
   default refusal.
2. **Unlink / remove-membership API** (admin only, removing both the edge and the closure rows, with the same cycle
   and invariant guarantees). Prerequisite for making any bulk linking safe.
3. **Multi-parent retroactive link.** `PATCH .../ancestors` takes one parent per call; a variant accepting several (e.g.
   `POST .../parents`) would mirror the create path. Leaf-only rules unchanged.
4. **Backfill / repair tooling** for legacy data: an admin function that detects closure gaps and reconstructs edges
   from external lineage metadata where available. Edges cannot be derived from the closure alone (§10.1).
5. **Locking:** per-lineage (not global) advisory lock or a `lock_timeout`; a guard that rejects non-READ COMMITTED
   isolation; a test of the opposite-link race.
6. **Remove v1** (create partitioning/checkpoint) in a later release; this removes the lock-free path and its race.
7. **Retire the closure** once every transitive read is served by recursive queries over the edges (§10.4).
8. **Read-side enrichment and cost:** mark direct parents in `GET .../ancestors` by joining the edges; a single
   `LIMIT n+1` query instead of the separate `has_more` scan in `get_partitioning_ancestors`/`descendants`;
   rate-limiting or caching for `/lineage`.
9. **Tooling & test coverage:** a binary signature checker (MiMa) in CI; a `Deprecation` HTTP header for v1; Postman
   coverage of `PATCH .../ancestors` returning `404` for an unknown parent or child.

---

## 9. Verification

- **Database (`sbt testDB`):** integration suites verify `link_to_parents` (dual write, idempotency, multi-parent,
  cycle/descendants rejections, two-connection lock race, edge-only fast-path backfill), `create_partitioning_with_parents`,
  `update_partitioning_parent` (copy-flag matrix), `get_partitioning_ancestors`, and `partitioning_edges`.
- **Server (`sbt test`, `sbt testIT`):** unit and integration suites verify controller/service/repository layers, endpoint
  HTTP status mappings (including `33 → 409`), and all `lineage.*` DB functions (`descendants`, `parents`, `children`,
  `lineage` graph cap/expandability).
- **Agent (`sbt testAgentSpark3`):** unit tests verify multi-parent `POST`, copy-flag overloads, and `409` propagation.
- **API smoke (`api-tests/`):** Postman/Newman 1-shot collection exercises v2 create/idempotency, `parents`, `children`,
  `ancestors`, `descendants`, `lineage`, `400`/`404`/`409` contracts, and deprecated v1 endpoints against Postgres 15.
- **Not run in CI here:** `AgentServerCompatibilityTests` (`sbt testCompatibility`), which requires a live server and
  runs in the deployment repository's CI.

---

## 10. Explicit edges and the `lineage` schema (#503, #493)

After #496 shipped with closure-only storage, option C of §5 was implemented as an **addition**, not a replacement:
`lineage.partitioning_edges` records one row per declared parent → child link (`fk_child_partitioning`,
`fk_parent_partitioning`, `created_by`, `created_at`), written in the same transaction as the closure by
`lineage.link_to_parents` (§3.2).

### 10.1 Why hybrid, and the forward-only gap

The two models coexist because:

- **Closure reads are already in production** and cheap (one indexed join on `flows.partitioning_to_flow`).
  Replacing them with recursive edge queries would cost O (depth) and change every consumer.
- **Adding is non-breaking.** Existing endpoints keep their semantics; new endpoints use the edges.
- **Different questions:** the closure answers "everything upstream/downstream" (audit scope, impact), edges answer
  "what was this built from" (provenance, graph rendering).

**Data written before v0.11.0 has no edges.** For such partitionings `GET .../parents` and `.../children` return an
empty list and `GET .../lineage` returns only the focal node (no edges), while `GET .../ancestors` and
`.../descendants` still answer. A migration backfill is **not possible**: the closure records `{A} ∪ descendants(A)`
but not which member is a direct child, so a direct parent cannot be told apart from a grandparent.

Edges accrue lazily for old data only when a producer re-runs and declares a parent that is already an ancestor: the
fast path (§3.2 step 3) inserts the missing edge without taking the lock. Edges exist only where producers declare
parents; partitionings created without parents never get edges.

### 10.2 The `lineage` schema and direct cutover

All lineage tables and functions live in a dedicated `lineage` schema (owner `atum_owner`; `USAGE` for `atum_user`
and `atum_reader`; `SELECT` on `partitioning_edges` for `atum_reader`; `EXECUTE` revoked from `PUBLIC` and granted to
`atum_user`):

| Object                                                  | Purpose                                              | Backing data |
|---------------------------------------------------------|------------------------------------------------------|--------------|
| `partitioning_edges` (table)                            | Direct parent → child links                          | —            |
| `link_to_parents(BIGINT, BIGINT[], TEXT)`               | Validation, cycle and leaf checks, dual write (§3.2) | both         |
| `get_direct_parents(BIGINT)`                            | 1-hop upstream with edge metadata                    | edges        |
| `get_direct_children(BIGINT)`                           | 1-hop downstream with edge metadata                  | edges        |
| `get_partitioning_ancestors(BIGINT, INT, BIGINT)`       | Paginated transitive ancestors (moved from `runs`)   | closure      |
| `get_partitioning_descendants(BIGINT, INT, BIGINT)`     | Paginated transitive descendants                     | closure      |
| `get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER)` | Bounded sub-graph (nodes + edges) as JSONB           | edges        |

Responsibilities: `runs` owns partitioning, checkpoint and measure-definition CRUD and the orchestrating entry points
(`create_partitioning_with_parents`, `update_partitioning_parent`, `search_partitionings`); `flows` owns the closure
and flow-based reads; `lineage` owns the relationships.

**Direct cutover, no wrappers.** Moved functions are dropped from their old schema in the same migration
(`runs.get_partitioning_ancestors` is dropped by `V0.10.0.6`), and unreleased #496 helpers were redefined directly in
`lineage`. Server and migrations are versioned and deployed together against a dedicated database, so wrappers would
add dead code at the cost of the lockstep requirement in §7.

Validation happens at the entry-point functions rather than through foreign keys on `partitioning_edges` (following the
existing functional-DB style). The table has PK `(fk_child_partitioning, fk_parent_partitioning)` for "parents of X",
`partitioning_edges_parent_idx (fk_parent_partitioning, fk_child_partitioning)` for "children of X" and downstream
BFS, and a `CHECK` against self-loops.

**SQL conventions to note:** `get_partitioning_ancestors`/`descendants` return a status-`14` row when empty whereas
`get_direct_parents`/`children` return zero rows; and `get_partitioning_lineage_graph` coerces an unknown `direction`
to `both` and clamps `depth` to `1..20` in SQL (the HTTP layer rejects invalid values with `400` first).

### 10.3 Read API

| Endpoint                                                                                  | Returns                                                                                                                                                     | Source  |
|-------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `GET /api/v2/partitionings/{id}/parents`                                                  | Direct parents (`ParentPartitioningDTO`: id, partitioning, author, `linkedBy`, `linkedAt`); empty list if none                                              | edges   |
| `GET /api/v2/partitionings/{id}/children`                                                 | Direct children (`ChildPartitioningDTO`, same shape); empty list if none                                                                                    | edges   |
| `GET /api/v2/partitionings/{id}/ancestors`                                                | Paginated transitive ancestors (unchanged)                                                                                                                  | closure |
| `GET /api/v2/partitionings/{id}/descendants`                                              | Paginated transitive descendants (new)                                                                                                                      | closure |
| `GET /api/v2/partitionings/{id}/lineage?direction=both\|upstream\|downstream&depth=1..20` | `LineageGraphDTO`: `primaryPartitioningId`, `depth`, `direction`, `nodes`, `edges`, `truncated`, `expandableUpstreamNodeIds`, `expandableDownstreamNodeIds` | edges   |

All return `404` for an unknown partitioning. `/lineage` defaults to `direction=both`, `depth=5`; an invalid value is
`400` (`direction` is trimmed and case-insensitive).

**Bounded traversal (#493).** The first `/lineage` version used a recursive CTE without visited-node tracking: a node
reachable by paths of different lengths was expanded once per path, causing combinatorial growth on fan-in/fan-out
hubs. It was replaced by an iterative BFS that expands each node once (O (V + E)) and stops at **5,000 nodes per
direction**. `truncated` is `true` only when that 5,000-node cap cut the traversal; `expandableUpstreamNodeIds` and
`expandableDownstreamNodeIds` list any nodes in the returned sub-graph that have valid parents or children outside it
(whether stopped by `depth`, `direction`, or the cap — e.g. `direction=downstream` still populates
`expandableUpstreamNodeIds` when nodes have unincluded parents).

### 10.4 Consequences of the amendment

- Exact 1-hop lineage for links written from v0.11.0 onward and graph rendering in one request, at a small extra write
  cost per link under the same lock.
- Two representations must stay consistent; only `lineage.link_to_parents` writes either, which keeps them in step.
- Path to retiring the closure (§8 item 7): once all transitive reads run over edges and old data is backfilled or
  accepted as edge-less, `flows.partitioning_to_flow` can become derived or be dropped.

---

## 11. References

- Issue #475; PRs #496 (multi-parent linking, closure only), #503 (explicit edges, `lineage` schema), #493 (bounded
  graph traversal, partitioning search).
- Migrations under `../database/src/main/postgres/`:
    - `flows/V0.10.0.1__partitioning_to_flow_index.ddl` — index on `partitioning_to_flow (fk_partitioning)`.
    - `lineage/V0.10.0.2___.ddl`, `lineage/V0.10.0.3__partitioning_edges.ddl` — `lineage` schema and edge table.
    - `lineage/V0.10.0.4__link_to_parents.sql` — linking and dual write.
    - `lineage/V0.10.0.5__get_partitioning_descendants.sql`, `lineage/V0.10.0.6__get_partitioning_ancestors.sql`.
    - `lineage/V0.10.0.7__get_direct_parents.sql`, `lineage/V0.10.0.8__get_direct_children.sql`.
    - `lineage/V0.10.0.9__get_partitioning_lineage_graph.sql`.
    - `runs/V0.10.0.10__update_partitioning_parent.sql`, `runs/V0.10.0.11__create_partitioning_with_parents.sql`.
- `../database/README.md` (schema organisation), `../server/README.md` (compatibility, lineage endpoints),
  `../agent/README.md` (sub-contexts and parents), `../api-tests/README.md`.
- ADR 02 *Audit Consumption* (consumption side of flow membership and edges; its §2 is aligned with §3 and §10 of
  this ADR).
