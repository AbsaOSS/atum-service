## Deployment

How to set up database for local testing

### Using Docker

```zsh
# start up postgres docker container (optional; instead you can create atum_db on your local postgres instance)
docker run --name=atum_db -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=atum_db -p 5432:5432 -d postgres:16

# migrate scripts
sbt flywayMigrate

# kill & remove docker container (optional; only if using dockerized postgres instance)
docker kill atum_db
docker rm atum_db
```

### Using local postgres instance
- create database `atum_db`
- migrate scripts
```zsh
sbt flywayMigrate
```

In case some structures are already present in the database, you can use
```zsh
sbt flywayClean 
```
to remove them or 
```zsh
sbt flywayBaseline 
```
to set the current state as the baseline.

## Migration notes for 1.1 (parent linking)

Three migrations of the unreleased 1.1 line (versions `V0.10.0.x` that are not part of the released `v0.10.0` tag) define how
partitionings are linked to their parents:

| Migration | State | Notes |
|-----------|-------|-------|
| `flows/V0.10.0.1___link_to_parents.sql` | new | `flows._link_to_parents`; replaces the deleted `runs/V0.10.0.1___copy_parents_data_to_partitioning.sql` |
| `runs/V0.10.0.2__create_partitioning_with_parents.sql` | edited in place | `runs.create_partitioning_with_parents` (idempotent, 0..N parents) |
| `runs/V0.10.0.3__update_partitioning_parent.sql` | new | redefines `runs.update_partitioning_parent` (same signature as in `V0.3.0.4`) on top of `flows._link_to_parents` |

