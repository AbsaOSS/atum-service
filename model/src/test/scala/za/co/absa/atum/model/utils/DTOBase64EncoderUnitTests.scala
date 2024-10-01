package za.co.absa.atum.model.utils

import org.scalatest.flatspec.AnyFlatSpecLike
import za.co.absa.atum.model.dto.{AdditionalDataPatchDTO, PartitionDTO}
import za.co.absa.atum.model.utils.DTOBase64Encoder.encodeDTO

class DTOBase64EncoderUnitTests extends AnyFlatSpecLike {

  "asJsonString" should "encode PartitionDTO into string" in {
    val expected = "eyJrZXkiOiJrZXkxIiwidmFsdWUiOiJ2YWx1ZTEifQ=="
    val actual = encodeDTO(PartitionDTO("key1", "value1"), PartitionDTO.encodePartitionDTO)

    assert(actual == expected)
  }

  "asJsonString" should "encode AdditionalDataDTO into string" in {
    val expected = "eyJieVVzZXIiOiJBcnR1cml0byIsImRhdGEiOnsiTGEiOiJjYXNhIiwiZGUiOiJwYXBlbCJ9fQ=="
    val actual = encodeDTO(
      AdditionalDataPatchDTO("Arturito", Map("La" -> "casa", "de" -> "papel")),
      AdditionalDataPatchDTO.encoderAdditionalDataPatchDTO
    )

    assert(actual == expected)
  }
}
