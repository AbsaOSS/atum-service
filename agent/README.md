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
    val atumContext1 = AtumAgent.createAtumContext(atumPartition)
```

#### AtumPartitions
A list of key values that maintains the order of arrival of the items, the `AtumService`
is able to deliver the correct `AtumContext` according to the `AtumPartitions` we give it.
```scala
    val atumPartitions = AtumPartitions().withPartitions(ListMap("name" -> "partition-name", "country" -> "SA", "gender" -> "female" ))

    val subPartition = atumPartitions.addPartition("otherKey", "otherValue")
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
