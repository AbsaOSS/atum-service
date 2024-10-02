/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
