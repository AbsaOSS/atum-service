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

## Schema Organization

The database is organized into three main schemas:

- **`runs`**: Core partitioning and checkpoint operations, measure definitions, additional data, and descendants traversal (`get_partitioning_descendants`)
- **`flows`**: Transitive closure (`partitioning_to_flow`) and flow-based queries (`get_flow_partitionings`, `get_flow_checkpoints`)
- **`lineage`**: Edge-based lineage operations (source of truth):
  - `partitioning_edges` table (direct parent-child relationships)
  - `link_to_parents` (multi-parent linking with cycle/leaf checks)
  - `get_direct_parents`, `get_direct_children` (1-hop navigation)
  - `get_partitioning_lineage_graph` (sub-graph for visualization)
  - `get_partitioning_ancestors` (transitive ancestors)

The `lineage` schema serves as the source of truth for partitioning relationships, while `flows` maintains derived/cached data for efficient transitive queries.
