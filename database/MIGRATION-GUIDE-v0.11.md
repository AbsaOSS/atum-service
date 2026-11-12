# Migration Guide: v0.11.0 - Lineage Schema Introduction

## Summary

Version 0.11.0 introduces a new `lineage` schema that consolidates partitioning lineage operations previously scattered across `flows` and `runs` schemas. This migration creates better semantic boundaries and positions the codebase for eventual retirement of the flows schema.

## What Changed

### New Schema

- **`lineage` schema created** - dedicated schema for all partitioning lineage operations

### Table Migrations

- `flows.partitioning_edges` → `lineage.partitioning_edges` (table moved with all indexes and constraints)

### Function Migrations

| Old Function | New Function | Notes |
|--------------|--------------|-------|
| `flows._link_to_parents` | `lineage.link_to_parents` | Now public (no underscore prefix), granted to atum_user |
| `flows.get_direct_parents` | `lineage.get_direct_parents` | Direct parent queries |
| `flows.get_direct_children` | `lineage.get_direct_children` | Direct child queries |
| `flows.get_partitioning_lineage_graph` | `lineage.get_partitioning_lineage_graph` | Full lineage graph queries |
| `runs.get_partitioning_ancestors` | `lineage.get_partitioning_ancestors` | Ancestor queries (uses closure) |

### Cross-Schema Updates

The following `runs` schema functions were updated to call `lineage.link_to_parents`:
- `runs.update_partitioning_parent`
- `runs.create_partitioning_with_parents`

## Backward Compatibility

**All old function names continue to work via wrapper functions.** You have until **v0.13.0** to migrate your code.

### Wrapper Functions Provided

- `flows._link_to_parents` → delegates to `lineage.link_to_parents`
- `flows.get_direct_parents` → delegates to `lineage.get_direct_parents`
- `flows.get_direct_children` → delegates to `lineage.get_direct_children`
- `flows.get_partitioning_lineage_graph` → delegates to `lineage.get_partitioning_lineage_graph`
- `runs.get_partitioning_ancestors` → delegates to `lineage.get_partitioning_ancestors`

All wrappers will be removed in v0.13.0.

## For Database Users

### Immediate Action Required

**None.** The migration is backward compatible. All existing queries continue to work.

### Recommended Actions (Before v0.13.0)

Update function calls to use the new schema:

```sql
-- OLD
SELECT * FROM flows.get_direct_parents(123);
SELECT * FROM flows.get_direct_children(123);
SELECT * FROM flows.get_partitioning_lineage_graph(123, 'both', 5);
SELECT * FROM runs.get_partitioning_ancestors(123, 10, 0);

-- NEW
SELECT * FROM lineage.get_direct_parents(123);
SELECT * FROM lineage.get_direct_children(123);
SELECT * FROM lineage.get_partitioning_lineage_graph(123, 'both', 5);
SELECT * FROM lineage.get_partitioning_ancestors(123, 10, 0);
```

## For Application Developers

### Scala Package Updates (Target: v0.12.0)

Update import paths:

```scala
// OLD
import za.co.absa.atum.server.api.database.flows.functions.GetDirectParents
import za.co.absa.atum.server.api.database.flows.functions.GetDirectChildren
import za.co.absa.atum.server.api.database.flows.functions.GetPartitioningLineageGraph
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors

// NEW
import za.co.absa.atum.server.api.database.lineage.functions.GetDirectParents
import za.co.absa.atum.server.api.database.lineage.functions.GetDirectChildren
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningLineageGraph
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningAncestors
```

### API Impact

**No REST API changes.** All endpoints continue to work as before:
- `GET /api/v2/partitionings/{id}/parents`
- `GET /api/v2/partitionings/{id}/children`
- `GET /api/v2/partitionings/{id}/lineage`
- `GET /api/v2/partitionings/{id}/ancestors`

## Schema Organization Rationale

### Before v0.11.0

- **runs schema**: Partitioning CRUD + some lineage operations
- **flows schema**: Flow management + other lineage operations
- **Problem**: Lineage concerns scattered, unclear ownership

### After v0.11.0

- **runs schema**: Partitioning CRUD, checkpoints, measurements (core data operations)
- **flows schema**: Flow management and transitive closure (derived/cached data, future retirement candidate)
- **lineage schema**: All direct lineage operations (source of truth for relationships)

## Technical Details

### Table Migration

The `partitioning_edges` table migration used `ALTER TABLE SET SCHEMA`, which is:
- **Atomic**: Completes in milliseconds
- **Zero-downtime**: No table locking
- **Preserves everything**: Indexes, constraints, and permissions carry over automatically

### Advisory Lock

The advisory lock name in `lineage.link_to_parents` remains `'atum.flows.link_to_parents'` (unchanged from the original) for safety and compatibility.

### Performance

**No performance impact expected.** All queries use the same indexes and execution plans as before.

## Deprecation Timeline

| Version | Action |
|---------|--------|
| **v0.11.0** (now) | Lineage schema created, backward compatibility wrappers provided |
| **v0.12.0** (target) | Application code updated to use lineage schema |
| **v0.13.0** (future) | Deprecated wrapper functions removed from flows/runs schemas |

## Verification

After migration, verify the changes:

```sql
-- Verify table moved
SELECT COUNT(*) FROM lineage.partitioning_edges;

-- Verify indexes exist
SELECT indexname FROM pg_indexes 
WHERE tablename = 'partitioning_edges' AND schemaname = 'lineage';

-- Verify new functions work
SELECT * FROM lineage.get_direct_parents(123);

-- Verify backward compatibility
SELECT * FROM flows.get_direct_parents(123);  -- Should delegate to lineage

-- Test runs functions still work
SELECT * FROM runs.update_partitioning_parent(456, 123, 'test', false, false);
```

## Troubleshooting

### Issue: Permission denied on lineage schema

**Solution**: Ensure your database role has been granted usage:
```sql
GRANT USAGE ON SCHEMA lineage TO your_role;
```

### Issue: Function not found in lineage schema

**Solution**: Verify migration completed successfully:
```sql
SELECT * FROM flyway_schema_history WHERE version LIKE '0.11.0%';
```

### Issue: Tests failing after upgrade

**Solution**: Check if tests are directly calling internal `flows._link_to_parents`. Update to use `lineage.link_to_parents` instead.

## Support

For questions or issues related to this migration:
1. Check the migration status: `SELECT * FROM flyway_schema_history WHERE version = '0.11.0.11';`
2. Review database logs for migration errors
3. Report issues at: https://github.com/absa-group/atum-service/issues

## Future Direction

The `lineage` schema represents the source of truth for partitioning relationships. The `flows` schema (which maintains the transitive closure) is now a performance optimization layer and a candidate for future retirement as we transition fully to edge-based lineage queries.

---

**Migration Author**: Database Team  
**Migration Date**: 2026-11-12  
**Target Completion**: v0.12.0 (Scala updates), v0.13.0 (cleanup)
