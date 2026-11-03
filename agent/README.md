# Atum Agent

`Atum Agent` module has two main parts:
* `AtumAgent`: Retrieves the configurations and reports the measures.
* `AtumContext`:  Provides a library for calculating control measures over a `Spark` `Dataframe`.


## Usage

Create multiple `AtumContext` with different control measures to be applied

### Option 1
```scala
val atumContextInstanceWithRecordCount = AtumContext(processor = processor)
  .withMeasureAdded(RecordCount(MockMeasureNames.recordCount1))

val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
  .withMeasureAdded(AbsSumOfValuesOfColumn(measuredColumn = "salary"))
```

### Option 2
Use `AtumPartitions` to get an `AtumContext` from the service using the `AtumAgent`.

```scala
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.model.types.basic.AtumPartitions

val atumPartitions = AtumPartitions(List("country" -> "za", "dataset" -> "customers"))
val atumContext1 = AtumAgent.getOrCreateAtumContext(atumPartitions)
```

### Option 3
Create a dedicated agent instance from a custom Typesafe config.

```scala
import com.typesafe.config.ConfigFactory
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.model.types.basic.AtumPartitions

val config = ConfigFactory.parseString(
  """
    |atum.dispatcher.type = "http"
    |atum.dispatcher.http.url = "http://localhost:8080"
    |""".stripMargin
)

val customAgent = AtumAgent.fromConfig(config)
val atumPartitions = AtumPartitions(List("country" -> "za", "dataset" -> "customers"))
val atumContext = customAgent.getOrCreateAtumContext(atumPartitions)
```

#### AtumPartitions
A list of key values that maintains the order of arrival of the items, the `AtumService`
is able to deliver the correct `AtumContext` according to the `AtumPartitions` we give it.
```scala
val atumPartitions = AtumPartitions(List(
  "name" -> "partition-name",
  "country" -> "SA",
  "gender" -> "female"
))

val withOtherKey = atumPartitions ++ AtumPartitions("otherKey" -> "otherValue")
```

Constructing these keys does not register a parent-child relationship. Use
`AtumContext.subPartitionContext` to create a linked child context.
To link a child to several parents at once, use
`AtumAgent.getOrCreateAtumSubContext(childPartitions, Seq(parentA, parentB))`; the child is identified by
`childPartitions` only and a single create call records all parent links.

#### Sub-contexts, parents and copying measure definitions

Whether a new child inherits the measure definitions of its parent(s) is an explicit opt-in, `copyMeasureDefinitions`
(server default `false`). The overloads that take it have **no default** for the flag, so the choice is always visible
at the call site; every pre-existing overload sends `false`.

```scala
// single parent, via the parent context
val child = parentCtx.subPartitionContext(AtumPartitions("run" -> "1"), mergeWithParent = true, copyMeasureDefinitions = true)

// single parent, via the agent (the parent is the implicit/explicit last parameter list)
val child2 = AtumAgent.getOrCreateAtumSubContext(AtumPartitions("run" -> "2"), mergeWithParent = false, copyMeasureDefinitions = true)(parentCtx)

// several parents, one create call
val merged = AtumAgent.getOrCreateAtumSubContext(AtumPartitions("run" -> "3"), Seq(parentA, parentB), copyMeasureDefinitions = true)
```

| Overload | `copyMeasureDefinitions` |
|----------|--------------------------|
| `AtumContext.subPartitionContext(subPartitions)` | `false` |
| `AtumContext.subPartitionContext(subPartitions, mergeWithParent = true)` | `false` |
| `AtumContext.subPartitionContext(subPartitions, mergeWithParent: Boolean, copyMeasureDefinitions: Boolean)` | explicit, no default |
| `AtumAgent.getOrCreateAtumSubContext(subPartitions)(implicit parent)` | `false` |
| `AtumAgent.getOrCreateAtumSubContext(subPartitions, mergeWithParent = true)(implicit parent)` | `false` |
| `AtumAgent.getOrCreateAtumSubContext(subPartitions, mergeWithParent: Boolean, copyMeasureDefinitions: Boolean)(implicit parent)` | explicit, no default |
| `AtumAgent.getOrCreateAtumSubContext(subPartitions, parentAtumContexts: Seq[AtumContext])` | `false` |
| `AtumAgent.getOrCreateAtumSubContext(subPartitions, parentAtumContexts: Seq[AtumContext], copyMeasureDefinitions: Boolean)` | explicit, no default |

- There is no multi-parent `AtumContext` overload; use the `AtumAgent` overloads for several parents. An empty `Seq` of
  parents throws `IllegalArgumentException`; duplicated parents (same partitions) are collapsed to one.
- `copyMeasureDefinitions = true` makes the server copy the parents' measure definitions **only when the child is newly
  created**; for an already existing child the flag does nothing. Additional data is never copied.
- `AtumContext.subPartitionContext` throws `PartitioningUpdateException` when `mergeWithParent` is `true` and the keys of the
  parent and of `subPartitions` overlap. The 3-argument `AtumAgent.getOrCreateAtumSubContext` overload has no such
  key-overlap guard: it simply builds `parent ++ sub` (overlapping keys take the value of `sub`).

#### One POST per sub-context; errors and retries

