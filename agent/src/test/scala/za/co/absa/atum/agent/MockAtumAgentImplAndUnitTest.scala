package za.co.absa.atum.agent
import za.co.absa.atum.agent.model._

class MockAtumAgentImplAndUnitTest extends AtumAgent {

  override def measurePublish(measure: Measurement): Unit = measure match {
    case RecordCount(MockMeasureNames.recordCount1, _, result) =>
      assert(result.contains("1000"))

    case AbsSumOfValuesOfColumn(MockMeasureNames.absSumOfValuesOfSalary, _, result) =>
      assert(result.contains("2987144"))

    case SumOfHashesOfColumn(MockMeasureNames.hashSumOfNames, _, result) => assert(result.contains("2044144307532"))

    case SumOfValuesOfColumn(MockMeasureNames.sumOfValuesOfSalary, _, result) => assert(result.contains("2988144"))

    case unKnownMeasure: Measurement =>
      throw new Exception(
        "Measure is not included in the test cases: " + unKnownMeasure
      )
  }

}

object MockMeasureNames {
  val recordCount1           = "record count"
  val absSumOfValuesOfSalary = "salary abs sum"
  val sumOfValuesOfSalary    = "salary sum"
  val hashSumOfNames         = "name hash sum"
}
