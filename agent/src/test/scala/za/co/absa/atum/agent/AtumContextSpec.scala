package za.co.absa.atum.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.model.Measurement._

class AtumContextSpec extends AnyFlatSpec with Matchers {

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    val atumContext = AtumContext()

    assert(atumContext.measurements.isEmpty)

    val atumContextWithRecordCount =
      atumContext.withMeasureAddedOrOverwritten(RecordCount("name 1", "id"))
    assert(atumContextWithRecordCount.measurements.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.withMeasureAddedOrOverwritten(
        Seq(RecordCount("same name", "id"), RecordCount("same name", "x"))
      )
    assert(atumContextWithTwoRecordCount.measurements.size == 2)

    val atumContextWithTwoDistinctRecordCount =
      atumContextWithRecordCount.withMeasureAddedOrOverwritten(
        Seq(RecordCount("name 1", "id"), RecordCount("name 2", "one"))
      )
    assert(atumContextWithTwoDistinctRecordCount.measurements.size == 2)

    val editedAtumContextWithTwoDistinctRecordCount =
      atumContextWithTwoDistinctRecordCount.withMeasureAddedOrOverwritten(
        RecordCount("name 1", "changed")
      )
    assert(
      editedAtumContextWithTwoDistinctRecordCount
        .measurements("name 1")
        .controlCol == "changed"
    )
  }

  "withMeasureRemoved" should "remove a measure if exists" in {

    val atumContext = AtumContext()
    assert(atumContext.measurements.isEmpty)

    val atumContext1 = atumContext.withMeasureAddedOrOverwritten(
      Seq(RecordCount("name 1", "id"), RecordCount("name 2", "id"))
    )
    assert(atumContext1.measurements.size == 2)

    val atumContextRemoved = atumContext1.withMeasureRemoved("name 1")
    assert(atumContextRemoved.measurements.size == 1)
    assert(atumContextRemoved.measurements.keys.head == "name 2")
  }

}