Creating a sub-context resolves each parent id first with one `GET` (in the given order) and then sends exactly one
`POST /api/v2/partitionings` carrying all parent ids: no `GET` of the child and no `PATCH`. The server answers `201`
(created) or `200` (already existing); the agent treats every `2xx` the same.

- An unknown parent: the lookup `GET` answers `404` and the agent throws a bare `HttpException(404, body)`
  (`za.co.absa.atum.agent.exception.AtumAgentException.HttpException`) before any `POST` is sent.
- A dependency cycle: the `POST` answers `409` and the agent throws `HttpException(409, body)`. This release of the agent
  does not swallow it (agents v0.8 - v0.10 did ignore a `409` of their `PATCH`).
- Any other non-2xx status is an `HttpException` as well; `4xx` are not retried.
- `5xx` and `IOException` are retried with back-off, `atum.dispatcher.http.retry.max-retries` (default `3`, i.e. up to 4
  attempts, the `POST` included). That is safe because the server-side `POST` is idempotent; the retries can put the
  same `POST` on the wire more than once.

#### Version compatibility (multi-parent)

Always upgrade the server before the agents. A multi-parent capable agent sends `parentPartitioningIds`
(`PartitioningSubmitV2DTO`, server API v2) when creating a partitioning. The agent uses only API v2; the deprecated
v1 `PartitioningSubmitDTO` is not used by the agent.
An older server ignores these unknown fields and silently drops the extra parents (or all of them, with no error).
Multi-parent sub-contexts therefore require a server that includes the three database migrations
(`flows/V0.10.0.1___link_to_parents.sql`, `runs/V0.10.0.2__create_partitioning_with_parents.sql`,
`runs/V0.10.0.3__update_partitioning_parent.sql`) and the matching v2 endpoint support.

Single-parent behavior, public signatures, and decoding of previously produced JSON (field absent -> empty list)
are unchanged for callers of `AtumAgent` and `AtumContext` (the Phase 10 signature check found only added overloads and
nothing removed); the exceptions for library users are listed in "Upgrading from v0.10.x" below. The *encoded* JSON of
`PartitioningSubmitV2DTO` now carries exactly `parentPartitioningIds` and `copyMeasureDefinitions` (besides `partitioning`
and `author`) and no longer the singular `parentPartitioningId`; `copyMeasureDefinitions` is `false` unless you use the
opt-in overloads above. The full agent / server compatibility table is in
[server/README.md](../server/README.md#compatibility-matrix).

#### Upgrading from v0.10.x

1. **Deploy the server before upgrading any agent.** A new agent against an old server loses its parents.
2. **Measure definitions are no longer inherited unless you ask for it.** Code that relied on a new child inheriting its
   parent's measure definitions must either pass `copyMeasureDefinitions = true` through the overloads above, or add the
   measures explicitly: `childCtx.addMeasures(parentCtx.currentMeasures)`. `addMeasures` changes the child context in place
   and returns it; the definitions reach the server with the child's checkpoints.
3. **`PartitioningSubmitV2DTO` changed shape.** It is now
   `PartitioningSubmitV2DTO(partitioning, parentPartitioningIds: Seq[Long], author, copyMeasureDefinitions = false)`:
   there is no `.parentPartitioningId` accessor any more, the constructor and `copy` take the new parameters, and only a
   3-argument `apply(partitioning, parentPartitioningId: Option[Long], author)` shim is kept.
4. **`Dispatcher.createPartitioning` changed its signature.** The abstract method is now
   ```scala
   protected[agent] def createPartitioning(partitioning: PartitioningCreateRequest): AtumContextDTO
   ```
   where `PartitioningCreateRequest(partitioning, parentPartitionings: Seq[PartitioningDTO], authorIfNew, copyMeasureDefinitions = false)`
   (package `za.co.absa.atum.agent.model`) replaces the v0.10.0 parameter `PartitioningSubmitDTO`. **A custom `Dispatcher`
   subclass compiled against 0.10.0 fails at runtime with `AbstractMethodError` and has to be adapted and recompiled.**
5. **Overload delegation changed.** In `AtumAgent.getOrCreateAtumSubContext` and `AtumContext.subPartitionContext` the
   1-argument and 2-argument overloads now call the 3-argument overload directly (in v0.10.0 the shorter ones went through
   each other), so a subclass or a mock that overrides only the shorter overloads is no longer reached by
   `AtumContext.subPartitionContext`.

Control measures can also be overwritten, added or removed.

```scala
    val atumContext1 = atumContext.withMeasureAdded(
      Seq(RecordCount("id"), RecordCount("salary"))
    )

    val atumContextRemoved = atumContext1.withMeasureRemoved(RecordCount("id"))
    assert(atumContextRemoved.measurements.size == 1)
    assert(atumContextRemoved.measurements.head == RecordCount("salary"))
```

Set a checkpoint on a `Dataframe` with an `AtumContext` associated.
```scala
val dfPersons: DataFrame = ss.read
  .format("CSV")
  .option("header", "true")
  .load("agent/src/test/resources/random-dataset/persons.csv")
  .setCheckpoint("checkpoint name")(atumContextInstanceWithRecordCount)
```

Another way is to use measures without an `AtumContext`.
```scala
val sequenceOfMeasures = Seq(RecordCount("columnName"), RecordCount("other columnName"))

 val dfPersons = ss.read
      .format("CSV")
      .executeMeasures("checkpoint name")(sequenceOfMeasures)
```
