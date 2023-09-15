package za.co.absa.atum.web.model

case class ControlMeasureMetadata(
  sourceApplication: String,
  country: String,
  historyType: String,
  dataFilename: String,
  sourceType: String,
  version: Int,
  informationDate: String,
  additionalInfo: Map[String, String] = Map.empty
)