These are in-place edits of versions that were never released. Any database that already applied an earlier build of these
`V0.10.0.x` migrations (for example a developer database that followed this branch) fails Flyway validation with
`Migration checksum mismatch for migration version 0.10.0.2` and `Validate failed: Migrations have failed validation`; such a database
has to be recreated, see [Resetting a development database](#resetting-a-development-database). Released migrations are untouched.
Migrations are forward-only: there are no down-migrations. A released server binary (`v0.10.0`) is expected to keep working against the
1.1 schema, because the signatures of the functions it calls are unchanged, but this combination is not tested.

## Resetting a development database

`sbt flywayClean flywayMigrate` is the right command for a fresh database or for one that only contains the `public` schema (this is what
CI does). It is **not** enough for a database that already contains the application schemas: the `flywayClean` of the Flyway sbt plugin used here (7.4.0) cleans only
the `public` schema, while the migrations create the schemas `runs`, `flows` and `validation` (`CREATE SCHEMA IF NOT EXISTS`; the schema name `flow_patterns` also appears in
the sources, but no applied migration creates it) and these survive the clean. A second `sbt flywayClean flywayMigrate` therefore fails: the migrations are
replayed from scratch, the schema creation is skipped because the schemas exist, and the first migration that creates an existing table
aborts, for example `Migration V0.1.0.31__additional_data.ddl failed` with `ERROR: relation "additional_data" already exists`.
(`DROP SCHEMA` alone does not help either: the Flyway history table lives in `public`, still lists every migration as applied, and
`flywayMigrate` answers `Schema "public" is up to date. No migration necessary.` without recreating the schemas.)

Both recipes below are for development and test databases only; they delete all data. They were executed against a scratch PostgreSQL 15 database
that had been put into the checksum-mismatch state, and both ended with all 74 migrations applied.

**Recipe A - keep the database, drop the application schemas, then clean and migrate:**

```zsh
psql -U postgres -d atum_db -c 'DROP SCHEMA IF EXISTS runs, flows, flow_patterns, validation CASCADE;'
sbt flywayClean flywayMigrate
```

(for the dockerized instance from above: `docker exec atum_db psql -U postgres -d atum_db -c '...'`). The roles created by the migrations are cluster-level and
stay in place; the migrations skip roles that already exist.

**Recipe B - recreate the database** (PostgreSQL 13 or newer for `WITH (FORCE)`):

```zsh
psql -U postgres -d postgres -c 'DROP DATABASE atum_db WITH (FORCE);'
psql -U postgres -d postgres -c 'CREATE DATABASE atum_db;'
sbt flywayMigrate
```

Pre-existing roles (they are cluster-level and survive the drop) are tolerated; no extra step is needed.

Never run `flywayRepair` to get past a checksum mismatch of these migrations: it would re-stamp the stored checksums over SQL that really did change
and hide the divergence between the database and the migration files. Never edit an already numbered migration either; add a new one.

## `measured_columns` order matters

A measure definition is identified by its partitioning, the measure name and the **ordered** `measured_columns` array (unique constraint `measure_definitions_unq`
on `(fk_partitioning, measure_name, measured_columns)`). `["a","b"]` and `["b","a"]` are two different definitions. When definitions are copied from parents to a
newly created partitioning (opt-in, `copyMeasureDefinitions`), the distinct union over the parents is formed by `(measure_name, measured_columns)` with exactly this ordered comparison,
so a parent measuring `["a","b"]` and another parent measuring `["b","a"]` produce two definitions on the child. The copies get `created_by` = the caller and
`created_at` = now, not the values of the parent.

## Parent linking functions

* `flows._link_to_parents(child, parents[], user)` is the single place that links an existing partitioning under one or more parents. It rejects a self-link or a cycle with status `32`
  before anything is written; when every parent already has the child in its flow it writes nothing and takes no lock (fast path, status `14`, also for a child that has descendants); otherwise it takes one global
  transaction-scoped advisory lock and, after the cycle check, refuses a child that already has descendants (any other member in the child's own main flow) with status `33` before anything is written.
  Otherwise it adds **only the child** to every flow that contains a parent (status `11`): descendants are never propagated into the flows of a new parent, only leaves are linked under new parents.
  There is no `lock_timeout`: a waiting writer holds a pool connection (Hikari maximum 10). The re-checks after the lock are correct only under READ COMMITTED
  (the default); do not run the application with REPEATABLE READ or SERIALIZABLE.
* `runs.create_partitioning_with_parents(partitioning, user, parent_ids[], copy_measure_definitions)` creates the partitioning or reuses the existing one (idempotent), links all parents through
  `flows._link_to_parents` in the same transaction, and returns the stored `created_by` of an existing partitioning. Status `11` created without parents, `12` created with parents, `13` parents registered on an existing
  partitioning, `14` already present, `32` cycle, `33` the existing partitioning already has descendants and would get a new parent (nothing is written, no measure definitions are copied;
  a partitioning created by the call is always a leaf), `41` parent not found. Measure definitions are copied only when the partitioning is newly created by the call and the flag is `true`
  (`NULL` means `false`); additional data is never copied.
* `runs.update_partitioning_parent(child, parent, user, copy_measurements DEFAULT true, copy_additional_data DEFAULT true)` links through `flows._link_to_parents` first (`32` on a cycle or self-link, `33` when the child already has descendants and the parent is not yet its ancestor;
  nothing is written), then applies the copy flags; the DB-level defaults of both flags are `true`. Repeating a call re-applies the copies and does not fail. The additional-data copy **overwrites** the child's values for same-named keys (the parent wins); the measure-definition copy is `INSERT ... ON CONFLICT DO NOTHING` (the child's own definition wins). Statuses: `11` OK, `32` cycle, `33` child already has descendants, `41` child not found, `42` parent not found.

## Data created before 1.1: no backfill, diagnostics

The linking functions maintain the closure invariant `members(F(A)) = {A} ∪ descendants(A)`, where `F(A)` is the main flow of partitioning `A` (the row of `flows.flows` whose
`fk_primary_partitioning` is `A`). The 1.1 linking functions never propagate descendants: only a leaf (a partitioning without descendants) can be linked under a new parent, an existing partitioning that already has descendants is
refused with status `33` / HTTP `409`, and a newly created child joins every flow that contains its parent. Parent links that were written by older server versions did propagate
only the child, not the descendants that already existed, and the migrations of 1.1 do **not** backfill that data. The same hole can still arise when the deprecated lock-free v1 create races a link of its parent under a new
ancestor (see "Known limitations"). Such data is not repaired automatically; the queries below detect it.

### Diagnostics (READ-ONLY)

The queries only `SELECT`. Run each one inside a read-only transaction; each returns zero rows on healthy data:

```sql
BEGIN READ ONLY;
-- run one of D1, D2, D3 here
ROLLBACK;
```

**D1 - missing descendants** (the no-backfill hole; legacy data written by older servers, or the v1 race mentioned above). One row per member `missing_member_id` of the flow of `child_id` that is absent from the flow of `ancestor_id` although `child_id` is in that flow:

```sql
SELECT FP.fk_primary_partitioning AS ancestor_id,
       PTF.fk_partitioning        AS child_id,
       CPTF.fk_partitioning       AS missing_member_id
FROM flows.flows FP
INNER JOIN flows.partitioning_to_flow PTF
        ON PTF.fk_flow = FP.id_flow
       AND PTF.fk_partitioning <> FP.fk_primary_partitioning
INNER JOIN flows.flows FC
        ON FC.fk_primary_partitioning = PTF.fk_partitioning
INNER JOIN flows.partitioning_to_flow CPTF
        ON CPTF.fk_flow = FC.id_flow
WHERE NOT EXISTS (
    SELECT 1
    FROM flows.partitioning_to_flow X
    WHERE X.fk_flow = FP.id_flow
      AND X.fk_partitioning = CPTF.fk_partitioning
)
ORDER BY ancestor_id, child_id, missing_member_id;
```

Repair (a write, deliberately not part of the query): add `missing_member_id` to the flow of `ancestor_id` with one insert per row (this works for every member, whether or not it has descendants itself):

```sql
INSERT INTO flows.partitioning_to_flow (fk_flow, fk_partitioning, created_by)
SELECT F.id_flow, <missing_member_id>, '<you>'
FROM flows.flows F
WHERE F.fk_primary_partitioning = <ancestor_id>
ON CONFLICT DO NOTHING;
```

For a `missing_member_id` that is a leaf the link API does the same: `PATCH /api/v2/partitionings/{missing_member_id}/ancestors` with
`{"parentPartitioningId": <ancestor_id>, "author": "<you>", "copyMeasurements": false, "copyAdditionalData": false}`
(or `SELECT status FROM runs.update_partitioning_parent(<missing_member_id>, <ancestor_id>, '<you>', false, false);`, status `11`). A `missing_member_id` that has descendants is refused with `33` / `409`
(descendants are never propagated), use the insert for it. Use `missing_member_id`, **not** `child_id`: re-linking `child_id` under `ancestor_id` takes the lock-free
"already linked" path and repairs nothing. Re-run D1 afterwards.

**D2 - mutual membership** (a dependency cycle). Pairs `partitioning_a < partitioning_b` where each one is a member of the flow of the other. The current code rejects every cycle with `409` / status `32`,
so such rows can only come from legacy or manually written data; there is no automatic repair, inspect the data and decide which of the two `flows.partitioning_to_flow` rows is wrong:

```sql
SELECT FA.fk_primary_partitioning AS partitioning_a,
       FB.fk_primary_partitioning AS partitioning_b
FROM flows.flows FA
INNER JOIN flows.partitioning_to_flow PA
        ON PA.fk_flow = FA.id_flow
INNER JOIN flows.flows FB
        ON FB.fk_primary_partitioning = PA.fk_partitioning
INNER JOIN flows.partitioning_to_flow PB
        ON PB.fk_flow = FB.id_flow
       AND PB.fk_partitioning = FA.fk_primary_partitioning
WHERE FA.fk_primary_partitioning < FB.fk_primary_partitioning
ORDER BY partitioning_a, partitioning_b;
```

**D3 - partitioning missing from its own main flow.** `main_flow_id` is `NULL` when the partitioning has no main flow at all (data older than the flows model); otherwise the flow exists but does not contain its
own primary partitioning, which is repaired by inserting the row `flows.partitioning_to_flow (fk_flow = main_flow_id, fk_partitioning = partitioning_id, created_by = '<you>')`:

```sql
SELECT P.id_partitioning AS partitioning_id,
       F.id_flow         AS main_flow_id
FROM runs.partitionings P
LEFT JOIN flows.flows F
       ON F.fk_primary_partitioning = P.id_partitioning
LEFT JOIN flows.partitioning_to_flow PTF
       ON PTF.fk_flow = F.id_flow
      AND PTF.fk_partitioning = P.id_partitioning
WHERE PTF.fk_flow IS NULL
ORDER BY partitioning_id;
```

## Known limitations and follow-ups

These are known and documented; they are not fixed by the 1.1 migrations.

* The deprecated v1 functions `runs.create_partitioning` / `runs.create_partitioning_if_not_exists` are lock-free: a v1 create of a new child under P can race a concurrent v2 link of P under a new ancestor Q
  and then miss the flow of Q (the new child is a descendant of P but not a member of the flow of Q; D1 reports it). Only the brand-new child can be missed; no existing descendant is ever moved or lost
  (Phase 8 review CR-01, narrowed because descendants are no longer propagated).
* `runs.update_partitioning_parent` is executable by `PUBLIC` (no `REVOKE` in its migration; WR-01).
* There is no guard against a transaction isolation level other than READ COMMITTED (WR-02): REPEATABLE READ and SERIALIZABLE callers are unsupported.
* `flows._add_to_parent_flows` and `flows._create_flow` are executable by `PUBLIC` and neither take the advisory lock nor check for cycles (IN-05); never call them directly.
* `runs.get_partitioning_ancestors` returns `has_more` as `NULL` only in its status `14` ("no rows") record. That record is returned for **any empty page**: a partitioning without ancestors (for example a root, also at `offset = 0`) or an `offset` past the last ancestor. A non-empty page computes `has_more` normally, whatever the `offset`. The server maps the `NULL` to `hasMore: false` (existing function, unchanged).
* The not-found messages (status `41` / `42`) do not name the offending ids.
