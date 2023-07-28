# Atum Agent


`Atum Agent` module has two main features
`AtumAgent`: Retrieves the configurations and reports the measures.
`AtumContext`:  Provides a library for calculating control measures over a `Spark` `Dataframe`.



## Usage

Include `AtumAgent` as an implicit in the scope for use by the `AtumContext`.

```scala
import za.co.absa.atum.agent.AtumContext.DatasetWrapper
import za.co.absa.atum.agent.model._
```

```scala
implicit val agent: AtumAgent = new AgentImpl
```

Create multiple `AtumContext` with different control measures
```scala
val atumContextInstanceWithRecordCount = AtumContext(processor = processor)
  .withMeasureAdded(RecordCount(MockMeasureNames.recordCount1, controlCol = "id"))

val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
  .withMeasureAdded(AbsSumOfValuesOfColumn(controlCol = "salary"))
```

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
