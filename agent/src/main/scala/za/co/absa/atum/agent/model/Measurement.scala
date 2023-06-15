package za.co.absa.atum.agent.model

trait Measurement {
  val name: String
  val controlCol: String
  val resultValue: Option[String]

  def withResult(s: Option[String]): Measurement
}

case class RecordCount(
  name: String,
  controlCol: String,
  resultValue: Option[String] = None
) extends Measurement {
  override def withResult(s: Option[String]): Measurement =
    this.copy(resultValue = s)
}

case class DistinctRecordCount(
  name: String,
  controlCol: String,
  resultValue: Option[String] = None
) extends Measurement {
  override def withResult(s: Option[String]): Measurement =
    this.copy(resultValue = s)
}

case class SumOfValuesOfColumn(
  name: String,
  controlCol: String,
  resultValue: Option[String] = None
) extends Measurement {
  override def withResult(s: Option[String]): Measurement =
    this.copy(resultValue = s)
}

case class AbsSumOfValuesOfColumn(
  name: String,
  controlCol: String,
  resultValue: Option[String] = None
) extends Measurement {
  override def withResult(s: Option[String]): Measurement =
    this.copy(resultValue = s)
}

case class SumOfHashesOfColumn(
  name: String,
  controlCol: String,
  resultValue: Option[String] = None
) extends Measurement {
  override def withResult(s: Option[String]): Measurement =
    this.copy(resultValue = s)
}
