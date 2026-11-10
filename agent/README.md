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

- There is no multi-parent `AtumContext` overload; use the `AtumAgent` overloads for several parents. An empty `Seq` of
  parents throws `IllegalArgumentException`; duplicated parents (same partitions) are collapsed to one.
- `copyMeasureDefinitions = true` makes the server copy the parents' measure definitions **only when the child is newly
  created**; for an already existing child the flag does nothing. Additional data is never copied.
- `AtumContext.subPartitionContext` throws `PartitioningUpdateException` when `mergeWithParent` is `true` and the keys
  of the parent and of `subPartitions` overlap. The 3-argument `AtumAgent.getOrCreateAtumSubContext` overload has no
  such key-overlap guard: it simply builds `parent ++ sub` (overlapping keys take the value of `sub`).

#### One POST per sub-context; errors and retries

Creating a sub-context resolves each parent id first with one `GET` (in the given order) and then sends exactly one
`POST /api/v2/partitionings` carrying all parent ids: no `GET` of the child and no `PATCH`. The server answers `201`
(created) or `200` (already existing); the agent treats every `2xx` the same.

- An unknown parent: the lookup `GET` answers `404` and the agent throws a bare `HttpException(404, body)`
  (`za.co.absa.atum.agent.exception.AtumAgentException.HttpException`) before any `POST` is sent.
- A `409`: the `POST` answers `409` for a dependency cycle, or when an already existing partitioning that has
  descendants of its own would get a new parent (descendants are never propagated into the flows of a new parent), and
  the agent throws `HttpException(409, body)`. This release of the agent does not swallow it (agents v0.8 - v0.10 did
  ignore a `409` of their `PATCH`). Re-running a job with the same parents is not affected.
- Any other non-2xx status is an `HttpException` as well; `4xx` are not retried.
- `5xx` and `IOException` are retried with back-off, `atum.dispatcher.http.retry.max-retries` (default `3`, i.e. up to 4
  attempts, the `POST` included). That is safe because the server-side `POST` is idempotent; the retries can put the
  same `POST` on the wire more than once.


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
