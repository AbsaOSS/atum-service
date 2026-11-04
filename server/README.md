# Atum Web Service

## How to build and run

To create a jar file that can be executed:

```shell
> sbt clean "project server" assembly
> java -jar server/target/jvm-2.13/*.jar
```

If you want to quickly build and run from sbt you can run using the command below (alternatively you can execute
za.co.absa.atum.server.Main within your IDE). This deploys it to `localhost:8080`.

```shell
sbt "server/runMain za.co.absa.atum.server.Main"
```

### API v1 is deprecated

All `/api/v1/*` endpoints (`POST /api/v1/createCheckpoint`, `POST /api/v1/createPartitioning`) are **deprecated**.
They are marked `deprecated: true` in the generated OpenAPI / Swagger UI (`/docs/`) and keep behaving exactly as in
v0.10.0 (single optional `parentPartitioning`).

- **Who is affected:** only Atum agents up to and including v0.5.x use API v1. Agents v0.6.0 and newer use API v2 only.
- **Replacement:** API v2 — `POST /api/v2/partitionings` (create / obtain a partitioning, optionally with parents) and
  `POST /api/v2/partitionings/{partitioningId}/checkpoints` (save a checkpoint).
- **Removal:** API v1 is planned to be removed in a later release; upgrade agents to v0.6.0 or newer before then.

### Version compatibility (multi-parent)

Always upgrade the server before the agents. A multi-parent capable agent sends `parentPartitioningIds`
(`PartitioningSubmitV2DTO`, API v2) when creating a partitioning. The deprecated v1 `PartitioningSubmitDTO` is
unchanged from v0.10.0 and supports a single optional parent only.
An older server ignores these unknown fields and silently drops the extra parents (or all of them, with no error).
Multi-parent support requires three database migrations: `flows/V0.10.0.1___link_to_parents.sql`,
`runs/V0.10.0.2__create_partitioning_with_parents.sql` and `runs/V0.10.0.3__update_partitioning_parent.sql` (see
[database/README.md](../database/README.md) for applying them and for resetting a development database), and the
v2 endpoint support in this server.

