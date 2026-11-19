## Deployment

How to set up database for local testing

### Using Docker

```zsh
# start up postgres docker container (optional; instead you can create atum_db on your local postgres instance; CI uses postgres:15)
docker run --name=atum_db -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=atum_db -p 5432:5432 -d postgres:15

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

## Schema Organization

The database is organized into three main schemas:

- **`runs`**: Core partitioning, checkpoint, and measure definition operations along with their metadata, plus
  `search_partitionings` (key/value, key-existence, partial/exact key-set, non-blank author, key/value substring with at
  least 2 non-whitespace characters, and checkpoint time-window search, backed by `runs.partitionings` GIN and
  `partitionings_created_by_idx` B-tree from `V0.10.0.15`, and `runs.checkpoints` start-time indexes)
- **`flows`**: Transitive closure (`partitioning_to_flow`) and flow-based queries (`get_flow_partitionings`,
  `get_flow_checkpoints`)
- **`lineage`**: Explicit direct-edge lineage operations (forward-only in versions newer than v0.10.0):
    - `partitioning_edges` table (direct parent-child relationships)
    - `link_to_parents` (multi-parent linking with cycle/leaf checks, dual-writing `partitioning_edges` and
      `partitioning_to_flow`)
    - `get_direct_parents`, `get_direct_children` (1-hop navigation over edges)
    - `get_partitioning_lineage_graph` (bounded sub-graph for visualization over edges)
    - `get_partitioning_ancestors` (transitive ancestors traversal over `flows`)
    - `get_partitioning_descendants` (transitive descendants traversal over `flows`)

`flows.partitioning_to_flow` remains authoritative for transitive queries (`ancestors`, `descendants`, flow queries)
and cycle/leaf checks.