Single-parent behaviour, public signatures, and decoding of previously produced JSON (field absent -> empty list)
are unchanged for REST clients. The *encoded* JSON of `PartitioningSubmitV2DTO` now carries exactly
`partitioning`, `parentPartitioningIds`, `author` and `copyMeasureDefinitions` and never the singular
`parentPartitioningId` (the server still decodes that legacy key). That is why a new agent against an older server
loses even a single parent. For JVM users the Scala shape of `PartitioningSubmitV2DTO` and the signature of
`Dispatcher.createPartitioning` did change, see [agent/README.md](../agent/README.md#upgrading-from-v010x).

**Creating a partitioning (API v2).** `POST /api/v2/partitionings` is an idempotent create-or-link for 0..N parents
(at most 100 distinct parent ids, otherwise 400). It always goes through `runs.create_partitioning_with_parents`:

- `201` + `Location` when the partitioning was created (with its parents, if any, linked);
- `200` + `Location` when the partitioning already existed ("already exists" is never a conflict). The body carries the
  STORED author, and any new parent links in the request are still applied, so linking a single new parent to an
  existing partitioning works;
- `404` for an unknown parent;
- `409` for a dependency cycle, or when an existing partitioning that already has descendants would get a new parent
  (nothing is written; a partitioning created by the request is always a leaf).

The legacy single-parent key `parentPartitioningId` is still accepted next to `parentPartitioningIds`.

Parent ids may be given as `parentPartitioningIds: []` (no parent), a number or a numeric string in the legacy key, or
`null` in the legacy key; duplicates are ignored and only DISTINCT ids count towards the limit of 100. A `404` for an
unknown parent creates nothing: the child does not exist afterwards.

**Copying measure definitions (opt-in).** `copyMeasureDefinitions` has the default `false` (absent or `null` means
`false`). With `true` the server copies the measure definitions of the parent(s) to the child **only if the child is
newly created**: the distinct union by measure name and `measured_columns` (the order of `measured_columns` matters, it is
part of the identity of a measure definition), with `created_by` = the `author` of the request and `created_at` = now.
The flag on a request for an already existing partitioning (`200`) does nothing. **Additional data is never copied by
`POST`.**

### Linking an existing partitioning (PATCH ancestors)

`PATCH /api/v2/partitionings/{partitioningId}/ancestors` makes `{partitioningId}` a child of an existing parent:

```json
{ "parentPartitioningId": 123, "author": "someone", "copyMeasurements": false, "copyAdditionalData": false }
```

- The flags are called `copyMeasurements` and `copyAdditionalData` here (on `POST` the flag is `copyMeasureDefinitions`).
  **Both are required**: the Scala DTO declares defaults, but the JSON decoder does not apply them, so a body without
  one or both flags is answered with `400` (verified on a running server).
- `204` on success. Repeating the request (or linking something that is already linked) is a no-op that is also `204`.
- `409` when the link is a self-link, would create a dependency cycle, or the partitioning already has descendants and
  the parent is not yet its ancestor (nothing is written); `404` when the child or the parent is unknown
  (the not-found messages, status 41 and 42, do not name the id).
- Both copy flags are opt-in per request: `true` copies the parent's measure definitions and/or additional data to the
  child, `false` copies nothing. **`copyAdditionalData: true` overwrites the child's own values for additional-data keys
  that also exist on the parent** (the parent's keys win; keys only the child has are kept). `copyMeasurements: true`
  copies only the definitions the child does not already have: on a conflict (same measure name and `measured_columns`)
  the child's own definition wins and nothing is overwritten. Both copies are also applied when the link already existed,
  so a repeated request applies them again (an additional-data value the child changed in the meantime is overwritten
  again).
- Descendants are never propagated: every flow that contains the parent receives the child only (a newly created
  child therefore joins every flow that contains its parent). The cycle check (`409`) runs before anything is written.
  Linking an **existing** partitioning that already has descendants under a **new** parent is refused with `409`
  (database status `33`) and nothing is written (no flow membership, no copies); only leaves can get new parents, so
  build the hierarchy top-down. Links that are redundant, also for a partitioning with descendants, change nothing
  (`204` / `200`). Creation-time linking of a new partitioning is unaffected. Existing data is **not** back-filled, see "Concurrency, retries and known limitations" below and the
  read-only diagnostics in [database/README.md](../database/README.md#diagnostics-read-only).

`GET /api/v2/partitionings/{partitioningId}/ancestors` answers `200` with an empty `data` list (and `hasMore: false`)
for a partitioning without ancestors, `404` for an unknown id.

### Compatibility matrix

Deploy the server before upgrading any agent. The server accepts what the released agents send (the legacy
`parentPartitioningId` key and the single-parent flow), the opposite direction is not safe.

| Client | Against this server | Effect |
|--------|---------------------|--------|
| Agent up to v0.5.x | uses API v1 (deprecated) | unchanged, v1 keeps behaving as in v0.10.0 |
| Agent v0.6 - v0.7 | `GET` partitioning, `POST` only if it is missing, any `2xx` is success | works; a newly created child **no longer inherits** the parent's measure definitions (the server default is now `false`) |
| Agent v0.8 - v0.10 | as above, plus `PATCH .../ancestors` (flags `false`) for an existing child | works and loses the inheritance as above; a `409` of that `PATCH` (cycle, or the child already has descendants) is **ignored** by these agents, they treat it as "link already exists", so a rejected link is not reported |
| Agent of this release | one `POST` for 0..N parents | full feature set, the copy opt-in is available through the new overloads |
| Agent of this release | an **older** server | the parents are lost: the encoded request carries only `parentPartitioningIds`, which an older server ignores |
| REST client | `POST` / `PATCH` / `GET` as described above | `POST` is idempotent (`201` / `200`), the legacy single-parent key is accepted |

Workarounds when released agents (v0.6 - v0.10) stopped inheriting measure definitions: (1) upgrade the agent and pass
the opt-in flag through the new overloads ([agent/README.md](../agent/README.md)); (2) add the measures explicitly in
agent code: `childCtx.addMeasures(parentCtx.currentMeasures)` (it changes the child context in place and returns it,
the definitions reach the server with the checkpoints of the child); (3) through the API: there is no endpoint that
creates a measure definition on its own, the definitions are created by a checkpoint
(`POST /api/v2/partitionings/{partitioningId}/checkpoints` with `measurements`), or link an existing child with
`PATCH .../ancestors` and `copyMeasurements: true`.

### Concurrency, retries and known limitations

- Writers are serialized by ONE global advisory lock, taken only when something has to be written (the fast path that
  finds everything already linked takes no lock). There is no `lock_timeout`: a waiting writer keeps one of the at most
  10 pooled database connections (`maxPoolSize=10`). Correctness requires the isolation level READ COMMITTED
  (the HikariCP default), REPEATABLE READ / SERIALIZABLE are not supported and not guarded.
- Retries: the agent retries `5xx` and `IOException` (default `max-retries=3`, so up to 4 `POST`s can reach the server for
  one logical create). That is safe because `POST /api/v2/partitionings` is idempotent.
- Data that was linked before this release is **not** back-filled; the read-only diagnostics and the repair recipe are in
  [database/README.md](../database/README.md#diagnostics-read-only). A partitioning's ancestors are not the same as its direct parents.
- Dev databases that applied an earlier V0.10.0.x of this unreleased change fail `flyway validate`: reset them as
  described in [Resetting a development database](../database/README.md#resetting-a-development-database).
  Never run `flywayRepair` for this.
- Known limitations that are tracked as follow-ups and not changed by this release: the deprecated v1
  `runs.create_partitioning(_if_not_exists)` is lock-free: a v1 create of a new child can race a v2 link of its parent
  under a new ancestor and miss that ancestor's flow (no existing descendant is ever lost);
  `runs.update_partitioning_parent` and the internal `flows._add_to_parent_flows` / `flows._create_flow` are executable by
  `PUBLIC`; there is no READ COMMITTED guard and no test of the opposite-link race; `BaseController` still has a stray
  `println`; `runs.get_partitioning_ancestors` returns the status-14 "no rows" record (with `has_more` `NULL`) for any
  empty page, for example a root or an `offset` past the last ancestor, and the server maps it to `hasMore: false`; the
  not-found messages do not name the ids.

### REST API Reference

The REST API exposes a Swagger Documentation UI which documents all the HTTP endpoints exposed.
It can be found at **{REST_API_HOST}/docs/** (e.g. `http://localhost:8080/docs/`)

### Observability metrics

Optionally you can run server with monitoring that collects metrics about http communication and/or jvm/zio
runtime. `intervalInSeconds` parameter refers to frequency of data collection from its runtime environment.
Monitoring of http communication is based on intercepting of http calls therefore `intervalInSeconds` parameter does not
apply.

```
{
  monitoring {
    # monitoring of http communication
    http {
      enabled=true
    }
    # monitoring of jvm and zio
    jvm {
      enabled=true
      intervalInSeconds=5
    }
  }
}
```

When monitoring enabled, the application exposes `http://localhost:8080/metrics`
and/or `http://localhost:8080/zio-metrics` endpoints which can be scraped by Prometheus.
For testing purposes there is [docker-compose.yml](./docker-compose.yml) file which can be used to start up dockerized
Prometheus and Grafana instances. Prometheus scraping configs are defined in [prometheus.yml](./prometheus.yml) file. 

# Build & run docker image

```shell
sbt clean assembly

docker build -t absaoss/atum-service:latest --progress=plain --no-cache \
--build-arg BUILD_PROXY=http://zproxycloud.intra.absaafrica:80 \
--build-arg SSL=false \
--build-arg LDAP_SSL_CERTS_PATH=./certs \
--build-arg SSL_DNAME="CN=*.my.domain.com, OU=project1, O=mycorp, L=Johannesburg, ST=Gauteng, C=za" ./

# If you want to run it against AWS, you need to add AWS related credentials into the docker container, possibly via 
# environment variables; but you can also run it against your a local Postgres DB (in host or another Docker container).
docker run -v /absolute/path/resource.conf:/opt/config/resource.conf \
  -e AWS_ACCESS_KEY_ID="abc" -e AWS_SECRET_ACCESS_KEY="def" -e AWS_SESSION_TOKEN="xyz" \
  -p 8080:8080 -p 8443:8443 absaoss/atum-service:latest

# reference.conf file has to be configured as below when running with ssl enabled
ssl {
    enabled=true
    keyStorePassword=changeit
    keyStorePath="/etc/ssl/certs/selfsigned.jks"
  }
```
